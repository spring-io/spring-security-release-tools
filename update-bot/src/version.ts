function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function gradleCoordinatePattern(): RegExp {
  return /io\.spring\.gradle:spring-security-release-plugin:([\w.-]+)/
}

function settingsPluginPattern(): RegExp {
  return /id\s+["']io\.spring\.security\.settings["']\s+version\s+["']([\w.-]+)["']/
}

function workflowReferencePattern(repository: string): RegExp {
  const repo = escapeRegExp(repository)
  return new RegExp(
    `(?<path>${repo}/[^@\\s]+)@[0-9a-f]{40}(?<separator>\\s*#\\s*)v(?<version>[\\w.-]+)`,
    'g'
  )
}

export function parseVersionFromGradle(content: string): string | undefined {
  return (
    content.match(gradleCoordinatePattern())?.[1] ??
    content.match(settingsPluginPattern())?.[1]
  )
}

export function parseVersionFromWorkflow(
  content: string,
  repository: string
): string | undefined {
  const pattern = workflowReferencePattern(repository)
  pattern.lastIndex = 0
  return pattern.exec(content)?.groups?.version
}

export function updateGradleVersion(
  content: string,
  oldVersion: string,
  newVersion: string
): string {
  const coordinatePattern = new RegExp(
    `io\\.spring\\.gradle:spring-security-release-plugin:${escapeRegExp(oldVersion)}`,
    'g'
  )
  const pluginPattern = new RegExp(
    `(id\\s+["']io\\.spring\\.security\\.settings["']\\s+version\\s+["'])${escapeRegExp(oldVersion)}(["'])`,
    'g'
  )
  return content
    .replace(
      coordinatePattern,
      `io.spring.gradle:spring-security-release-plugin:${newVersion}`
    )
    .replace(pluginPattern, `$1${newVersion}$2`)
}

export function updateWorkflowRefs(
  content: string,
  repository: string,
  newSha: string,
  newVersion: string
): string {
  return content.replace(
    workflowReferencePattern(repository),
    `$<path>@${newSha}$<separator>v${newVersion}`
  )
}
