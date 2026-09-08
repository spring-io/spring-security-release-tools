import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as github from '@actions/github'
import * as glob from '@actions/glob'
import * as fs from 'fs'
import { GithubActionsUpdate, parseGithubActionsUpdates } from './dependabot'
import { getLatestRelease, LatestRelease } from './release'
import {
  parseVersionFromGradle,
  parseVersionFromWorkflow,
  updateGradleVersion,
  updateWorkflowRefs
} from './version'

const REPOSITORY = 'spring-io/spring-security-release-tools'
const DEPENDABOT_CONFIG_PATH = '.github/dependabot.yml'
const DEFAULT_LABELS = ['type: dependency-upgrade']

const GRADLE_FILE_GLOB = ['**/*.toml', '**/*.gradle', '**/*.gradle.kts']
const WORKFLOW_FILE_GLOB = ['.github/**/*.yml', '.github/**/*.yaml']

const EXCLUDED_PATHS = [
  '!**/node_modules/**',
  '!**/.git/**',
  '!**/build/**',
  '!**/.gradle/**',
  '!**/out/**'
]

interface Inputs {
  token: string
}

interface BranchResult {
  branch: string
  updated?: boolean
  previousVersion?: string
  newVersion?: string
  pullRequestUrl?: string
  error?: string
}

function getInputs(): Inputs {
  return {
    token: core.getInput('token', { required: true })
  }
}

async function findFiles(patterns: string[]): Promise<string[]> {
  const globber = await glob.create([...patterns, ...EXCLUDED_PATHS].join('\n'))
  return globber.glob()
}

async function currentBranch(): Promise<string> {
  let branch = ''
  await exec.exec('git', ['branch', '--show-current'], {
    listeners: {
      stdout: (data: Buffer) => {
        branch += data.toString()
      }
    }
  })
  return branch.trim()
}

async function checkoutBranch(branch: string): Promise<void> {
  await exec.exec('git', ['fetch', 'origin', branch])
  await exec.exec('git', ['checkout', '-B', branch, `origin/${branch}`])
}

function readDependabotUpdates(defaultBranch: string): GithubActionsUpdate[] {
  if (!fs.existsSync(DEPENDABOT_CONFIG_PATH)) {
    throw new Error(
      `Could not find ${DEPENDABOT_CONFIG_PATH}. This action reads it to determine which branches to update and which dependencies to leave alone.`
    )
  }
  const updates = parseGithubActionsUpdates(
    fs.readFileSync(DEPENDABOT_CONFIG_PATH, 'utf-8'),
    defaultBranch
  )
  if (updates.length === 0) {
    throw new Error(
      `Found ${DEPENDABOT_CONFIG_PATH}, but it has no "package-ecosystem: github-actions" entries.`
    )
  }
  return updates
}

type Octokit = ReturnType<typeof github.getOctokit>

async function updateBranch(
  octokit: Octokit,
  repoOwner: string,
  repoName: string,
  latest: LatestRelease,
  update: GithubActionsUpdate
): Promise<BranchResult> {
  const branch = update.targetBranch

  await checkoutBranch(branch)

  const gradleFiles = await findFiles(GRADLE_FILE_GLOB)
  const workflowFiles = await findFiles(WORKFLOW_FILE_GLOB)

  let currentVersion: string | undefined
  for (const file of gradleFiles) {
    currentVersion = parseVersionFromGradle(fs.readFileSync(file, 'utf-8'))
    if (currentVersion) break
  }
  if (!currentVersion) {
    for (const file of workflowFiles) {
      currentVersion = parseVersionFromWorkflow(
        fs.readFileSync(file, 'utf-8'),
        REPOSITORY
      )
      if (currentVersion) break
    }
  }
  if (!currentVersion) {
    throw new Error(
      `Could not find a currently pinned version of ${REPOSITORY} on branch ${branch}.`
    )
  }

  if (currentVersion === latest.version) {
    core.info(`[${branch}] Already up to date with v${latest.version}`)
    return {
      branch,
      updated: false,
      previousVersion: currentVersion,
      newVersion: latest.version
    }
  }

  const changedFiles: string[] = []
  for (const file of gradleFiles) {
    const content = fs.readFileSync(file, 'utf-8')
    const updated = updateGradleVersion(content, currentVersion, latest.version)
    if (updated !== content) {
      fs.writeFileSync(file, updated)
      changedFiles.push(file)
    }
  }
  for (const file of workflowFiles) {
    const content = fs.readFileSync(file, 'utf-8')
    const updated = updateWorkflowRefs(
      content,
      REPOSITORY,
      latest.sha,
      latest.version
    )
    if (updated !== content) {
      fs.writeFileSync(file, updated)
      changedFiles.push(file)
    }
  }

  if (changedFiles.length === 0) {
    throw new Error(
      `Found new release v${latest.version} but no references to ${REPOSITORY} were found to update on branch ${branch}.`
    )
  }

  core.info(
    `[${branch}] Updating ${changedFiles.length} file(s) from v${currentVersion} to v${latest.version}:`
  )
  for (const file of changedFiles) core.info(`  - ${file}`)

  const headBranch = `chore/bump-spring-security-release-tools-${latest.version}-${branch}`
  const title = `Bump ${REPOSITORY} from ${currentVersion} to ${latest.version}`
  const body = [
    `Bumps [${REPOSITORY}](https://github.com/${REPOSITORY}) from \`${currentVersion}\` to \`${latest.version}\`.`,
    '',
    'Updated files:',
    ...changedFiles.map(file => `- \`${file}\``)
  ].join('\n')

  await exec.exec('git', ['config', 'user.name', 'github-actions[bot]'])
  await exec.exec('git', [
    'config',
    'user.email',
    '41898282+github-actions[bot]@users.noreply.github.com'
  ])
  await exec.exec('git', ['checkout', '-B', headBranch])
  await exec.exec('git', ['add', ...changedFiles])
  await exec.exec('git', ['commit', '-m', title])
  await exec.exec('git', [
    'push',
    '--force',
    'origin',
    `${headBranch}:${headBranch}`
  ])

  const existingPulls = await octokit.rest.pulls.list({
    owner: repoOwner,
    repo: repoName,
    state: 'open',
    head: `${repoOwner}:${headBranch}`,
    base: branch
  })

  let pullNumber: number
  let pullRequestUrl: string
  if (existingPulls.data.length > 0) {
    pullNumber = existingPulls.data[0].number
    pullRequestUrl = existingPulls.data[0].html_url
    await octokit.rest.pulls.update({
      owner: repoOwner,
      repo: repoName,
      pull_number: pullNumber,
      title,
      body
    })
    core.info(`[${branch}] Updated existing pull request #${pullNumber}`)
  } else {
    const created = await octokit.rest.pulls.create({
      owner: repoOwner,
      repo: repoName,
      title,
      body,
      head: headBranch,
      base: branch
    })
    pullNumber = created.data.number
    pullRequestUrl = created.data.html_url
    core.info(`[${branch}] Opened pull request #${pullNumber}`)
  }

  const labels = update.labels ?? DEFAULT_LABELS
  if (labels.length > 0) {
    await octokit.rest.issues.addLabels({
      owner: repoOwner,
      repo: repoName,
      issue_number: pullNumber,
      labels
    })
  }
  if (update.milestone !== undefined) {
    await octokit.rest.issues.update({
      owner: repoOwner,
      repo: repoName,
      issue_number: pullNumber,
      milestone: update.milestone
    })
  }

  return {
    branch,
    updated: true,
    previousVersion: currentVersion,
    newVersion: latest.version,
    pullRequestUrl
  }
}

async function writeSummary(results: BranchResult[]): Promise<void> {
  core.summary.addHeading('Update Bot', 2)
  core.summary.addTable([
    [
      { data: 'Branch', header: true },
      { data: 'Result', header: true }
    ],
    ...results.map(result => [result.branch, describeResult(result)])
  ])
  await core.summary.write()
}

function describeResult(result: BranchResult): string {
  if (result.error) return `❌ Failed: ${result.error}`
  if (result.updated) {
    return `⬆️ [${result.previousVersion} → ${result.newVersion}](${result.pullRequestUrl})`
  }
  return `✅ Already up to date (v${result.previousVersion})`
}

export async function run(): Promise<void> {
  try {
    const inputs = getInputs()
    const [owner, repo] = REPOSITORY.split('/')
    const octokit = github.getOctokit(inputs.token)

    let latest: LatestRelease
    try {
      latest = await getLatestRelease(octokit, owner, repo)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      throw new Error(
        `Could not fetch the latest release for ${REPOSITORY}: ${message}`
      )
    }
    core.info(
      `Latest release of ${REPOSITORY} is v${latest.version} (${latest.sha})`
    )

    const homeBranch = await currentBranch()
    if (!homeBranch) {
      throw new Error(
        'Could not determine the current branch. Make sure this action runs after a checkout of a named branch (not a detached HEAD).'
      )
    }

    const updates = readDependabotUpdates(homeBranch)

    const { owner: currentOwner, repo: currentRepo } = github.context.repo
    const results: BranchResult[] = []
    for (const update of updates) {
      try {
        results.push(
          await updateBranch(octokit, currentOwner, currentRepo, latest, update)
        )
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error)
        core.error(`[${update.targetBranch}] ${message}`)
        results.push({ branch: update.targetBranch, error: message })
      }
    }

    await checkoutBranch(homeBranch)
    await writeSummary(results)

    const failures = results.filter(result => result.error)
    if (failures.length > 0) {
      core.setFailed(
        `Failed to update ${failures.length} branch(es): ${failures
          .map(failure => failure.branch)
          .join(', ')}`
      )
    }
  } catch (error) {
    if (error instanceof Error) core.setFailed(error.message)
  }
}
