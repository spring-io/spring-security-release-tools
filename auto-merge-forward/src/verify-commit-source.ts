import * as core from '@actions/core'
import * as github from '@actions/github'

type Octokit = ReturnType<typeof github.getOctokit>

/**
 * Finds the first commit whose source cannot be confirmed with GitHub.
 *
 * The author name and email of a commit are set by whoever creates it, so on
 * their own they prove nothing. A commit is trusted here only if GitHub
 * verified its signature and it belongs to a merged pull request that was
 * opened by `username`. The pull request author is an account, not a string
 * in the commit, so it cannot be forged by pushing a commit.
 *
 * Stops at the first commit that cannot be confirmed to save API calls.
 * Errors from the API are not caught: if GitHub cannot be asked, no commit is
 * trusted.
 *
 * @returns the SHA of the first commit that could not be confirmed, if any
 */
export async function findUntrustedCommit(
  octokit: Octokit,
  owner: string,
  repo: string,
  shas: string[],
  username: string
): Promise<string | undefined> {
  for (const sha of shas) {
    const { data: commit } = await octokit.rest.repos.getCommit({
      owner,
      repo,
      ref: sha
    })
    const verification = commit.commit.verification
    if (!verification?.verified) {
      core.info(
        `Commit ${sha} is not verified by GitHub (${verification?.reason ?? 'no verification'})`
      )
      return sha
    }
    const { data: pulls } =
      await octokit.rest.repos.listPullRequestsAssociatedWithCommit({
        owner,
        repo,
        commit_sha: sha,
        per_page: 100
      })
    if (!pulls.some(pr => !!pr.merged_at && pr.user?.login === username)) {
      core.info(`Commit ${sha} is not in a merged pull request by ${username}`)
      return sha
    }
  }
  return undefined
}
