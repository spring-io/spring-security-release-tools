import * as core from '@actions/core'
import * as github from '@actions/github'

type Octokit = ReturnType<typeof github.getOctokit>

/**
 * Finds the first commit whose source cannot be confirmed with GitHub.
 *
 * The author name and email of a commit are set by whoever creates it, so on
 * their own they prove nothing. A commit is trusted here only if it belongs to
 * a merged pull request that was opened by `username`. The pull request author
 * is an account, not a string in the commit, so it cannot be forged by pushing
 * a commit.
 *
 * Rebase merges rewrite commits, and GitHub does not sign the result, so an
 * unsigned commit is accepted only if GitHub itself recorded it as the result
 * of merging that pull request (`merge_commit_sha`) and recorded a user as the
 * one who merged it (`merged_by`). Neither can be set by pushing a commit.
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
    const signed = !!verification?.verified
    const { data: pulls } =
      await octokit.rest.repos.listPullRequestsAssociatedWithCommit({
        owner,
        repo,
        commit_sha: sha,
        per_page: 100
      })
    const candidates = pulls.filter(
      pr => !!pr.merged_at && pr.user?.login === username
    )
    if (candidates.length === 0) {
      core.info(`Commit ${sha} is not in a merged pull request by ${username}`)
      return sha
    }
    if (signed) {
      continue
    }
    if (!(await isMergeResult(octokit, owner, repo, sha, candidates))) {
      core.info(
        `Commit ${sha} is not verified by GitHub (${verification?.reason ?? 'no verification'}) and is not the merge result of a pull request merged by a user`
      )
      return sha
    }
  }
  return undefined
}

/**
 * Whether GitHub recorded `sha` as the result of merging one of the pull
 * requests, and a user as the one who merged it.
 *
 * `merged_by` is only returned when fetching a single pull request.
 */
async function isMergeResult(
  octokit: Octokit,
  owner: string,
  repo: string,
  sha: string,
  candidates: { number: number; merge_commit_sha: string | null }[]
): Promise<boolean> {
  for (const candidate of candidates) {
    if (candidate.merge_commit_sha !== sha) {
      continue
    }
    const { data: pull } = await octokit.rest.pulls.get({
      owner,
      repo,
      pull_number: candidate.number
    })
    if (pull.merge_commit_sha === sha && pull.merged_by?.type === 'User') {
      return true
    }
  }
  return false
}
