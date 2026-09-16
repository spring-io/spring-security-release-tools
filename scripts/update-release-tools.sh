#!/usr/bin/env bash
#
# Updates a project to a target spring-io/spring-security-release-tools
# release, from that project's perspective:
#
#   1. Every SHA-pinned workflow/action reference to
#      spring-io/spring-security-release-tools
#      (e.g. `uses: spring-io/spring-security-release-tools/update-bot@<sha>  # v1.0.17`)
#      has its SHA and trailing version comment rewritten to match, in any
#      *.yml, *.yaml, *.yml.template, or *.yaml.template file.
#
#   2. Every Gradle dependency on the release-tools plugins
#      (io.spring.gradle:spring-security-release-plugin or
#      io.spring.gradle:spring-security-project-plugin) has its version
#      bumped to match, in any *.gradle, *.gradle.kts, or *.toml
#      (e.g. gradle/libs.versions.toml) file.
#
# By default the target release is whichever spring-io/spring-security-release-tools
# tag is newest, resolved from GitHub over the network. Pass a tag explicitly
# to pin to something else instead.
#
# If target-dir is inside a git work tree and any file was changed, the
# changed files are committed with the message "Update to spring-security-release-tools <tag>".
#
# Usage:
#   update-release-tools.sh [target-dir] [tag]
#
#   target-dir  Directory to search. Defaults to the current directory.
#   tag         spring-security-release-tools tag to update to, e.g. v1.0.18
#               (the leading "v" is optional). Defaults to the latest tag on
#               GitHub.

set -euo pipefail

REPO="spring-io/spring-security-release-tools"
REPO_URL="https://github.com/${REPO}.git"

TARGET_DIR="${1:-.}"
TAG="${2:-}"

if [[ ! -d "${TARGET_DIR}" ]]; then
  echo "error: target directory '${TARGET_DIR}' does not exist" >&2
  exit 1
fi

TARGET_DIR="$(cd "${TARGET_DIR}" && pwd)"

if [[ -n "${TAG}" && "${TAG}" != v* ]]; then
  TAG="v${TAG}"
fi

TAGS_REMOTE="$(git ls-remote --tags "${REPO_URL}")"

if [[ -z "${TAG}" ]]; then
  TAG="$(
    echo "${TAGS_REMOTE}" \
      | awk '{print $2}' | sed -E 's#^refs/tags/##' \
      | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
      | sed -E 's/^v//' \
      | sort -t. -k1,1n -k2,2n -k3,3n \
      | tail -n1 \
      | sed -E 's/^/v/'
  )"
fi

if [[ -z "${TAG}" || "${TAG}" == "v" ]]; then
  echo "error: could not determine a release tag for ${REPO}" >&2
  exit 1
fi

VERSION="${TAG#v}"

SHA="$(echo "${TAGS_REMOTE}" | awk -v ref="refs/tags/${TAG}^{}" '$2 == ref {print $1}')"
if [[ -z "${SHA}" ]]; then
  SHA="$(echo "${TAGS_REMOTE}" | awk -v ref="refs/tags/${TAG}" '$2 == ref {print $1}')"
fi

if [[ -z "${SHA}" ]]; then
  echo "error: tag '${TAG}' not found on ${REPO}" >&2
  exit 1
fi

echo "Updating references to ${REPO} -> ${TAG} (${SHA})"

UPDATED_FILES=()

# 1. SHA-pinned workflow/action references, e.g.
#    spring-io/spring-security-release-tools/update-bot@<sha> # v1.0.17
WORKFLOW_MATCH="${REPO}/[A-Za-z0-9._/-]+@[0-9a-f]{40}[[:space:]]*#[[:space:]]*v[0-9]+\.[0-9]+\.[0-9]+"

while IFS= read -r -d '' file; do
  grep -qE "${WORKFLOW_MATCH}" "${file}" || continue

  backup="$(mktemp)"
  cp "${file}" "${backup}"

  perl -pi -e "s{(\Q${REPO}\E/[A-Za-z0-9._/-]+)\@[0-9a-f]{40}([ \t]*#[ \t]*)v[0-9]+\.[0-9]+\.[0-9]+}{\${1}\@${SHA}\${2}${TAG}}g" "${file}"

  if ! cmp -s "${backup}" "${file}"; then
    echo "  updated: ${file}"
    UPDATED_FILES+=("${file}")
  fi
  rm -f "${backup}"
done < <(find "${TARGET_DIR}" \( -name '*.yml' -o -name '*.yaml' -o -name '*.yml.template' -o -name '*.yaml.template' \) -type f -not -path '*/.git/*' -print0)

# 2. Gradle plugin coordinates, e.g.
#    io.spring.gradle:spring-security-release-plugin:1.0.17
PLUGIN_MATCH="io\.spring\.gradle:spring-security-(release|project)-plugin:[0-9]+\.[0-9]+\.[0-9]+"

while IFS= read -r -d '' file; do
  grep -qE "${PLUGIN_MATCH}" "${file}" || continue

  backup="$(mktemp)"
  cp "${file}" "${backup}"

  perl -pi -e "s{(io\.spring\.gradle:spring-security-(?:release|project)-plugin:)[0-9]+\.[0-9]+\.[0-9]+}{\${1}${VERSION}}g" "${file}"

  if ! cmp -s "${backup}" "${file}"; then
    echo "  updated: ${file}"
    UPDATED_FILES+=("${file}")
  fi
  rm -f "${backup}"
done < <(find "${TARGET_DIR}" \( -name '*.gradle' -o -name '*.gradle.kts' -o -name '*.toml' \) -type f -not -path '*/.git/*' -not -path '*/build/*' -print0)

if [[ "${#UPDATED_FILES[@]}" -eq 0 ]]; then
  echo "No outdated references found; nothing to do."
elif git -C "${TARGET_DIR}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git -C "${TARGET_DIR}" add -- "${UPDATED_FILES[@]}"
  git -C "${TARGET_DIR}" commit -m "Update to spring-security-release-tools ${TAG}"
else
  echo "warning: ${TARGET_DIR} is not inside a git work tree; skipping commit" >&2
fi
