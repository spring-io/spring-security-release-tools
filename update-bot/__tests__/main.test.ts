import { jest } from '@jest/globals'
import type * as coreTypes from '@actions/core'
import type * as execTypes from '@actions/exec'
import type * as githubTypes from '@actions/github'
import type * as globTypes from '@actions/glob'
import * as fs from 'fs'
import type { LatestRelease } from '../src/release.js'

// ESM modules can't be reassigned or automocked, so each mocked module is
// registered with an explicit factory before main.js is imported.
const mockedCore = {
  getInput: jest.fn<typeof coreTypes.getInput>(),
  setFailed: jest.fn<typeof coreTypes.setFailed>(),
  info: jest.fn<typeof coreTypes.info>(),
  warning: jest.fn<typeof coreTypes.warning>(),
  error: jest.fn<typeof coreTypes.error>(),
  summary: {
    addHeading: jest.fn().mockReturnThis(),
    addTable: jest.fn().mockReturnThis(),
    write: jest.fn<() => Promise<void>>().mockResolvedValue(undefined)
  }
}
const mockedExec = { exec: jest.fn<typeof execTypes.exec>() }
const mockedGlob = { create: jest.fn<typeof globTypes.create>() }
const mockedGithub = {
  getOctokit: jest.fn<typeof githubTypes.getOctokit>(),
  context: { repo: { owner: '', repo: '' } }
}
const mockedGetLatestRelease = jest.fn<() => Promise<LatestRelease>>()

jest.unstable_mockModule('@actions/core', () => mockedCore)
jest.unstable_mockModule('@actions/exec', () => mockedExec)
jest.unstable_mockModule('@actions/glob', () => mockedGlob)
jest.unstable_mockModule('@actions/github', () => mockedGithub)
jest.unstable_mockModule('../src/release.js', () => ({
  getLatestRelease: mockedGetLatestRelease
}))

// Only readFileSync/writeFileSync/existsSync are overridden (rather than
// automocking 'fs' outright): @actions/core reads fs.promises at import
// time, and a full automock leaves that undefined. readFileSync defaults to
// the real implementation - @actions/github's Context reads
// GITHUB_EVENT_PATH as a side effect of importing it, before any test gets a
// chance to mock it.
const actualFs = await import('fs')
const readFileSyncSpy = jest.fn<typeof fs.readFileSync>(actualFs.readFileSync)
const writeFileSyncSpy = jest.fn<typeof fs.writeFileSync>()
const existsSyncSpy = jest.fn<typeof fs.existsSync>(actualFs.existsSync)
jest.unstable_mockModule('fs', () => ({
  ...actualFs,
  default: actualFs,
  readFileSync: readFileSyncSpy,
  writeFileSync: writeFileSyncSpy,
  existsSync: existsSyncSpy
}))

const { run } = await import('../src/main.js')

const GRADLE_FILE = 'gradle/libs.versions.toml'
const SETTINGS_FILE = 'settings.gradle'
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
    const isGradle = patterns.includes('*.gradle')
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
  mockedGithub.context.repo = { owner: 'consumer', repo: 'consumer-repo' }
  return octokit
}

beforeEach(() => {
  jest.clearAllMocks()
  fileContents = {}
  mockCurrentBranch('main')
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

  it('warns instead of failing when no pinned version can be found', async () => {
    mockInputs()
    mockFiles([], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha2'
    })
    mockOctokit({})

    await run()

    expect(mockedCore.warning).toHaveBeenCalledWith(
      expect.stringContaining(
        '[main] Could not find a currently pinned version'
      )
    )
    expect(mockedCore.setFailed).not.toHaveBeenCalled()
  })

  it('ignores docs-build branches entirely', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockDependabotConfig(githubActionsDependabotYaml(['main', 'docs-build']))
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha2'
    })
    mockOctokit({})

    await run()

    expect(mockedCore.info).toHaveBeenCalledWith('[docs-build] Ignoring branch')
    expect(mockedExec.exec).not.toHaveBeenCalledWith('git', [
      'fetch',
      'origin',
      'docs-build'
    ])
    expect(mockedCore.warning).not.toHaveBeenCalled()
    expect(mockedCore.setFailed).not.toHaveBeenCalled()
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

  it('also rewrites a settings.gradle plugins DSL declaration', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE, SETTINGS_FILE], [])
    mockDependabotConfig(githubActionsDependabotYaml())
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17',
      [SETTINGS_FILE]:
        'plugins {\n\tid "io.spring.security.settings" version "1.0.17"\n}\n'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha3'
    })
    mockOctokit({})

    await run()

    expect(writeFileSyncSpy).toHaveBeenCalledWith(
      SETTINGS_FILE,
      expect.stringContaining(
        'id "io.spring.security.settings" version "1.0.18"'
      )
    )
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
