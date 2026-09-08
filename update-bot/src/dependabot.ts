import * as yaml from 'js-yaml'

export interface GithubActionsUpdate {
  targetBranch: string
  milestone?: number
  labels?: string[]
}

interface DependabotUpdateEntry {
  'package-ecosystem': string
  'target-branch'?: string
  milestone?: number
  labels?: string[]
}

interface DependabotConfig {
  updates?: DependabotUpdateEntry[]
}

/**
 * Parses the `github-actions` entries out of a dependabot.yml file, merging
 * entries that share the same target branch (e.g. one per directory).
 */
export function parseGithubActionsUpdates(
  content: string,
  defaultBranch: string
): GithubActionsUpdate[] {
  const config = (yaml.load(content) ?? {}) as DependabotConfig
  const entries = (config.updates ?? []).filter(
    entry => entry['package-ecosystem'] === 'github-actions'
  )

  const byBranch = new Map<string, GithubActionsUpdate>()
  for (const entry of entries) {
    const targetBranch = entry['target-branch'] ?? defaultBranch

    const existing = byBranch.get(targetBranch)
    if (!existing) {
      byBranch.set(targetBranch, {
        targetBranch,
        milestone: entry.milestone,
        labels: entry.labels
      })
      continue
    }

    if (entry.labels) {
      existing.labels = [...(existing.labels ?? []), ...entry.labels]
    }
    if (existing.milestone === undefined) {
      existing.milestone = entry.milestone
    }
  }

  return [...byBranch.values()]
}
