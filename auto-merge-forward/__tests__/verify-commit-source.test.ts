import * as github from '@actions/github'
import { findUntrustedCommit } from '../src/verify-commit-source'

type Verification = { verified: boolean; reason: string } | null
type Pull = { merged_at: string | null; user: { login: string } | null }

function octokit(
  commits: Record<string, Verification>,
  pulls: Record<string, Pull[]>
): ReturnType<typeof github.getOctokit> {
  return {
    rest: {
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
const merged = (login: string): Pull => ({
  merged_at: '2026-01-01T00:00:00Z',
  user: { login }
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
      { a: { verified: false, reason: 'unsigned' }, b: null },
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
      { a: [{ merged_at: null, user: { login } }] }
    )

    expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe('a')
  })

  it('does not trust a pull request without a user', async () => {
    const client = octokit(
      { a: verified },
      { a: [{ merged_at: '2026-01-01T00:00:00Z', user: null }] }
    )

    expect(await findUntrustedCommit(client, 'o', 'r', ['a'], login)).toBe('a')
  })

  it('stops at the first commit it cannot confirm', async () => {
    const client = octokit(
      { a: verified, b: { verified: false, reason: 'unsigned' }, c: verified },
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
