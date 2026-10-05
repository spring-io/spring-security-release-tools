import {
  computeNextBranch,
  isSafeBranchName,
  parseRemoteBranches
} from '../src/next-branch'

describe('computeNextBranch', () => {
  const branches = ['5.8.x', '6.4.x', '6.5.x', '7.0.x', '7.1.x', 'main']

  it('picks the next minor', () => {
    expect(computeNextBranch('6.4.x', branches, 'main')).toBe('6.5.x')
  })

  it('crosses a major version', () => {
    expect(computeNextBranch('6.5.x', branches, 'main')).toBe('7.0.x')
  })

  it('sorts numerically rather than lexically', () => {
    const b = ['7.2.x', '7.10.x', '7.9.x']
    expect(computeNextBranch('7.1.x', b, 'main')).toBe('7.2.x')
    expect(computeNextBranch('7.9.x', b, 'main')).toBe('7.10.x')
  })

  it('skips a generation that has no branch', () => {
    expect(computeNextBranch('6.4.x', ['6.4.x', '7.0.x'], 'main')).toBe('7.0.x')
  })

  it('uses the default branch after the newest branch', () => {
    expect(computeNextBranch('7.1.x', branches, 'main')).toBe('main')
  })

  it('uses the default branch when the next generation has no branch yet', () => {
    expect(computeNextBranch('7.1.x', ['7.0.x', '7.1.x'], 'main')).toBe('main')
  })

  it('has nothing to do on the default branch', () => {
    expect(computeNextBranch('main', branches, 'main')).toBeUndefined()
  })

  it('rejects branches that are not N.N.x', () => {
    expect(() => computeNextBranch('feature/x', branches, 'main')).toThrow(
      /not an N.N.x branch/
    )
  })

  it('ignores remote branches that are not N.N.x', () => {
    expect(
      computeNextBranch('7.1.x', ['gh-1', '8.0.x-wip', '7.1.x'], 'main')
    ).toBe('main')
  })
})

describe('parseRemoteBranches', () => {
  it('extracts branch names from ls-remote output', () => {
    const output =
      'abc123\trefs/heads/6.5.x\ndef456\trefs/heads/feature/x\n789aaa\trefs/tags/v1\n'
    expect(parseRemoteBranches(output)).toEqual(['6.5.x', 'feature/x'])
  })
})

describe('isSafeBranchName', () => {
  it.each(['main', '6.5.x', 'feature/x', 'gh-1_a'])('accepts %s', name => {
    expect(isSafeBranchName(name)).toBe(true)
  })

  it.each(['', '-s', '--upload-pack=x', 'a b', 'a;b', '$(x)', '.hidden'])(
    'rejects %j',
    name => {
      expect(isSafeBranchName(name)).toBe(false)
    }
  )
})
