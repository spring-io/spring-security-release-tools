import * as github from '@actions/github'

export interface LatestRelease {
  version: string
  sha: string
}

type Octokit = ReturnType<typeof github.getOctokit>

export async function getLatestRelease(
  octokit: Octokit,
  owner: string,
  repo: string
): Promise<LatestRelease> {
  const { data: release } = await octokit.rest.repos.getLatestRelease({
    owner,
    repo
  })
  const version = release.tag_name.replace(/^v/, '')

  const { data: ref } = await octokit.rest.git.getRef({
    owner,
    repo,
    ref: `tags/${release.tag_name}`
  })

  let sha = ref.object.sha
  if (ref.object.type === 'tag') {
    // An annotated tag ref points at a tag object, not the commit - look up what it targets.
    const { data: tagObject } = await octokit.rest.git.getTag({
      owner,
      repo,
      tag_sha: sha
    })
    sha = tagObject.object.sha
  }

  return { version, sha }
}
