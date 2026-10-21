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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkflowTemplates}.
 */
class WorkflowTemplatesTests {

	@TempDir
	Path specs;

	@Test
	void shippedWhenLoadedThenEveryTemplateInTheResourcesDirectoryIsIncluded() throws IOException {
		Path resources = Path.of("src/main/resources/io/spring/gradle/plugin/release/workflows");
		try (var files = Files.list(resources)) {
			List<String> expected = files.map((file) -> file.getFileName().toString())
				.filter((name) -> name.endsWith(".template"))
				.map((name) -> name.substring(0, name.length() - ".template".length()))
				.toList();
			assertThat(WorkflowTemplates.shipped().keySet()).containsExactlyInAnyOrderElementsOf(expected);
		}
	}

	@Test
	void loadWhenProjectTemplateHasSameNameThenReplacesDefault() throws IOException {
		Files.writeString(this.specs.resolve("dependabot-pull-request-build.yml.template"), "mine");
		assertThat(WorkflowTemplates.load(this.specs, true).get("dependabot-pull-request-build.yml").content())
			.isEqualTo("mine");
		assertThat(WorkflowTemplates.load(this.specs, true)).containsKey("pull-request-build.yml");
	}

	@Test
	void loadWhenProjectTemplateHasNoDefaultThenAdded() throws IOException {
		Files.writeString(this.specs.resolve("custom.yml.template"), "custom");
		assertThat(WorkflowTemplates.load(this.specs, true)).containsKeys("custom.yml", "pull-request-build.yml");
	}

	@Test
	void loadWhenDefaultsDoNotApplyThenOnlyProjectTemplates() throws IOException {
		Files.writeString(this.specs.resolve("custom.yml.template"), "custom");
		assertThat(WorkflowTemplates.load(this.specs, false)).containsOnlyKeys("custom.yml");
	}

}
