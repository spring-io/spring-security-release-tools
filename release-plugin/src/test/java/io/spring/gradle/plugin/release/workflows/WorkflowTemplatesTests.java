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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link WorkflowTemplates}.
 */
class WorkflowTemplatesTests {

	@TempDir
	Path specs;

	@Test
	void loadWhenDefaultsApplyThenReleaseTrainActionsAreIncluded() {
		assertThat(WorkflowTemplates.load(this.specs, true, Fixtures.specs())).containsKeys(
				"actions/release-train-build/action.yml", "actions/release-train-test/action.yml");
	}

	@Test
	void loadWhenProjectTemplateHasSameNameThenReplacesDefault() throws IOException {
		Files.writeString(workflow("dependabot-pull-request-build.spec.yml"), "mine");
		assertThat(
				WorkflowTemplates.load(this.specs, true, Fixtures.specs()).get("workflows/dependabot-pull-request-build.yml").content())
			.isEqualTo("mine");
		assertThat(WorkflowTemplates.load(this.specs, true, Fixtures.specs())).containsKey("workflows/pull-request-build.yml");
	}

	@Test
	void loadWhenProjectTemplateHasNoDefaultThenAdded() throws IOException {
		Files.writeString(workflow("custom.spec.yml"), "custom");
		assertThat(WorkflowTemplates.load(this.specs, true, Fixtures.specs())).containsKeys("workflows/custom.yml",
				"workflows/pull-request-build.yml");
	}

	@Test
	void loadWhenProjectHasActionTemplateThenReplacesDefaultOrIsAdded() throws IOException {
		Path actions = Files.createDirectories(this.specs.resolve("actions/release-train-build"));
		Files.writeString(actions.resolve("action.spec.yml"), "mine");
		Files.writeString(Files.createDirectories(this.specs.resolve("actions/custom")).resolve("action.spec.yml"),
				"custom");
		assertThat(WorkflowTemplates.load(this.specs, true, Fixtures.specs()).get("actions/release-train-build/action.yml").content())
			.isEqualTo("mine");
		assertThat(WorkflowTemplates.load(this.specs, true, Fixtures.specs())).containsKeys("actions/custom/action.yml",
				"actions/release-train-test/action.yml");
	}

	@Test
	void loadWhenDefaultsApplyAndNoneAreGivenThenSaysHowToGetThem() {
		assertThatExceptionOfType(GradleException.class).isThrownBy(() -> WorkflowTemplates.load(this.specs, true, null))
			.withMessageContaining("--templates-version latest");
	}

	private Path workflow(String name) throws IOException {
		return Files.createDirectories(this.specs.resolve("workflows")).resolve(name);
	}

	@Test
	void loadWhenDefaultsDoNotApplyThenOnlyProjectTemplates() throws IOException {
		Files.writeString(workflow("custom.spec.yml"), "custom");
		assertThat(WorkflowTemplates.load(this.specs, false, null)).containsOnlyKeys("workflows/custom.yml");
	}

}
