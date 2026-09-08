import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as github from '@actions/github'
import * as glob from '@actions/glob'
import * as fs from 'fs'
import { getLatestRelease } from '../src/release'
import { run } from '../src/main'

jest.mock('@actions/core')
jest.mock('@actions/exec')
jest.mock('@actions/glob')
jest.mock('@actions/github')
jest.mock('../src/release')

// Only readFileSync/writeFileSync/existsSync are overridden (rather than
// automocking 'fs' outright): @actions/core reads fs.promises at import
// time, and a full automock leaves that undefined. readFileSync defaults to
// the real implementation - @actions/github's Context reads
// GITHUB_EVENT_PATH as a side effect of importing it, before any test gets a
// chance to mock it.
jest.mock('fs', () => {
  const actual = jest.requireActual('fs')
  return {
    ...actual,
    readFileSync: jest.fn(actual.readFileSync),
    writeFileSync: jest.fn(),
    existsSync: jest.fn(actual.existsSync)
  }
})

const mockedCore = core as jest.Mocked<typeof core>
const mockedExec = exec as jest.Mocked<typeof exec>
const mockedGlob = glob as jest.Mocked<typeof glob>
const mockedGithub = github as jest.Mocked<typeof github>
const mockedGetLatestRelease = getLatestRelease as jest.MockedFunction<
  typeof getLatestRelease
>
const readFileSyncSpy = fs.readFileSync as jest.Mock
const writeFileSyncSpy = fs.writeFileSync as jest.Mock
const existsSyncSpy = fs.existsSync as jest.Mock

const GRADLE_FILE = 'gradle/libs.versions.toml'
const WORKFLOW_FILE = '.github/workflows/build.yml'
const DEPENDABOT_PATH = '.github/dependabot.yml'

let fileContents: Record<string, string> = {}

function githubActionsDependabotYaml(branches: string[] = ['main']): string {
  const entries = branches
    .map(
      branch => `  - package-ecosystem: github-actions
    target-branch: ${branch}`
    )
    .join('\n')
  return `updates:\n${entries}\n`
}

function mockInputs(overrides: Record<string, string> = {}): void {
  const values: Record<string, string> = {
    token: 'gh-token',
    ...overrides
  }
  mockedCore.getInput.mockImplementation((name: string) => values[name] ?? '')
}

function mockFiles(gradleFiles: string[], workflowFiles: string[]): void {
  mockedGlob.create.mockImplementation(async patterns => {
    const isGradle = (patterns as string).includes('*.gradle')
    return {
      glob: jest.fn().mockResolvedValue(isGradle ? gradleFiles : workflowFiles)
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any
  })
}

function mockFileContents(contents: Record<string, string>): void {
  Object.assign(fileContents, contents)
  readFileSyncSpy.mockImplementation(
    (file => fileContents[file as string]) as typeof fs.readFileSync
  )
}

function mockDependabotConfig(yaml: string | undefined): void {
  existsSyncSpy.mockImplementation(
    (path: fs.PathLike) => path === DEPENDABOT_PATH && yaml !== undefined
  )
  if (yaml !== undefined) {
    fileContents[DEPENDABOT_PATH] = yaml
  }
  readFileSyncSpy.mockImplementation(
    (file => fileContents[file as string]) as typeof fs.readFileSync
  )
}

function mockCurrentBranch(branch: string): void {
  mockedExec.exec.mockImplementation(async (command, args, options) => {
    if (command === 'git' && args?.[0] === 'branch') {
      options?.listeners?.stdout?.(Buffer.from(`${branch}\n`))
    }
    return 0
  })
}

interface OctokitOverrides {
  existingPulls?: { number: number; html_url: string }[]
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  createImpl?: (
    params: any
  ) => Promise<{ data: { number: number; html_url: string } }>
}

function mockOctokit(
  overrides: OctokitOverrides = {}
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): any {
  const octokit = {
    rest: {
      pulls: {
        list: jest
          .fn()
          .mockResolvedValue({ data: overrides.existingPulls ?? [] }),
        create:
          overrides.createImpl ??
          jest.fn().mockResolvedValue({
            data: { number: 1, html_url: 'created-url' }
          }),
        update: jest.fn().mockResolvedValue({})
      },
      issues: {
        addLabels: jest.fn().mockResolvedValue({}),
        update: jest.fn().mockResolvedValue({})
      }
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any
  mockedGithub.getOctokit.mockReturnValue(octokit)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ;(mockedGithub as any).context = {
    repo: { owner: 'consumer', repo: 'consumer-repo' }
  }
  return octokit
}

beforeEach(() => {
  jest.clearAllMocks()
  fileContents = {}
  mockCurrentBranch('main')
  ;(mockedCore as unknown as { summary: unknown }).summary = {
    addHeading: jest.fn().mockReturnThis(),
    addTable: jest.fn().mockReturnThis(),
    write: jest.fn().mockResolvedValue(undefined)
  }
})

describe('run', () => {
  it('fails when dependabot.yml cannot be found', async () => {
    mockInputs()
    mockFiles([], [])
    mockDependabotConfig(undefined)
    mockedGetLatestRelease.mockResolvedValue({ version: '1.0.18', sha: 'sha1' })
    mockOctokit({})

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('Could not find .github/dependabot.yml')
    )
  })

  it('fails when dependabot.yml has no github-actions entries', async () => {
    mockInputs()
    mockFiles([], [])
    mockDependabotConfig(
      'updates:\n  - package-ecosystem: gradle\n    target-branch: main\n'
    )
    mockedGetLatestRelease.mockResolvedValue({ version: '1.0.18', sha: 'sha1' })
    mockOctokit({})

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('no "package-ecosystem: github-actions" entries')
    )
  })

  it('fails with a wrapped message when fetching the latest release errors', async () => {
    mockInputs()
    mockFiles([], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockedGetLatestRelease.mockRejectedValue(new Error('rate limited'))
    mockOctokit({})

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining(
        'Could not fetch the latest release for spring-io/spring-security-release-tools: rate limited'
      )
    )
  })

  it('fails when the current branch cannot be determined', async () => {
    mockInputs()
    mockFiles([], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockedGetLatestRelease.mockResolvedValue({ version: '1.0.18', sha: 'sha1' })
    mockOctokit({})
    mockedExec.exec.mockResolvedValue(0)

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('Could not determine the current branch')
    )
  })

  it('reports up to date and does nothing else when no newer release exists', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.17',
      sha: 'sha1'
    })
    mockOctokit({})

    await run()

    expect(writeFileSyncSpy).not.toHaveBeenCalled()
    expect(mockedExec.exec).not.toHaveBeenCalledWith(
      'git',
      expect.arrayContaining(['push']),
      expect.anything()
    )
    expect(mockedCore.setFailed).not.toHaveBeenCalled()
  })

  it('fails the branch when no pinned version can be found', async () => {
    mockInputs()
    mockFiles([], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha2'
    })
    mockOctokit({})

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('Failed to update 1 branch(es): main')
    )
  })

  it('rewrites pinned files, pushes a per-branch head, and opens a new pull request', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [WORKFLOW_FILE])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17',
      [WORKFLOW_FILE]:
        'uses: spring-io/spring-security-release-tools/.github/workflows/build.yml@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa # v1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    })
    const octokit = mockOctokit({})

    await run()

    expect(writeFileSyncSpy).toHaveBeenCalledWith(
      GRADLE_FILE,
      expect.stringContaining('1.0.18')
    )
    expect(writeFileSyncSpy).toHaveBeenCalledWith(
      WORKFLOW_FILE,
      expect.stringContaining('bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb')
    )
    expect(mockedExec.exec).toHaveBeenCalledWith('git', [
      'checkout',
      '-B',
      'chore/bump-spring-security-release-tools-1.0.18-main'
    ])
    expect(mockedExec.exec).toHaveBeenCalledWith('git', [
      'push',
      '--force',
      'origin',
      'chore/bump-spring-security-release-tools-1.0.18-main:chore/bump-spring-security-release-tools-1.0.18-main'
    ])
    expect(octokit.rest.pulls.create).toHaveBeenCalledWith(
      expect.objectContaining({ base: 'main' })
    )
    expect(octokit.rest.issues.addLabels).toHaveBeenCalledWith(
      expect.objectContaining({ labels: ['type: dependency-upgrade'] })
    )
    expect(octokit.rest.issues.update).not.toHaveBeenCalled()
    expect(mockedCore.setFailed).not.toHaveBeenCalled()
  })

  it('applies the milestone and labels configured in dependabot.yml, overriding the default label', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockDependabotConfig(
      `updates:\n  - package-ecosystem: github-actions\n    target-branch: main\n    milestone: 7\n    labels:\n      - 'type: task'\n`
    )
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha4'
    })
    const octokit = mockOctokit({})

    await run()

    expect(octokit.rest.issues.addLabels).toHaveBeenCalledWith(
      expect.objectContaining({ labels: ['type: task'] })
    )
    expect(octokit.rest.issues.update).toHaveBeenCalledWith(
      expect.objectContaining({ milestone: 7 })
    )
  })

  it('updates the existing pull request instead of creating a new one', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha5'
    })
    const octokit = mockOctokit({
      existingPulls: [{ number: 42, html_url: 'existing-url' }]
    })

    await run()

    expect(octokit.rest.pulls.update).toHaveBeenCalledWith(
      expect.objectContaining({ pull_number: 42 })
    )
    expect(octokit.rest.pulls.create).not.toHaveBeenCalled()
  })

  it('falls back to the default label when dependabot.yml does not specify one', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha6'
    })
    const octokit = mockOctokit({})

    await run()

    expect(octokit.rest.issues.addLabels).toHaveBeenCalledWith(
      expect.objectContaining({ labels: ['type: dependency-upgrade'] })
    )
  })

  it('processes every branch in dependabot.yml, isolating failures per branch', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockDependabotConfig(githubActionsDependabotYaml(['main', '6.1.x']))
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha7'
    })
    const octokit = mockOctokit({
      createImpl: jest.fn(async params => {
        if (params.base === '6.1.x') throw new Error('boom')
        return { data: { number: 1, html_url: 'created-url' } }
      })
    })

    await run()

    expect(octokit.rest.pulls.create).toHaveBeenCalledWith(
      expect.objectContaining({ base: 'main' })
    )
    expect(octokit.rest.pulls.create).toHaveBeenCalledWith(
      expect.objectContaining({ base: '6.1.x' })
    )
    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('Failed to update 1 branch(es): 6.1.x')
    )
  })
})
