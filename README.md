Spring Security Release Tools
==

Shared workflows, Libraries and plugins for managing automated releases of Spring projects.

Shared Workflows
===

The shared workflows in this repository use the following secrets:

* `GH_ACTIONS_REPO_TOKEN`: A GitHub PAT (personal access token) used for updating the repository, including accessing repository contents, opening and closing milestones and creating GitHub releases. The token must belong to a user that is part of the `spring-projects` GitHub organization, and requires the following permissions for [fine-grained personal access tokens](https://docs.github.com/rest/overview/permissions-required-for-fine-grained-personal-access-tokens):
  * Contents: Read and write
  * Issues: Read and write
  * Workflows: Read and write (only needed if the token will be used to open pull requests that modify files under `.github/workflows/`, e.g. as a `token` input to [`update-bot`](update-bot); the default `github.token` can never be granted this, no matter what `permissions:` the calling workflow declares)

Plugins
===

* [`spring-security-release-plugin`](release-plugin) ([docs](release-plugin/README.adoc))
* [`spring-security-maven-plugin`](maven-plugin) ([docs](maven-plugin/README.adoc))

Libraries
===

* [`spring-security-release-tools-core`](core) (see javadoc for [SpringReleases.java](core/src/main/java/io/spring/release/SpringReleases.java))
* [`github-api`](api/github) (see javadoc for [GitHubApi.java](api/github/src/main/java/com/github/api/GitHubApi.java))
* [`sagan-api`](api/sagan) (see javadoc for [SaganApi.java](api/sagan/src/main/java/io/spring/api/SaganApi.java))
