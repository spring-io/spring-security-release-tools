/*
 * Copyright 2002-2026 the original author or authors.
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

package io.spring.gradle.plugin.maven;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringMavenPublishPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringMavenPublishPluginTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).withName("mavenJavaTests").build();
		this.project.getPluginManager().apply("java");
		this.project.getPluginManager().apply(SpringMavenPublishPlugin.class);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName("checkMavenPomLicense");
		assertThat(task).isNotNull().isInstanceOf(CheckMavenPomLicenseTask.class);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenPluginAppliedThenLicenseFileIsRootProjectLicense() {
		var task = (CheckMavenPomLicenseTask) this.project.getTasks().getByName("checkMavenPomLicense");
		var expected = this.project.getRootProject().file("LICENSE.txt");
		assertThat(task.getLicenseFile().get().getAsFile()).isEqualTo(expected);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenPluginAppliedThenDependsOnGeneratePomFileTask() {
		Task checkTask = this.project.getTasks().getByName("checkMavenPomLicense");
		Task generatePomTask = this.project.getTasks().getByName("generatePomFileForMavenJavaPublication");
		Set<Task> dependencies = new HashSet<>(checkTask.getTaskDependencies().getDependencies(checkTask));
		assertThat(dependencies).contains(generatePomTask);
	}

	@Test
	public void checkTaskWhenPluginAppliedThenDependsOnCheckMavenPomLicense() {
		Task checkTask = this.project.getTasks().getByName(LifecycleBasePlugin.CHECK_TASK_NAME);
		Task checkPomLicenseTask = this.project.getTasks().getByName("checkMavenPomLicense");
		Set<Task> dependencies = new HashSet<>(checkTask.getTaskDependencies().getDependencies(checkTask));
		assertThat(dependencies).contains(checkPomLicenseTask);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenJavaGradlePluginPluginAppliedThenNotRegistered() {
		Project otherProject = ProjectBuilder.builder().build();
		otherProject.getPluginManager().apply("java-gradle-plugin");
		otherProject.getPluginManager().apply(SpringMavenPublishPlugin.class);

		assertThat(otherProject.getTasks().findByName("checkMavenPomLicense")).isNull();
	}

}
