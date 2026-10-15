/**
 * Unit tests for the action's main functionality, src/main.ts
 *
 * Git is faked: `exec.getExecOutput` answers the read-only commands and
 * `exec.exec` records the commands that change state.
 */

import { jest } from '@jest/globals'
import type * as coreTypes from '@actions/core'
import type * as execTypes from '@actions/exec'
import type * as githubTypes from '@actions/github'
import type { findUntrustedCommit } from '../src/verify-commit-source.js'

// ESM modules can't be spied on or reassigned, so each mocked module is
// registered with an explicit factory before main.js is imported.
let inputs: Record<string, string>
let commitCount: number
let remoteBranches: string
let shallow: string

const setFailedMock = jest.fn<typeof coreTypes.setFailed>()
const getInputMock = jest.fn<typeof coreTypes.getInput>()
const execMock = jest.fn<(cmd: string, args: string[]) => Promise<number>>()
const getExecOutputMock = jest.fn<typeof execTypes.getExecOutput>()
const findUntrustedMock = jest.fn<typeof findUntrustedCommit>()
const mockedGithub = {
  getOctokit: jest.fn<typeof githubTypes.getOctokit>(),
  context: {
    ref: '',
    repo: { owner: 'spring-projects', repo: 'spring-session' }
  }
}

jest.unstable_mockModule('@actions/core', () => ({
  info: jest.fn(),
  setFailed: setFailedMock,
  getInput: getInputMock
}))
jest.unstable_mockModule('@actions/exec', () => ({
  exec: execMock,
  getExecOutput: getExecOutputMock
}))
jest.unstable_mockModule('@actions/github', () => mockedGithub)
jest.unstable_mockModule('../src/verify-commit-source.js', () => ({
  findUntrustedCommit: findUntrustedMock
}))

const main = await import('../src/main.js')

function shas(count: number): string {
  return Array.from({ length: count }, (_, i) => `sha${i}\n`).join('')
}

function heads(...names: string[]): string {
  return names.map(n => `abc123\trefs/heads/${n}\n`).join('')
}

function commands(): string[] {
  return execMock.mock.calls.map(([, args]) => args.join(' '))
}

function stateChanging(): string[] {
  return commands().filter(c => /^(merge|push)/.test(c))
}

describe('run', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    inputs = {
      'github-token': 'token'
    }
    commitCount = 0
    remoteBranches = heads('6.4.x', '6.5.x', '7.0.x', 'main')
    shallow = 'false'
    mockedGithub.context.ref = 'refs/heads/6.5.x'

    getInputMock.mockImplementation((name: string) => inputs[name] ?? '')
    mockedGithub.getOctokit.mockReturnValue({} as never)
    findUntrustedMock.mockResolvedValue(undefined)
    execMock.mockResolvedValue(0)
    getExecOutputMock.mockImplementation(
      async (_cmd: string, args: string[]) => {
        let stdout = ''
        if (args[0] === 'ls-remote') {
          stdout = remoteBranches
        } else if (args[0] === 'rev-parse') {
          stdout = shallow
        } else if (args[0] === 'log') {
          stdout = shas(commitCount)
        }
        return { exitCode: 0, stdout, stderr: '' }
      }
    )
  })

  it('merges into the next branch and pushes it', async () => {
    commitCount = 2

    await main.run()

    expect(setFailedMock).not.toHaveBeenCalled()
    expect(commands()).toEqual(
      expect.arrayContaining(['merge 6.5.x -s ours', 'push origin 7.0.x'])
    )
    expect(getExecOutputMock).toHaveBeenCalledWith('git', [
      'log',
      '6.5.x',
      '^7.0.x',
      '--format=%H',
      '--no-merges',
      '--'
    ])
  })

  it('merges into main after the newest N.N.x branch', async () => {
    mockedGithub.context.ref = 'refs/heads/7.0.x'
    commitCount = 1

    await main.run()

    expect(commands()).toContain('merge 7.0.x -s ours')
    expect(commands()).toContain('push origin main')
  })

  it('does nothing on main', async () => {
    mockedGithub.context.ref = 'refs/heads/main'

    await main.run()

    expect(setFailedMock).not.toHaveBeenCalled()
    expect(findUntrustedMock).not.toHaveBeenCalled()
    expect(stateChanging()).toEqual([])
  })

  it('uses the merge strategy input', async () => {
    inputs['merge-strategy'] = 'recursive'
    commitCount = 1

    await main.run()

    expect(commands()).toContain('merge 6.5.x -s recursive')
  })

  it('verifies all of the git log output against from-username', async () => {
    inputs['from-username'] = 'some-user'
    commitCount = 3

    await main.run()

    expect(findUntrustedMock).toHaveBeenCalledWith(
      expect.anything(),
      'spring-projects',
      'spring-session',
      ['sha0', 'sha1', 'sha2'],
      'some-user'
    )
  })

  it('defaults from-username to dependabot[bot]', async () => {
    commitCount = 1

    await main.run()

    expect(findUntrustedMock).toHaveBeenCalledWith(
      expect.anything(),
      expect.anything(),
      expect.anything(),
      expect.anything(),
      'dependabot[bot]'
    )
  })

  it('does not merge when a commit cannot be confirmed', async () => {
    commitCount = 2
    findUntrustedMock.mockResolvedValue('sha1')

    await main.run()

    expect(setFailedMock).not.toHaveBeenCalled()
    expect(stateChanging()).toEqual([])
  })

  it('does not ask GitHub when there is nothing to merge', async () => {
    await main.run()

    expect(findUntrustedMock).not.toHaveBeenCalled()
    expect(stateChanging()).toEqual([])
  })

  it('does not push on a dry run', async () => {
    inputs['dry-run'] = 'true'
    commitCount = 1

    await main.run()

    expect(commands()).toContain('merge 6.5.x -s ours')
    expect(commands().filter(c => c.startsWith('push'))).toEqual([])
  })

  it('unshallows the triggering branch only when shallow', async () => {
    commitCount = 1
    await main.run()
    expect(commands()).not.toContain('fetch origin 6.5.x --unshallow')

    jest.clearAllMocks()
    execMock.mockResolvedValue(0)
    shallow = 'true'
    await main.run()
    expect(commands()).toContain('fetch origin 6.5.x --unshallow')
  })

  it('fails when the push fails', async () => {
    commitCount = 1
    execMock.mockImplementation(async (_cmd: string, args: string[]) => {
      if (args[0] === 'push') throw new Error('push rejected')
      return 0
    })

    await main.run()

    expect(setFailedMock).toHaveBeenCalledWith('push rejected')
  })

  it('fails when GitHub cannot be asked', async () => {
    commitCount = 1
    findUntrustedMock.mockRejectedValue(new Error('rate limited'))

    await main.run()

    expect(setFailedMock).toHaveBeenCalledWith('rate limited')
    expect(stateChanging()).toEqual([])
  })

  it('fails when not triggered by a branch', async () => {
    mockedGithub.context.ref = 'refs/tags/v1.0.0'

    await main.run()

    expect(setFailedMock).toHaveBeenCalledWith(
      "Expected a branch ref but got 'refs/tags/v1.0.0'"
    )
  })

  it('fails on a branch that is not N.N.x', async () => {
    mockedGithub.context.ref = 'refs/heads/feature'

    await main.run()

    expect(setFailedMock).toHaveBeenCalledWith(
      "Cannot compute the next branch for 'feature', it is not an N.N.x branch"
    )
    expect(stateChanging()).toEqual([])
  })
})
