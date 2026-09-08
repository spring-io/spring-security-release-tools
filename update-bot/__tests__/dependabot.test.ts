import { parseGithubActionsUpdates } from '../src/dependabot'

describe('parseGithubActionsUpdates', () => {
  it('ignores entries for other package ecosystems', () => {
    const yaml = `
updates:
  - package-ecosystem: gradle
    target-branch: main
  - package-ecosystem: github-actions
    target-branch: main
`
    const updates = parseGithubActionsUpdates(yaml, 'main')

    expect(updates).toEqual([
      { targetBranch: 'main', milestone: undefined, labels: undefined }
    ])
  })

  it('defaults the target branch when one is not specified', () => {
    const yaml = `
updates:
  - package-ecosystem: github-actions
`
    const updates = parseGithubActionsUpdates(yaml, 'trunk')

    expect(updates).toEqual([
      { targetBranch: 'trunk', milestone: undefined, labels: undefined }
    ])
  })

  it('captures milestone and labels for a branch', () => {
    const yaml = `
updates:
  - package-ecosystem: github-actions
    target-branch: main
    milestone: 42
    labels:
      - 'type: task'
`
    const updates = parseGithubActionsUpdates(yaml, 'main')

    expect(updates).toEqual([
      {
        targetBranch: 'main',
        milestone: 42,
        labels: ['type: task']
      }
    ])
  })

  it('merges multiple entries that share the same target branch', () => {
    const yaml = `
updates:
  - package-ecosystem: github-actions
    target-branch: main
    directory: /
    milestone: 1
    labels:
      - 'label-a'
  - package-ecosystem: github-actions
    target-branch: main
    directory: /docs
    milestone: 2
    labels:
      - 'label-b'
`
    const updates = parseGithubActionsUpdates(yaml, 'main')

    expect(updates).toEqual([
      {
        targetBranch: 'main',
        milestone: 1,
        labels: ['label-a', 'label-b']
      }
    ])
  })

  it('keeps branches distinct', () => {
    const yaml = `
updates:
  - package-ecosystem: github-actions
    target-branch: main
  - package-ecosystem: github-actions
    target-branch: 6.1.x
`
    const updates = parseGithubActionsUpdates(yaml, 'main')

    expect(updates.map(update => update.targetBranch)).toEqual([
      'main',
      '6.1.x'
    ])
  })
})
