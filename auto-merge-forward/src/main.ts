import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as github from '@actions/github'
import { findUntrustedCommit } from './verify-commit-source'
import {
  computeNextBranch,
  isSafeBranchName,
  parseRemoteBranches
} from './next-branch'

const DEFAULT_BRANCH = 'main'

async function git(args: string[]): Promise<string> {
  const { stdout } = await exec.getExecOutput('git', args)
  return stdout
}

function triggeringBranch(): string {
  const prefix = 'refs/heads/'
  if (!github.context.ref.startsWith(prefix)) {
    throw new Error(`Expected a branch ref but got '${github.context.ref}'`)
  }
  return github.context.ref.substring(prefix.length)
}

/**
 * The main function for the action.
 * @returns {Promise<void>} Resolves when the action is complete.
 */
export async function run(): Promise<void> {
  try {
    const fromUsername = core.getInput('from-username') || 'dependabot[bot]'
    const mergeStrategy: string = core.getInput('merge-strategy') || 'ours'
    const dryRun: boolean = core.getInput('dry-run') === 'true'

    const current = triggeringBranch()
    const next = computeNextBranch(
      current,
      parseRemoteBranches(await git(['ls-remote', '--heads', 'origin'])),
      DEFAULT_BRANCH
    )
    if (!next) {
      core.info('There is no branch to merge into, nothing to do')
      return
    }
    if (!isSafeBranchName(current) || !isSafeBranchName(next)) {
      throw new Error(`Invalid branch name(s): ${current}, ${next}`)
    }
    core.info(`Next branch after ${current} is ${next}`)

    if (
      (await git(['rev-parse', '--is-shallow-repository'])).trim() === 'true'
    ) {
      await exec.exec('git', ['fetch', 'origin', current, '--unshallow'])
    }
    await exec.exec('git', ['fetch', 'origin', next])
    await exec.exec('git', ['switch', next])
    await exec.exec('git', ['switch', '-'])

    const gitLogOutput = await git([
      'log',
      current,
      `^${next}`,
      '--format=%H',
      '--no-merges',
      '--'
    ])
    const commits = gitLogOutput.split('\n').filter(v => !!v)
    core.info(
      `Found ${commits.length} commits in ${current} that are not present in ${next}`
    )
    if (commits.length === 0) {
      return
    }

    const untrusted = await findUntrustedCommit(
      github.getOctokit(core.getInput('github-token')),
      github.context.repo.owner,
      github.context.repo.repo,
      commits,
      fromUsername
    )
    if (untrusted !== undefined) {
      core.info(
        `A commit is not from a pull request opened by '${fromUsername}', not merging`
      )
      return
    }

    core.info(`Merging ${current} into ${next} using ${mergeStrategy} strategy`)
    await exec.exec('git', ['switch', next])
    await exec.exec('git', ['merge', current, '-s', mergeStrategy])
    if (dryRun) {
      core.info('Dry-run is true, not invoking push this time')
    } else {
      await exec.exec('git', ['push', 'origin', next])
    }
  } catch (error) {
    // Fail the workflow run if an error occurs
    if (error instanceof Error) core.setFailed(error.message)
  }
}
