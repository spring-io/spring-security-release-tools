import {
  parseVersionFromGradle,
  parseVersionFromWorkflow,
  updateGradleVersion,
  updateWorkflowRefs
} from '../src/version'

const REPO = 'spring-io/spring-security-release-tools'

describe('parseVersionFromGradle', () => {
  it('finds the version from a version catalog entry', () => {
    const content =
      'io-spring-security-release-plugin = "io.spring.gradle:spring-security-release-plugin:1.0.17"'
    expect(parseVersionFromGradle(content)).toBe('1.0.17')
  })

  it('finds the version from an inline classpath declaration', () => {
    const content =
      "classpath 'io.spring.gradle:spring-security-release-plugin:1.0.17'"
    expect(parseVersionFromGradle(content)).toBe('1.0.17')
  })

  it('returns undefined when the coordinate is not present', () => {
    expect(parseVersionFromGradle('some unrelated content')).toBeUndefined()
  })

  it('finds the version from a settings.gradle plugins DSL declaration', () => {
    const content = [
      'plugins {',
      '\tid "io.spring.security.settings" version "1.0.20"',
      '}'
    ].join('\n')
    expect(parseVersionFromGradle(content)).toBe('1.0.20')
  })

  it('finds the version from a single-quoted plugins DSL declaration', () => {
    const content = [
      'plugins {',
      "    id 'io.spring.security.settings' version '1.0.20'",
      '}'
    ].join('\n')
    expect(parseVersionFromGradle(content)).toBe('1.0.20')
  })
})

describe('parseVersionFromWorkflow', () => {
  it('finds the version pinned in a workflow reference', () => {
    const content = `    uses: ${REPO}/.github/workflows/build.yml@b92832ecbc7cbe969201e6beafbde0ee400cf095 # v1.0.15`
    expect(parseVersionFromWorkflow(content, REPO)).toBe('1.0.15')
  })

  it('returns undefined when there is no reference to the repository', () => {
    expect(
      parseVersionFromWorkflow('uses: actions/checkout@v4', REPO)
    ).toBeUndefined()
  })
})

describe('updateGradleVersion', () => {
  it('replaces only the matching old version', () => {
    const content =
      'io-spring-security-release-plugin = "io.spring.gradle:spring-security-release-plugin:1.0.17"\n' +
      'other = "com.example:other:1.0.17"'
    const updated = updateGradleVersion(content, '1.0.17', '1.0.18')
    expect(updated).toContain(
      'io.spring.gradle:spring-security-release-plugin:1.0.18'
    )
    expect(updated).toContain('com.example:other:1.0.17')
  })

  it('returns the content unchanged when the old version is not present', () => {
    const content = 'io.spring.gradle:spring-security-release-plugin:1.0.16'
    expect(updateGradleVersion(content, '1.0.17', '1.0.18')).toBe(content)
  })

  it('replaces the version in a settings.gradle plugins DSL declaration', () => {
    const content = [
      'plugins {',
      '\tid "io.spring.security.settings" version "1.0.20"',
      '}'
    ].join('\n')
    const updated = updateGradleVersion(content, '1.0.20', '1.0.21')
    expect(updated).toContain(
      'id "io.spring.security.settings" version "1.0.21"'
    )
  })

  it('leaves an unrelated plugin unchanged even if its version matches', () => {
    const content = [
      'plugins {',
      '\tid "io.spring.security.settings" version "1.0.20"',
      '\tid "io.spring.develocity.conventions" version "1.0.20"',
      '}'
    ].join('\n')
    const updated = updateGradleVersion(content, '1.0.20', '1.0.21')
    expect(updated).toContain(
      'id "io.spring.security.settings" version "1.0.21"'
    )
    expect(updated).toContain(
      'id "io.spring.develocity.conventions" version "1.0.20"'
    )
  })
})

describe('updateWorkflowRefs', () => {
  it('rewrites the sha and version comment while preserving the referenced path', () => {
    const content = [
      `    uses: ${REPO}/.github/workflows/build.yml@b92832ecbc7cbe969201e6beafbde0ee400cf095 # v1.0.15`,
      `        uses: ${REPO}/.github/actions/send-notification@b92832ecbc7cbe969201e6beafbde0ee400cf095 # v1.0.15`
    ].join('\n')

    const updated = updateWorkflowRefs(
      content,
      REPO,
      'd6c65d3013c0888e2c9cbae9f4beda610994776c',
      '1.0.16'
    )

    expect(updated).toContain(
      `uses: ${REPO}/.github/workflows/build.yml@d6c65d3013c0888e2c9cbae9f4beda610994776c # v1.0.16`
    )
    expect(updated).toContain(
      `uses: ${REPO}/.github/actions/send-notification@d6c65d3013c0888e2c9cbae9f4beda610994776c # v1.0.16`
    )
  })

  it('leaves unrelated references untouched', () => {
    const content = 'uses: actions/checkout@v4'
    expect(updateWorkflowRefs(content, REPO, 'deadbeef', '1.0.16')).toBe(
      content
    )
  })

  it('does not touch references pinned to a moving tag instead of a sha', () => {
    const content = `uses: ${REPO}/.github/actions/send-notification@v1`
    expect(updateWorkflowRefs(content, REPO, 'deadbeef', '1.0.16')).toBe(
      content
    )
  })
})
