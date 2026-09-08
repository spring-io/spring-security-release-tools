import * as github from '@actions/github'
import { getLatestRelease } from '../src/release'

function mockOctokit(overrides: {
  tagName: string
  ref: { sha: string; type: string }
  tagObjectSha?: string
}): ReturnType<typeof github.getOctokit> {
  return {
    rest: {
      repos: {
        getLatestRelease: jest
          .fn()
          .mockResolvedValue({ data: { tag_name: overrides.tagName } })
      },
      git: {
        getRef: jest.fn().mockResolvedValue({
          data: { object: overrides.ref }
        }),
        getTag: jest.fn().mockResolvedValue({
          data: { object: { sha: overrides.tagObjectSha } }
        })
      }
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any
}

describe('getLatestRelease', () => {
  it('resolves the version and sha for a lightweight tag', async () => {
    const octokit = mockOctokit({
      tagName: 'v1.0.17',
      ref: { sha: 'commitsha1', type: 'commit' }
    })

    const result = await getLatestRelease(
      octokit,
      'spring-io',
      'spring-security-release-tools'
    )

    expect(result).toEqual({ version: '1.0.17', sha: 'commitsha1' })
    expect(octokit.rest.git.getTag).not.toHaveBeenCalled()
  })

  it('dereferences an annotated tag to the commit it points to', async () => {
    const octokit = mockOctokit({
      tagName: 'v1.0.17',
      ref: { sha: 'tagsha1', type: 'tag' },
      tagObjectSha: 'commitsha2'
    })

    const result = await getLatestRelease(
      octokit,
      'spring-io',
      'spring-security-release-tools'
    )

    expect(result).toEqual({ version: '1.0.17', sha: 'commitsha2' })
    expect(octokit.rest.git.getTag).toHaveBeenCalledWith({
      owner: 'spring-io',
      repo: 'spring-security-release-tools',
      tag_sha: 'tagsha1'
    })
  })

  it('strips the leading v from the release tag', async () => {
    const octokit = mockOctokit({
      tagName: 'v2.3.4',
      ref: { sha: 'commitsha3', type: 'commit' }
    })

    const result = await getLatestRelease(octokit, 'owner', 'repo')

    expect(result.version).toBe('2.3.4')
  })
})
