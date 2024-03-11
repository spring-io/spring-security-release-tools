# Get Workflow Run ID

Get the ID of the latest successful workflow run.

Accepts the following inputs:

* `repository` (Required) - The repository owner and name (e.g. `spring-projects/spring-security`).
* `workflow-id` (Required) - The ID of the workflow or the workflow file name.

Produces the following output:

* `run-id` - The ID of the latest successful workflow run.

## Installation

```yaml
- id: get-workflow-run-id
  name: Get Workflow Run ID
  uses: spring-io/spring-security-release-tools/.github/actions/get-workflow-run-id@v1
  with:
    repository: owner/repo
    workflow-id: build-and-deploy.yml
```

## Example Usage

```yaml
name: Download Artifacts

on: push

permissions:
  actions: read

jobs:
  download-artifacts:
    name: Download Artifacts
    runs-on: ubuntu-latest
    steps:
      - id: get-workflow-run-id
        name: Get Workflow Run ID
        uses: spring-io/spring-security-release-tools/.github/actions/get-workflow-run-id@v1
        with:
          repository: spring-projects/spring-security
          workflow-id: continuous-integration-workflow.yml
      - name: Download Artifacts
        uses: actions/download-artifact@v4
        with:
          name: maven-repository
          path: build
          repository: spring-projects/spring-security
          github-token: ${{ github.token }}
          run-id: ${{ steps.get-workflow-run-id.outputs.run-id }}
      - name: Unzip Artifacts
        run: pushd build && unzip maven-repository.zip && popd
```
