import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as github from '@actions/github'

/**
 * The main function for the action.
 * @returns {Promise<void>} Resolves when the action is complete.
 */
export async function run(): Promise<void> {
  try {
    const fromAuthor = core.getInput('from-author')
    const branches: string[] = core
      .getInput('branches')
      .split(',')
      .map(b => b.trim())
    const mergeStrategy: string = core.getInput('merge-strategy')
    const dryRun: boolean = core.getInput('dry-run') === 'true'
    const useAuthorEmail: boolean = core.getInput('use-author-email') === 'true'
    const logFormat = useAuthorEmail ? '%ae' : '%an'

    if (!branches || branches.length < 2) {
      throw new Error('Please specify at least 2 branches')
    }

    const originBranch = github.context.ref.split('/')[2]
    for (const branch of branches) {
      if (branch === originBranch) {
        await exec.exec('git', ['fetch', 'origin', branch, '--unshallow'])
        continue
      }
      await exec.exec('git', ['fetch', 'origin', branch])
      await exec.exec('git', ['switch', branch])
      await exec.exec('git', ['switch', '-'])
    }

    const branchesToPush: string[] = []

    for (let i = 1; i < branches.length; i++) {
      const previousBranch = branches[i - 1]
      const currentBranch = branches[i]

      let gitLogOutput = ''
      const options: exec.ExecOptions = {
        listeners: {
          stdout: (data: Buffer) => {
            gitLogOutput = data.toString()
          }
        }
      }

      await exec.exec(
        'git',
        [
          'log',
          previousBranch,
          `^${currentBranch}`,
          `--format=${logFormat}`,
          '--no-merges'
        ],
        options
      )
      const authorsFromLog = gitLogOutput.split('\n').filter(v => !!v)
      core.info(
        `Found ${authorsFromLog.length} commits in ${previousBranch} that are not present in ${currentBranch}`
      )
      const authors = new Set<string>(authorsFromLog)
      core.info(`Found ${authors.size} unique commit actors`)
      if (authors.size == 1 && authors.has(fromAuthor)) {
        core.info(
          `Merging ${previousBranch} into ${currentBranch} using ${mergeStrategy} strategy`
        )
        await exec.exec('git', ['switch', currentBranch])
        await exec.exec('git', ['merge', previousBranch, '-s', mergeStrategy])
        branchesToPush.push(currentBranch)
      } else {
        core.info(
          `Expected author '${fromAuthor}' not found or there are multiple authors`
        )
        if (branchesToPush.length > 0) {
          throw new Error(
            'Aborted because cannot guarantee the successful merge between all branches'
          )
        }
      }
    }

    if (branchesToPush.length === 0) {
      return
    }
    if (dryRun) {
      core.info('Dry-run is true, not invoking push this time')
    } else {
      const pushCommand: string[] = [
        'push',
        '--atomic',
        'origin',
        ...branchesToPush
      ]
      exec.exec('git', pushCommand)
    }
  } catch (error) {
    // Fail the workflow run if an error occurs
    if (error instanceof Error) core.setFailed(error.message)
  }
}
