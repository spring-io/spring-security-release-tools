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
 * Tests for {@link VerifyWorkflowsTask}.
 */
class VerifyWorkflowsTaskTests {

	@TempDir
	File projectDir;

	private Project project;

	@BeforeEach
	void setUp() throws IOException {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
		this.project.getPluginManager().apply(SpringReleasePlugin.class);
		Files.createDirectories(new File(this.projectDir, ".github").toPath());
		Files.writeString(new File(this.projectDir, ".github/workflow-generator.yml").toPath(),
				"workflow:\n  generator:\n    project:\n      java:\n        versions:\n          primary: 25\n");
	}

	@Test
	void verifyWhenDefaultTemplatesRenderedThenSucceeds() throws IOException {
		render();
		task().verify();
	}

	@Test
	void verifyWhenDefaultTemplatesNotRenderedThenFailsNamingTheTemplate() {
		assertThatExceptionOfType(GradleException.class).isThrownBy(() -> task().verify())
			.withMessageContaining("dependabot-pull-request-build.yml is missing")
			.withMessageContaining("the default workflows/dependabot-pull-request-build.yml.template");
	}

	@Test
	void verifyWhenUnpinnedThenFailsNamingTheFile() throws IOException {
		File workflow = new File(this.projectDir, ".github/workflows/ci.yml");
		Files.createDirectories(workflow.getParentFile().toPath());
		Files.writeString(workflow.toPath(), "steps:\n  - uses: actions/checkout@main\n");
		assertThatExceptionOfType(GradleException.class).isThrownBy(() -> task().verify())
			.withMessageContaining("ci.yml:2")
			.withMessageContaining("actions/checkout@main");
	}

	@Test
	void verifyWhenSpecRendersToCheckedInWorkflowThenSucceeds() throws IOException {
		File specs = new File(this.projectDir, ".github/specs/workflows");
		File workflows = new File(this.projectDir, ".github/workflows");
		Files.createDirectories(specs.toPath());
		Files.createDirectories(workflows.toPath());
		String sha = WorkflowVersions.shipped().action("actions/checkout").sha();
		Files.writeString(new File(specs, "ci.yml.template").toPath(), "steps:\n  - uses: actions/checkout\n");
		Files.writeString(new File(workflows, "ci.yml").toPath(), "steps:\n  - uses: actions/checkout@" + sha + " # "
				+ WorkflowVersions.shipped().action("actions/checkout").ref() + "\n");
		render();
		task().verify();
	}

	@Test
	void verifyWhenAntoraUiWorkflowRenderedOnMainThenSucceedsWithNoBranchAndNoNetwork() throws IOException {
		UpdateWorkflowsTask update = (UpdateWorkflowsTask) this.project.getTasks()
			.getByName(UpdateWorkflowsTask.TASK_NAME);
		update.getSkipGenerator().set(true);
		update.getBranch().set("main");
		try (FakeSagan sagan = new FakeSagan()) {
			update.getSaganApiUrl().set(sagan.url());
			update.update();
		}
		assertThat(new File(this.projectDir, ".github/workflows/update-antora-ui-spring.yml")).exists();
		task().verify();
	}

	@Test
	void pluginWhenAppliedThenVerifyWorkflowsRegistered() {
		assertThat(this.project.getTasks().findByName(VerifyWorkflowsTask.TASK_NAME)).isNotNull();
	}

	private void render() throws IOException {
		UpdateWorkflowsTask update = (UpdateWorkflowsTask) this.project.getTasks()
			.getByName(UpdateWorkflowsTask.TASK_NAME);
		update.getSkipGenerator().set(true);
		update.update();
	}

	private VerifyWorkflowsTask task() {
		return (VerifyWorkflowsTask) this.project.getTasks().getByName(VerifyWorkflowsTask.TASK_NAME);
	}

}
