/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.gradle.plugin.release.workflows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.spring.gradle.plugin.release.SpringReleasePlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link UpdateWorkflowsTask}. The generator is skipped; it is an external
 * program.
 */
class UpdateWorkflowsTaskTests {

	@TempDir
	File projectDir;

	private Project project;

	@BeforeEach
	void setUp() throws IOException {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
		this.project.getPluginManager().apply(SpringReleasePlugin.class);
		write(".github/workflow-generator.yml",
				"workflow:\n  generator:\n    project:\n      java:\n        versions:\n          primary: 25\n");
	}

	@Test
	void updateWhenSpecsThenWorkflowsRenderedAndPinned() throws Exception {
		write(".github/specs/workflows/ci.yml.template", "steps:\n  - uses: actions/checkout\n");
		task().update();
		WorkflowVersions.Action checkout = WorkflowVersions.shipped().action("actions/checkout");
		assertThat(new File(this.projectDir, ".github/workflows/ci.yml"))
			.hasContent("steps:\n  - uses: actions/checkout@" + checkout.sha() + " # " + checkout.ref() + "\n");
	}

	@Test
	void updateWhenDefaultsApplyThenReleaseTrainActionsRenderedAndPinned() throws Exception {
		task().update();
		WorkflowVersions.Action actions = WorkflowVersions.shipped().action("spring-io/spring-security-actions");
		for (String name : new String[] { "build", "test" }) {
			assertThat(new File(this.projectDir, ".github/actions/release-train-" + name + "/action.yml")).content()
				.contains("spring-io/spring-security-actions/run-gradle@" + actions.sha() + " # " + actions.ref())
				.contains("ORG_GRADLE_PROJECT_releaseChannel");
		}
	}

	@Test
	void updateWhenDefaultsApplyThenCodeqlWorkflowRenderedAndPinned() throws Exception {
		task().update();
		WorkflowVersions.Action actions = WorkflowVersions.shipped().action("spring-io/github-actions");
		assertThat(new File(this.projectDir, ".github/workflows/codeql.yml")).content()
			.contains("spring-io/github-actions/.github/workflows/codeql-analysis.yml@" + actions.sha() + " # "
					+ actions.ref())
			.contains("security-events: write");
	}

	@Test
	void updateWhenRunTwiceThenSecondRunProducesTheSameWorkflows() throws Exception {
		write(".github/specs/workflows/ci.yml.template", "steps:\n  - uses: actions/checkout\n");
		task().update();
		File workflow = new File(this.projectDir, ".github/workflows/ci.yml");
		String first = Files.readString(workflow.toPath());
		task().update();
		assertThat(workflow).hasContent(first);
	}

	@Test
	void updateWhenSelfReferenceThenPinnedToThisPluginsOwnBuild() throws Exception {
		write(".github/specs/workflows/ci.yml.template",
				"steps:\n  - uses: spring-io/spring-security-release-tools/x\n");
		task().update();
		WorkflowVersions.Action self = WorkflowVersions.shipped().action("spring-io/spring-security-release-tools");
		assertThat(new File(this.projectDir, ".github/workflows/ci.yml")).content()
			.contains("spring-io/spring-security-release-tools/x@" + self.sha() + " # " + self.ref());
		((VerifyWorkflowsTask) this.project.getTasks().getByName("verifyWorkflows")).verify();
	}

	@Test
	void updateWhenNoWorkflowGeneratorConfigThenDefaultTemplatesNotRendered() throws Exception {
		Files.delete(new File(this.projectDir, ".github/workflow-generator.yml").toPath());
		write(".github/specs/workflows/mine.yml.template", "name: Mine\n");
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/mine.yml")).exists();
		assertThat(new File(this.projectDir, ".github/workflows/pull-request-build.yml")).doesNotExist();
	}

	@Test
	void updateWhenTemplateRendersToNothingThenWorkflowRemoved() throws Exception {
		write(".github/workflows/dependabot-pull-request-automerge.yml", "name: Old\n");
		write(".github/specs/workflows/dependabot-pull-request-automerge.yml.template", "\n");
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-automerge.yml")).doesNotExist();
		task().update();
	}

	@Test
	void updateWhenDependabotMergeNotOptedIntoThenNeitherWorkflowRendered() throws Exception {
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-automerge.yml")).doesNotExist();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-auto-merge-forward.yml")).doesNotExist();
	}

	@Test
	void updateWhenDependabotMergeOptedIntoThenEachWorkflowRenderedIndependently() throws Exception {
		String config = "workflow:\n  generator:\n    project:\n      java:\n        versions:\n          primary: 25\n"
				+ "    workflows:\n      release-tools:\n        dependabot:\n          %s: true\n";
		write(".github/workflow-generator.yml", config.formatted("automerge"));
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-automerge.yml")).content()
			.contains("pull_request:");
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-auto-merge-forward.yml")).doesNotExist();
		write(".github/workflow-generator.yml", config.formatted("automergeforward"));
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-automerge.yml")).doesNotExist();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-auto-merge-forward.yml")).content()
			.contains("push:");
	}

	@Test
	void updateWhenVerifyNotConfiguredThenVerificationIsOn() throws Exception {
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/ci.yml")).content()
			.contains("verify-workflows:")
			.contains("ci-verify-workflows.yml");
		assertThat(new File(this.projectDir, ".github/workflows/pull-request-build.yml")).content()
			.contains("ORG_GRADLE_PROJECT_verifyWorkflows: true");
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-build.yml")).content()
			.contains("ORG_GRADLE_PROJECT_verifyWorkflows: true");
	}

	@Test
	void updateWhenVerifyIsFalseThenVerificationIsOffEverywhere() throws Exception {
		write(".github/workflow-generator.yml", "workflow:\n  generator:\n    project:\n      java:\n"
				+ "        versions:\n          primary: 25\n    workflows:\n      release-tools:\n        verify: false\n");
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/ci.yml")).content()
			.doesNotContain("verify-workflows")
			.contains("deploy-artifacts:");
		assertThat(new File(this.projectDir, ".github/workflows/pull-request-build.yml")).content()
			.doesNotContain("verifyWorkflows");
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-build.yml")).content()
			.doesNotContain("verifyWorkflows");
	}

	@Test
	void updateWhenWritingThenNoStagingFilesLeftBehind() throws Exception {
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows").list()).noneMatch((name) -> name.endsWith(".tmp"));
	}

	@Test
	void updateWhenLocalVersionsOverrideThenLocalUsed() throws Exception {
		write(".github/specs/versions.json",
				"{\"actions\":{\"actions/checkout\":{\"ref\":\"v9\",\"sha\":\"" + "1".repeat(40) + "\"}}}");
		write(".github/specs/workflows/ci.yml.template", "uses: actions/checkout\n");
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/ci.yml"))
			.hasContent("uses: actions/checkout@" + "1".repeat(40) + " # v9\n");
	}

	@Test
	void updateWhenOneSpecCannotBeRenderedThenNoWorkflowIsWritten() throws Exception {
		write(".github/specs/workflows/a.yml.template", "uses: actions/checkout\n");
		write(".github/specs/workflows/b.yml.template", "uses: unknown/repo\n");
		assertThatExceptionOfType(GradleException.class).isThrownBy(() -> task().update())
			.withMessageContaining("b.yml.template");
		assertThat(new File(this.projectDir, ".github/workflows/a.yml")).doesNotExist();
	}

	@Test
	void updateWhenNoSpecsThenDefaultTemplatesRendered() throws Exception {
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-build.yml")).content()
			.contains("JAVA_VERSION: '25'")
			.doesNotContain("workflow.generator");
		assertThat(new File(this.projectDir, ".github/workflows/pull-request-build.yml")).exists();
	}

	@Test
	void updateWhenProjectHasTemplateOfSameNameThenItReplacesTheDefault() throws Exception {
		write(".github/specs/workflows/dependabot-pull-request-build.yml.template", "name: Mine\n");
		task().update();
		assertThat(new File(this.projectDir, ".github/workflows/dependabot-pull-request-build.yml"))
			.hasContent("name: Mine\n");
		assertThat(new File(this.projectDir, ".github/workflows/pull-request-build.yml")).exists();
	}

	private UpdateWorkflowsTask task() {
		UpdateWorkflowsTask task = (UpdateWorkflowsTask) this.project.getTasks().getByName("updateWorkflows");
		task.getSkipGenerator().set(true);
		return task;
	}

	private void write(String path, String content) throws IOException {
		File file = new File(this.projectDir, path);
		Files.createDirectories(file.getParentFile().toPath());
		Files.writeString(file.toPath(), content);
	}

}
