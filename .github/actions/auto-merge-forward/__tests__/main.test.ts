/**
 * Unit tests for the action's main functionality, src/main.ts
 *
 * These should be run as if the action was called from a workflow.
 * Specifically, the inputs listed in `action.yml` should be set as return
 * values of `core.getInput`.
 */

import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as github from '@actions/github'
import * as main from '../src/main'

let getInputMock: jest.SpyInstance
let setFailedMock: jest.SpyInstance
let execMock: jest.SpyInstance

function inputs(values: Record<string, string>): void {
  getInputMock.mockImplementation((name: string): string => values[name] ?? '')
}

/**
 * Makes `git log` report the given authors (one per commit) for each call.
 */
function gitLogAuthors(...authors: string[]): void {
  execMock.mockImplementation(
    async (
      _command: string,
      args?: string[],
      options?: exec.ExecOptions
    ): Promise<number> => {
      if (args?.[0] === 'log') {
        options?.listeners?.stdout?.(Buffer.from(authors.join('\n')))
      }
      return 0
    }
  )
}

describe('action', () => {
  beforeEach(() => {
    jest.clearAllMocks()

    jest.spyOn(core, 'warning').mockImplementation()
    jest.spyOn(core, 'info').mockImplementation()
    getInputMock = jest.spyOn(core, 'getInput').mockImplementation()
    setFailedMock = jest.spyOn(core, 'setFailed').mockImplementation()
    execMock = jest.spyOn(exec, 'exec').mockResolvedValue(0)
    jest.replaceProperty(github.context, 'ref', 'refs/heads/1.0.x')
  })

  it('sets a failed status when fewer than 2 branches are given', async () => {
    inputs({ branches: '1.0.x' })

    await main.run()

    expect(setFailedMock).toHaveBeenCalledWith(
      'Please specify at least 2 branches'
    )
    expect(execMock).not.toHaveBeenCalled()
  })

  it('merges forward and pushes when all commits are from the expected author', async () => {
    inputs({
      branches: '1.0.x, 1.1.x',
      'from-author': 'dependabot[bot]',
      'merge-strategy': 'ours'
    })
    gitLogAuthors('dependabot[bot]', 'dependabot[bot]')

    await main.run()

    expect(setFailedMock).not.toHaveBeenCalled()
    expect(execMock).toHaveBeenCalledWith('git', ['switch', '1.1.x'])
    expect(execMock).toHaveBeenCalledWith('git', [
      'merge',
      '1.0.x',
      '-s',
      'ours'
    ])
    expect(execMock).toHaveBeenCalledWith('git', [
      'push',
      '--atomic',
      'origin',
      '1.1.x'
    ])
  })

  it('does not push when dry-run is true', async () => {
    inputs({
      branches: '1.0.x,1.1.x',
      'from-author': 'dependabot[bot]',
      'merge-strategy': 'ours',
      'dry-run': 'true'
    })
    gitLogAuthors('dependabot[bot]')

    await main.run()

    expect(setFailedMock).not.toHaveBeenCalled()
    expect(execMock).toHaveBeenCalledWith('git', [
      'merge',
      '1.0.x',
      '-s',
      'ours'
    ])
    expect(execMock).not.toHaveBeenCalledWith(
      'git',
      expect.arrayContaining(['push'])
    )
  })

  it('does not merge when another author has commits', async () => {
    inputs({
      branches: '1.0.x,1.1.x',
      'from-author': 'dependabot[bot]',
      'merge-strategy': 'ours'
    })
    gitLogAuthors('dependabot[bot]', 'someone-else')

    await main.run()

    expect(setFailedMock).not.toHaveBeenCalled()
    expect(execMock).not.toHaveBeenCalledWith(
      'git',
      expect.arrayContaining(['merge'])
    )
  })
})
