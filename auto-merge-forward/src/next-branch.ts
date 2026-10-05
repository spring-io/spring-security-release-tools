const RELEASE_BRANCH = /^(\d+)\.(\d+)\.x$/
const SAFE_BRANCH = /^[A-Za-z0-9_][A-Za-z0-9._/-]*$/

/**
 * Whether the name is safe to hand to git as a positional argument,
 * i.e. it cannot be mistaken for an option.
 */
export function isSafeBranchName(name: string): boolean {
  return SAFE_BRANCH.test(name)
}

function parseReleaseBranch(name: string): [number, number] | undefined {
  const match = RELEASE_BRANCH.exec(name)
  return match ? [Number(match[1]), Number(match[2])] : undefined
}

function compare(a: [number, number], b: [number, number]): number {
  return a[0] - b[0] || a[1] - b[1]
}

/**
 * Parses the output of `git ls-remote --heads` into branch names.
 */
export function parseRemoteBranches(lsRemoteOutput: string): string[] {
  const prefix = 'refs/heads/'
  return lsRemoteOutput
    .split('\n')
    .map(line => line.split('\t')[1])
    .filter(ref => !!ref && ref.startsWith(prefix))
    .map(ref => ref.substring(prefix.length))
}

/**
 * Computes the branch that commits on `current` should be merged forward into.
 *
 * That is the lowest `N.N.x` branch above `current` that exists in
 * `remoteBranches` or, if there is none, `defaultBranch`.
 *
 * @returns the next branch, or `undefined` if `current` is `defaultBranch`
 * @throws if `current` is neither `defaultBranch` nor an `N.N.x` branch
 */
export function computeNextBranch(
  current: string,
  remoteBranches: string[],
  defaultBranch: string
): string | undefined {
  if (current === defaultBranch) {
    return undefined
  }
  const currentVersion = parseReleaseBranch(current)
  if (!currentVersion) {
    throw new Error(
      `Cannot compute the next branch for '${current}', it is not an N.N.x branch`
    )
  }
  const next = remoteBranches
    .map(name => ({ name, version: parseReleaseBranch(name) }))
    .filter(
      (b): b is { name: string; version: [number, number] } =>
        !!b.version && compare(b.version, currentVersion) > 0
    )
    .sort((a, b) => compare(a.version, b.version))[0]
  return next ? next.name : defaultBranch
}
