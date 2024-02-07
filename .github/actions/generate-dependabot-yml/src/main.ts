import * as core from '@actions/core'
import * as yaml from 'js-yaml'
import * as fs from 'fs'

interface Update {
  'package-ecosystem': string
  'target-branch': string
}

interface Template {
  updates: Update[]
}

const inputs = {
  gradleBranches: (core.getInput('gradle-branches') as string).split(','),
  githubActionsBranches: (
    core.getInput('github-actions-branches') as string
  ).split(','),
  templateFile: core.getInput('template-file')
}

/**
 * The main function for the action.
 * @returns {Promise<void>} Resolves when the action is complete.
 */
export async function run(): Promise<void> {
  try {
    const template = yaml.load(
      fs.readFileSync(inputs.templateFile, 'utf-8')
    ) as Template
    const updatesTemplate = template.updates
    const resolvedUpdates: Update[] = []

    for (const baseUpdate of updatesTemplate) {
      if (baseUpdate['package-ecosystem'] == 'gradle') {
        resolvedUpdates.push(
          ...resolveUpdate(baseUpdate, inputs.gradleBranches)
        )
        continue
      }
      if (baseUpdate['package-ecosystem'] == 'github-actions') {
        resolvedUpdates.push(
          ...resolveUpdate(baseUpdate, inputs.githubActionsBranches)
        )
        continue
      }
    }
    core.info(`Resolved updates ${resolvedUpdates}`)
    template.updates = resolvedUpdates
    core.info('Final template:')
    const finalTemplate = yaml.dump(template, { noRefs: true })
    core.info(finalTemplate)
    core.info('Writing to .github/dependabot.yml')
    fs.writeFileSync('.github/dependabot.yml', finalTemplate)
  } catch (error) {
    // Fail the workflow run if an error occurs
    if (error instanceof Error) core.setFailed(error.message)
  }
}

function resolveUpdate(baseUpdate: Update, branches: string[]): Update[] {
  const updates: Update[] = []
  if (branches.length === 0) {
    return updates
  }
  for (const branch of branches) {
    const resolved: Update = {
      ...baseUpdate,
      'target-branch': branch
    }
    updates.push(resolved)
  }
  return updates
}
