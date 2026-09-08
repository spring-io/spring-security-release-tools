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

// Only readFileSync/writeFileSync are overridden (rather than automocking
// 'fs' outright): @actions/core reads fs.promises at import time, and a full
// automock leaves that undefined. readFileSync defaults to the real
// implementation - @actions/github's Context reads GITHUB_EVENT_PATH as a
// side effect of importing it, before any test gets a chance to mock it.
jest.mock('fs', () => {
  const actual = jest.requireActual('fs')
  return {
    ...actual,
    readFileSync: jest.fn(actual.readFileSync),
    writeFileSync: jest.fn()
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

const GRADLE_FILE = 'gradle/libs.versions.toml'
const WORKFLOW_FILE = '.github/workflows/build.yml'

function mockInputs(overrides: Record<string, string> = {}): void {
  const values: Record<string, string> = {
    token: 'gh-token',
    labels: 'type: dependency-upgrade',
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
  readFileSyncSpy.mockImplementation(
    (file => contents[file as string]) as typeof fs.readFileSync
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

function mockOctokit(overrides: {
  existingPulls?: { number: number; html_url: string }[]
}): ReturnType<typeof github.getOctokit> {
  const octokit = {
    rest: {
      pulls: {
        list: jest
          .fn()
          .mockResolvedValue({ data: overrides.existingPulls ?? [] }),
        create: jest
          .fn()
          .mockResolvedValue({ data: { number: 1, html_url: 'created-url' } }),
        update: jest.fn().mockResolvedValue({})
      },
      issues: {
        addLabels: jest.fn().mockResolvedValue({})
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
  mockCurrentBranch('main')
})

describe('run', () => {
  it('reports up to date and does nothing else when no newer release exists', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockFileContents({
      [GRADLE_FILE]:
        'io-spring-security-release-plugin = "io.spring.gradle:spring-security-release-plugin:1.0.17"'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.17',
      sha: 'sha1'
    })
    mockOctokit({})

    await run()

    expect(mockedCore.setOutput).toHaveBeenCalledWith('updated', false)
    expect(writeFileSyncSpy).not.toHaveBeenCalled()
    expect(mockedExec.exec).not.toHaveBeenCalledWith(
      'git',
      expect.arrayContaining(['push']),
      expect.anything()
    )
    expect(mockedCore.setFailed).not.toHaveBeenCalled()
  })

  it('fails when no pinned version can be found', async () => {
    mockInputs()
    mockFiles([], [])
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha2'
    })
    mockOctokit({})

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('Could not find a currently pinned version')
    )
  })

  it('fails with a wrapped message when fetching the latest release errors', async () => {
    mockInputs()
    mockFiles([], [])
    mockedGetLatestRelease.mockRejectedValue(new Error('rate limited'))
    mockOctokit({})

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining(
        'Could not fetch the latest release for spring-io/spring-security-release-tools: rate limited'
      )
    )
  })

  it('falls back to workflow files when no gradle file has a pinned version', async () => {
    mockInputs()
    mockFiles([], [WORKFLOW_FILE])
    mockFileContents({
      [WORKFLOW_FILE]:
        'uses: spring-io/spring-security-release-tools/.github/workflows/build.yml@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa # v1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.17',
      sha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    })
    mockOctokit({})

    await run()

    expect(mockedCore.setOutput).toHaveBeenCalledWith(
      'previous-version',
      '1.0.17'
    )
    expect(mockedCore.setOutput).toHaveBeenCalledWith('updated', false)
  })

  it('fails if the pinned reference is gone by the time it rewrites files', async () => {
    // Simulates the file changing between the scan that finds the current
    // version and the pass that rewrites it - readFileSync returns the
    // pinned coordinate once, then content without it.
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    let reads = 0
    readFileSyncSpy.mockImplementation(() => {
      reads += 1
      return reads === 1
        ? 'io.spring.gradle:spring-security-release-plugin:1.0.17'
        : 'no matching coordinate here'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha3'
    })
    mockOctokit({})

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('no references')
    )
  })

  it('rewrites pinned files, pushes a branch, and opens a new pull request', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [WORKFLOW_FILE])
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
      'chore/bump-spring-security-release-tools-1.0.18'
    ])
    expect(mockedExec.exec).toHaveBeenCalledWith('git', [
      'push',
      '--force',
      'origin',
      'chore/bump-spring-security-release-tools-1.0.18:chore/bump-spring-security-release-tools-1.0.18'
    ])
    expect(octokit.rest.pulls.create).toHaveBeenCalled()
    expect(octokit.rest.issues.addLabels).toHaveBeenCalledWith(
      expect.objectContaining({ labels: ['type: dependency-upgrade'] })
    )
    expect(mockedCore.setOutput).toHaveBeenCalledWith(
      'pull-request-url',
      'created-url'
    )
  })

  it('updates the existing pull request instead of creating a new one', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha4'
    })
    const octokit = mockOctokit({
      existingPulls: [{ number: 42, html_url: 'existing-url' }]
    })

    await run()

    expect(octokit.rest.pulls.update).toHaveBeenCalledWith(
      expect.objectContaining({ pull_number: 42 })
    )
    expect(octokit.rest.pulls.create).not.toHaveBeenCalled()
    expect(mockedCore.setOutput).toHaveBeenCalledWith(
      'pull-request-url',
      'existing-url'
    )
  })

  it('skips adding labels when none are configured', async () => {
    mockInputs({ labels: '' })
    mockFiles([GRADLE_FILE], [])
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha5'
    })
    const octokit = mockOctokit({})

    await run()

    expect(octokit.rest.issues.addLabels).not.toHaveBeenCalled()
  })

  it('fails when the current branch cannot be determined', async () => {
    mockInputs()
    mockFiles([GRADLE_FILE], [])
    mockFileContents({
      [GRADLE_FILE]: 'io.spring.gradle:spring-security-release-plugin:1.0.17'
    })
    mockedGetLatestRelease.mockResolvedValue({
      version: '1.0.18',
      sha: 'sha6'
    })
    mockOctokit({})
    mockedExec.exec.mockResolvedValue(0)

    await run()

    expect(mockedCore.setFailed).toHaveBeenCalledWith(
      expect.stringContaining('Could not determine the current branch')
    )
  })
})
