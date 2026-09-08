import * as core from '@actions/core'
import * as exec from '@actions/exec'
import * as github from '@actions/github'
import * as glob from '@actions/glob'
import * as fs from 'fs'
import { getLatestRelease } from './release'
import {
  parseVersionFromGradle,
  parseVersionFromWorkflow,
  updateGradleVersion,
  updateWorkflowRefs
} from './version'

const REPOSITORY = 'spring-io/spring-security-release-tools'

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
  labels: string[]
}

function csv(name: string): string[] {
  return core
    .getInput(name)
    .split(',')
    .map(value => value.trim())
    .filter(value => value.length > 0)
}

function getInputs(): Inputs {
  return {
    token: core.getInput('token', { required: true }),
    labels: csv('labels')
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

export async function run(): Promise<void> {
  try {
    const inputs = getInputs()
    const [owner, repo] = REPOSITORY.split('/')
    const octokit = github.getOctokit(inputs.token)

    let latest
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
        `Could not find a currently pinned version of ${REPOSITORY} in this repository.`
      )
    }

    core.setOutput('previous-version', currentVersion)
    core.setOutput('new-version', latest.version)

    if (currentVersion === latest.version) {
      core.info(`Already up to date with v${latest.version}`)
      core.setOutput('updated', false)
      return
    }

    const changedFiles: string[] = []
    for (const file of gradleFiles) {
      const content = fs.readFileSync(file, 'utf-8')
      const updated = updateGradleVersion(
        content,
        currentVersion,
        latest.version
      )
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
        `Found new release v${latest.version} but no references to ${REPOSITORY} were found to update.`
      )
    }

    core.info(
      `Updating ${changedFiles.length} file(s) from v${currentVersion} to v${latest.version}:`
    )
    for (const file of changedFiles) core.info(`  - ${file}`)

    core.setOutput('updated', true)

    const baseBranch = await currentBranch()
    if (!baseBranch) {
      throw new Error(
        'Could not determine the current branch. Make sure this action runs after a checkout of a named branch (not a detached HEAD).'
      )
    }
    const headBranch = `chore/bump-spring-security-release-tools-${latest.version}`
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

    const { owner: currentOwner, repo: currentRepo } = github.context.repo
    const existingPulls = await octokit.rest.pulls.list({
      owner: currentOwner,
      repo: currentRepo,
      state: 'open',
      head: `${currentOwner}:${headBranch}`,
      base: baseBranch
    })

    let pullNumber: number
    let pullRequestUrl: string
    if (existingPulls.data.length > 0) {
      pullNumber = existingPulls.data[0].number
      pullRequestUrl = existingPulls.data[0].html_url
      await octokit.rest.pulls.update({
        owner: currentOwner,
        repo: currentRepo,
        pull_number: pullNumber,
        title,
        body
      })
      core.info(`Updated existing pull request #${pullNumber}`)
    } else {
      const created = await octokit.rest.pulls.create({
        owner: currentOwner,
        repo: currentRepo,
        title,
        body,
        head: headBranch,
        base: baseBranch
      })
      pullNumber = created.data.number
      pullRequestUrl = created.data.html_url
      core.info(`Opened pull request #${pullNumber}`)
    }

    if (inputs.labels.length > 0) {
      await octokit.rest.issues.addLabels({
        owner: currentOwner,
        repo: currentRepo,
        issue_number: pullNumber,
        labels: inputs.labels
      })
    }

    core.setOutput('pull-request-url', pullRequestUrl)
  } catch (error) {
    if (error instanceof Error) core.setFailed(error.message)
  }
}
