/**
 * Unit tests for the action's main functionality, src/main.ts
 *
 * Git is faked: `exec.getExecOutput` answers the read-only commands and
 * `exec.exec` records the commands that change state.
 */

import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as github from '@actions/github'
import * as main from '../src/main'
import { findUntrustedCommit } from '../src/verify-commit-source'

jest.mock('@actions/exec')
jest.mock('../src/verify-commit-source')

let inputs: Record<string, string>
let commitCount: number
let remoteBranches: string
let shallow: string
let setFailedMock: jest.SpyInstance

const execMock = exec.exec as jest.Mock
const getExecOutputMock = exec.getExecOutput as jest.Mock
const findUntrustedMock = findUntrustedCommit as jest.Mock

function shas(count: number): string {
  return Array.from({ length: count }, (_, i) => `sha${i}\n`).join('')
}

function heads(...names: string[]): string {
  return names.map(n => `abc123\trefs/heads/${n}\n`).join('')
}

function commands(): string[] {
  return execMock.mock.calls.map(([, args]) => (args as string[]).join(' '))
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
    github.context.ref = 'refs/heads/6.5.x'

    jest.spyOn(core, 'info').mockImplementation()
    setFailedMock = jest.spyOn(core, 'setFailed').mockImplementation()
    jest
      .spyOn(core, 'getInput')
      .mockImplementation((name: string) => inputs[name] ?? '')
    process.env.GITHUB_REPOSITORY = 'spring-projects/spring-session'
    jest.spyOn(github, 'getOctokit').mockReturnValue({} as never)
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
    github.context.ref = 'refs/heads/7.0.x'
    commitCount = 1

    await main.run()

    expect(commands()).toContain('merge 7.0.x -s ours')
    expect(commands()).toContain('push origin main')
  })

  it('does nothing on main', async () => {
    github.context.ref = 'refs/heads/main'

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
    github.context.ref = 'refs/tags/v1.0.0'

    await main.run()

    expect(setFailedMock).toHaveBeenCalledWith(
      "Expected a branch ref but got 'refs/tags/v1.0.0'"
    )
  })

  it('fails on a branch that is not N.N.x', async () => {
    github.context.ref = 'refs/heads/feature'

    await main.run()

    expect(setFailedMock).toHaveBeenCalledWith(
      "Cannot compute the next branch for 'feature', it is not an N.N.x branch"
    )
    expect(stateChanging()).toEqual([])
  })
})
