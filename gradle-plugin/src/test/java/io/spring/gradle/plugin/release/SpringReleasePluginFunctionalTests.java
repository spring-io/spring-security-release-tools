/*
 * Copyright 2020-2023 the original author or authors.
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

import java.io.File;

import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.gradle.plugin.core.RegularFileUtils;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Steve Riesenberg
 */
public class SpringReleasePluginFunctionalTests {

	private static final String REPOSITORY_OWNER = "spring-projects";

	private static final String REPOSITORY_NAME = "spring-security";

	@TempDir
	private File projectDir;

	private Project project;

	private SpringReleasePluginExtension springRelease;

	@BeforeEach
	public void setUp() {
		// @formatter:off
		this.project = ProjectBuilder.builder()
			.withProjectDir(this.projectDir)
			.withName(REPOSITORY_NAME)
			.build();
		// @formatter:on
		this.project.setVersion("1.0.0-SNAPSHOT");
		this.project.getPluginManager().apply(SpringReleasePlugin.class);

		this.springRelease = this.project.getExtensions().getByType(SpringReleasePluginExtension.class);
		this.springRelease.getRepositoryOwner().set(REPOSITORY_OWNER);
	}

	@Test
	public void getNextReleaseMilestoneTaskWhenPluginAppliedThenConfigured() {
		var task = ProjectUtils.findTaskByType(this.project, GetNextReleaseMilestoneTask.class);
		assertThat(task.getVersion().get()).isEqualTo(this.project.getVersion());

		var repository = task.getRepository().get();
		assertThat(repository.owner()).isEqualTo(REPOSITORY_OWNER);
		assertThat(repository.name()).isEqualTo(REPOSITORY_NAME);

		var outputFile = task.getNextReleaseMilestoneFile().get();
		assertThat(outputFile.getAsFile().getName()).isEqualTo(GetNextReleaseMilestoneTask.OUTPUT_VERSION_PATH);
	}

	@Test
	public void getNextReleaseMilestoneTaskWhenConfiguredThenSuccess() {
		RegularFileUtils.mkdirs(this.project.getLayout().getBuildDirectory());

		var task = ProjectUtils.findTaskByType(this.project, GetNextReleaseMilestoneTask.class);
		task.getNextReleaseMilestone();

		var nextReleaseMilestone = RegularFileUtils.readString(task.getNextReleaseMilestoneFile().get());
		assertThat(nextReleaseMilestone).isEqualTo("1.0.0");
	}

	@Test
	public void getNextReleaseMilestoneTaskWhenNameIsSetThenOverridden() {
		this.springRelease.getRepositoryName().set("my-project");

		var task = ProjectUtils.findTaskByType(this.project, GetNextReleaseMilestoneTask.class);
		assertThat(task.getVersion().get()).isEqualTo("1.0.0-SNAPSHOT");

		var repository = task.getRepository().get();
		assertThat(repository.owner()).isEqualTo(REPOSITORY_OWNER);
		assertThat(repository.name()).isEqualTo("my-project");
	}

	@Test
	public void getNextSnapshotVersionTaskWhenPluginAppliedThenConfigured() {
		var task = ProjectUtils.findTaskByType(this.project, GetNextSnapshotVersionTask.class);
		assertThat(task.getVersion().get()).isEqualTo(this.project.getVersion());

		var outputFile = task.getNextSnapshotVersionFile().get();
		assertThat(outputFile.getAsFile().getName()).isEqualTo(GetNextSnapshotVersionTask.OUTPUT_VERSION_PATH);
	}

	@Test
	public void getNextSnapshotVersionTaskWhenConfiguredThenSuccess() {
		this.project.setVersion("1.0.0");
		RegularFileUtils.mkdirs(this.project.getLayout().getBuildDirectory());

		var task = ProjectUtils.findTaskByType(this.project, GetNextSnapshotVersionTask.class);
		task.getNextSnapshotVersion();

		var outputFile = task.getNextSnapshotVersionFile().get();
		var nextSnapshotVersion = RegularFileUtils.readString(outputFile);
		assertThat(nextSnapshotVersion).isEqualTo("1.0.1-SNAPSHOT");
	}

	@Test
	public void checkMilestoneHasNoOpenIssuesTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(CheckMilestoneHasNoOpenIssuesTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void checkMilestoneIsDueTodayTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(CheckMilestoneIsDueTodayTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void createGitHubReleaseTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(CreateGitHubReleaseTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void createReleaseTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(CreateReleaseTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void createSaganReleaseTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(CreateSaganReleaseTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void deleteSaganReleaseTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(DeleteSaganReleaseTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void generateChangelogTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(GenerateChangelogTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void getGitHubUserNameTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(GetGitHubUserNameTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void getNextReleaseMilestoneTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(GetNextReleaseMilestoneTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void getNextSnapshotVersionTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(GetNextSnapshotVersionTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void getPreviousReleaseMilestoneTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(GetPreviousReleaseMilestoneTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void scheduleNextReleaseTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName(ScheduleNextReleaseTask.TASK_NAME);
		assertThat(task).isNotNull();
	}

	@Test
	public void springReleasePluginExtensionWhenPluginAppliedThenExists() {
		var springRelease = this.project.getExtensions().findByType(SpringReleasePluginExtension.class);
		assertThat(springRelease).isNotNull();
	}

}
