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

package io.spring.gradle.plugin.release;

import io.spring.gradle.plugin.release.workflows.VerifyWorkflowsTask;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for making {@code check} verify workflows with {@code -PverifyWorkflows}.
 */
class VerifyOnCheckTests {

	@ParameterizedTest
	@ValueSource(strings = { "true", "TRUE", "1", "" })
	void verifyWorkflowsRequestedWhenSetToAnythingButFalseThenTrue(String value) {
		assertThat(SpringReleasePlugin.verifyWorkflowsRequested(value)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "false", "False", " false " })
	void verifyWorkflowsRequestedWhenFalseThenFalse(String value) {
		assertThat(SpringReleasePlugin.verifyWorkflowsRequested(value)).isFalse();
	}

	@Test
	void verifyWorkflowsRequestedWhenAbsentThenFalse() {
		assertThat(SpringReleasePlugin.verifyWorkflowsRequested(null)).isFalse();
	}

	@Test
	void applyWhenPropertyNotSetThenCheckDoesNotDependOnVerifyWorkflows() {
		Project project = project();
		assertThat(dependsOnVerify(project)).isFalse();
	}

	@Test
	void verifyOnCheckWhenCheckExistsThenItDependsOnVerifyWorkflows() {
		Project project = project();
		SpringReleasePlugin.verifyOnCheck(project);
		assertThat(dependsOnVerify(project)).isTrue();
	}

	private static Project project() {
		Project project = ProjectBuilder.builder().build();
		project.getPluginManager().apply("base");
		project.getPluginManager().apply(SpringReleasePlugin.class);
		return project;
	}

	private static boolean dependsOnVerify(Project project) {
		var check = project.getTasks().getByName("check");
		return check.getTaskDependencies()
			.getDependencies(check)
			.contains(project.getTasks().getByName(VerifyWorkflowsTask.TASK_NAME));
	}

}
