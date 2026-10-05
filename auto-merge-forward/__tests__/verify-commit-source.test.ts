import * as github from '@actions/github'
import { findUntrustedCommit } from '../src/verify-commit-source'

type Verification = { verified: boolean; reason: string } | null
type Pull = {
  number: number
  merged_at: string | null
  merge_commit_sha: string | null
  user: { login: string } | null
}
type FullPull = {
  merge_commit_sha: string | null
  merged_by: { type: string } | null
}

function octokit(
  commits: Record<string, Verification>,
  pulls: Record<string, Pull[]>,
  full: Record<number, FullPull> = {}
): ReturnType<typeof github.getOctokit> {
  return {
    rest: {
      pulls: {
        get: jest.fn(async ({ pull_number }: { pull_number: number }) => ({
          data: full[pull_number]
        }))
      },
      repos: {
        getCommit: jest.fn(async ({ ref }: { ref: string }) => ({
          data: { commit: { verification: commits[ref] } }
        })),
        listPullRequestsAssociatedWithCommit: jest.fn(
          async ({ commit_sha }: { commit_sha: string }) => ({
            data: pulls[commit_sha] ?? []
          })
        )
      }
    }
  } as never
}

const verified = { verified: true, reason: 'valid' }
const unsigned = { verified: false, reason: 'unsigned' }
const merged = (
  login: string,
  merge_commit_sha: string | null = null
): Pull => ({
  number: 1,
  merged_at: '2026-01-01T00:00:00Z',
  merge_commit_sha,
  user: { login }
})
const mergedBy = (
  merge_commit_sha: string,
  type: string | null = 'User'
): Record<number, FullPull> => ({
  1: { merge_commit_sha, merged_by: type ? { type } : null }
})

describe('findUntrustedCommit', () => {
  const login = 'dependabot[bot]'

  it('trusts verified commits in a merged pull request by the login', async () => {
    const client = octokit(
      { a: verified, b: verified },
      { a: [merged(login)], b: [merged('someone'), merged(login)] }
    )

    expect(
      await findUntrustedCommit(client, 'o', 'r', ['a', 'b'], login)
    ).toBeUndefined()
  })

  it('does not trust a commit that is not verified', async () => {
    const client = octokit(
      { a: unsigned, b: null },
      { a: [merged(login)], b: [merged(login)] }
    )

    expect(await findUntrustedCommit(client, 'o', 'r', ['a', 'b'], login)).toBe(
      'a'
    )
  })

  it('does not trust a commit that is in no pull request, e.g. a direct push', async () => {
    const client = octokit({ a: verified }, { a: [] })

    expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe('a')
  })

  it('does not trust a pull request by someone else', async () => {
    const client = octokit({ a: verified }, { a: [merged('mallory')] })

    expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe('a')
  })

  it('does not trust a pull request that is not merged', async () => {
    const client = octokit(
      { a: verified },
      { a: [{ ...merged(login), merged_at: null }] }
    )

    expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe('a')
  })

  it('does not trust a pull request without a user', async () => {
    const client = octokit(
      { a: verified },
      { a: [{ ...merged(login), user: null }] }
    )

    expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe('a')
  })

  describe('unsigned commits, e.g. from a rebase merge', () => {
    it('trusts the merge result of a pull request merged by a user', async () => {
      const client = octokit(
        { a: unsigned },
        { a: [merged(login, 'a')] },
        mergedBy('a')
      )

      expect(
        await findUntrustedCommit(client, 'o', 'r', ['a'], login)
      ).toBeUndefined()
    })

    it('does not trust a commit that is not the merge result of the pull request', async () => {
      const client = octokit(
        { a: unsigned },
        { a: [merged(login, 'other')] },
        mergedBy('other')
      )

      expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe(
        'a'
      )
      expect(client.rest.pulls.get).not.toHaveBeenCalled()
    })

    it('does not trust a commit when the full pull request disagrees', async () => {
      const client = octokit(
        { a: unsigned },
        { a: [merged(login, 'a')] },
        mergedBy('other')
      )

      expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe(
        'a'
      )
    })

    it('does not trust a pull request that was not merged by a user', async () => {
      for (const type of ['Bot', null]) {
        const client = octokit(
          { a: unsigned },
          { a: [merged(login, 'a')] },
          mergedBy('a', type)
        )

        expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe(
          'a'
        )
      }
    })

    it('does not trust a forged author on a direct push', async () => {
      const client = octokit({ a: unsigned }, { a: [] })

      expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe(
        'a'
      )
    })

    it('does not trust the merge result of a pull request by someone else', async () => {
      const client = octokit(
        { a: unsigned },
        { a: [merged('mallory', 'a')] },
        mergedBy('a')
      )

      expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe(
        'a'
      )
    })

    it('does not look up the full pull request for signed commits', async () => {
      const client = octokit({ a: verified }, { a: [merged(login)] })

      expect(
        await findUntrustedCommit(client, 'o', 'r', ['a'], login)
      ).toBeUndefined()
      expect(client.rest.pulls.get).not.toHaveBeenCalled()
    })
  })

  it('stops at the first commit it cannot confirm', async () => {
    const client = octokit(
      { a: verified, b: unsigned, c: verified },
      { a: [merged(login)], b: [merged(login)], c: [merged(login)] }
    )

    expect(
      await findUntrustedCommit(client, 'o', 'r', ['a', 'b', 'c'], login)
    ).toBe('b')
    expect(client.rest.repos.getCommit).toHaveBeenCalledTimes(2)
  })

  it('does not swallow API errors', async () => {
    const client = {
      rest: {
        repos: {
          getCommit: jest.fn().mockRejectedValue(new Error('rate limited'))
        }
      }
    } as never

    await expect(
      findUntrustedCommit(client, 'o', 'r', ['a'], login)
    ).rejects.toThrow('rate limited')
  })
})
