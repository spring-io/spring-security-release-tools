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

package io.spring.gradle.plugin.release;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.Function;

import com.github.api.Repository;
import groovy.lang.MissingPropertyException;
import io.spring.gradle.plugin.core.ProjectUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests how the release tasks derive their inputs from Gradle properties and the
 * {@code springRelease} extension. The tasks call {@code SpringReleases} directly, so
 * what they hand to the GitHub and Sagan APIs is verified through the task inputs, plus
 * the dry-run output of {@link CreateReleaseTask}.
 *
 * @author Josh Cummings
 */
class ReleaseTasksBehaviorTests {

	private static final String ROOT_NAME = "root-name";

	@TempDir
	private File projectDir;

	private Project project(Map<String, Object> properties) {
		var project = ProjectBuilder.builder().withProjectDir(this.projectDir).withName(ROOT_NAME).build();
		project.setVersion("1.0.0-SNAPSHOT");
		properties.forEach((name, value) -> project.getExtensions().getExtraProperties().set(name, value));
		project.getPluginManager().apply(SpringReleasePlugin.class);
		return project;
	}

	private static SpringReleasePluginExtension springRelease(Project project) {
		return project.getExtensions().getByType(SpringReleasePluginExtension.class);
	}

	private static <T extends Task> T task(Project project, Class<T> type) {
		return ProjectUtils.findTaskByType(project, type);
	}

	private record RepositoryTask(Class<? extends Task> type, Function<Task, Property<Repository>> repository) {

		@SuppressWarnings("unchecked")
		static <T extends Task> RepositoryTask of(Class<T> type, Function<T, Property<Repository>> repository) {
			return new RepositoryTask(type, (task) -> repository.apply((T) task));
		}

		@Override
		public String toString() {
			return this.type.getSimpleName();
		}
	}

	static RepositoryTask[] repositoryTasks() {
		return new RepositoryTask[] { RepositoryTask.of(CreateReleaseTask.class, CreateReleaseTask::getRepository),
				RepositoryTask.of(CreateGitHubReleaseTask.class, CreateGitHubReleaseTask::getRepository),
				RepositoryTask.of(CreateSaganReleaseTask.class, CreateSaganReleaseTask::getRepository),
				RepositoryTask.of(CloseMilestoneTask.class, CloseMilestoneTask::getRepository),
				RepositoryTask.of(ScheduleNextReleaseTask.class, ScheduleNextReleaseTask::getRepository),
				RepositoryTask.of(GetNextReleaseMilestoneTask.class, GetNextReleaseMilestoneTask::getRepository),
				RepositoryTask.of(CheckMilestoneHasOpenIssuesTask.class,
						CheckMilestoneHasOpenIssuesTask::getRepository),
				RepositoryTask.of(CheckMilestoneIsDueTodayTask.class, CheckMilestoneIsDueTodayTask::getRepository),
				RepositoryTask.of(CheckBranchHasOssSupportTask.class, CheckBranchHasOssSupportTask::getRepository),
				RepositoryTask.of(CheckBranchHasCommercialSupportTask.class,
						CheckBranchHasCommercialSupportTask::getRepository) };
	}

	@ParameterizedTest
	@MethodSource("repositoryTasks")
	void repositoryWhenExtensionNotConfiguredThenUsesDefaultOwnerAndRootProjectName(RepositoryTask repositoryTask) {
		var project = project(Map.of());
		var repository = repositoryTask.repository().apply(task(project, repositoryTask.type())).get();
		assertThat(repository).isEqualTo(new Repository("spring-projects", ROOT_NAME));
	}

	@ParameterizedTest
	@MethodSource("repositoryTasks")
	void repositoryWhenExtensionConfiguredThenUsesExtension(RepositoryTask repositoryTask) {
		var project = project(Map.of());
		springRelease(project).getRepositoryOwner().set("my-org");
		springRelease(project).getRepositoryName().set("my-repo");
		var repository = repositoryTask.repository().apply(task(project, repositoryTask.type())).get();
		assertThat(repository).isEqualTo(new Repository("my-org", "my-repo"));
	}

	@Test
	void projectNameWhenExtensionConfiguredThenUsesExtensionName() {
		var project = project(Map.of());
		springRelease(project).getRepositoryName().set("my-repo");
		assertThat(task(project, DeleteSaganReleaseTask.class).getProjectName().get()).isEqualTo("my-repo");
		assertThat(task(project, GetPreviousReleaseMilestoneTask.class).getProjectName().get()).isEqualTo("my-repo");
	}

	@Test
	void projectNameWhenExtensionNotConfiguredThenUsesRootProjectName() {
		var project = project(Map.of());
		assertThat(task(project, DeleteSaganReleaseTask.class).getProjectName().get()).isEqualTo(ROOT_NAME);
		assertThat(task(project, GetPreviousReleaseMilestoneTask.class).getProjectName().get()).isEqualTo(ROOT_NAME);
	}

	@Test
	void nextVersionWhenPropertySetThenOverridesTasks() {
		var project = project(Map.of("nextVersion", "2.3.4"));
		assertThat(task(project, CreateReleaseTask.class).getVersion().get()).isEqualTo("2.3.4");
		assertThat(task(project, CreateGitHubReleaseTask.class).getVersion().get()).isEqualTo("2.3.4");
		assertThat(task(project, CreateSaganReleaseTask.class).getVersion().get()).isEqualTo("2.3.4");
		assertThat(task(project, CloseMilestoneTask.class).getVersion().get()).isEqualTo("2.3.4");
		assertThat(task(project, ScheduleNextReleaseTask.class).getVersion().get()).isEqualTo("2.3.4");
		assertThat(task(project, CheckMilestoneHasOpenIssuesTask.class).getVersion().get()).isEqualTo("2.3.4");
		assertThat(task(project, CheckMilestoneIsDueTodayTask.class).getVersion().get()).isEqualTo("2.3.4");
		assertThat(task(project, GenerateChangelogTask.class).getVersion().get()).isEqualTo("2.3.4");
	}

	@Test
	void nextVersionWhenNotSetThenCreateSaganReleaseUsesProjectVersion() {
		var project = project(Map.of());
		assertThat(task(project, CreateSaganReleaseTask.class).getVersion().get()).isEqualTo("1.0.0-SNAPSHOT");
	}

	@Test
	void currentVersionWhenPropertySetThenOverridesTasks() {
		var project = project(Map.of("currentVersion", "3.4.5"));
		assertThat(task(project, GetNextReleaseMilestoneTask.class).getVersion().get()).isEqualTo("3.4.5");
		assertThat(task(project, GetNextSnapshotVersionTask.class).getVersion().get()).isEqualTo("3.4.5");
		assertThat(task(project, GetPreviousReleaseMilestoneTask.class).getVersion().get()).isEqualTo("3.4.5");
	}

	@Test
	void currentVersionWhenNotSetThenTasksUseProjectVersion() {
		var project = project(Map.of());
		assertThat(task(project, GetNextReleaseMilestoneTask.class).getVersion().get()).isEqualTo("1.0.0-SNAPSHOT");
		assertThat(task(project, GetNextSnapshotVersionTask.class).getVersion().get()).isEqualTo("1.0.0-SNAPSHOT");
		assertThat(task(project, GetPreviousReleaseMilestoneTask.class).getVersion().get()).isEqualTo("1.0.0-SNAPSHOT");
	}

	@Test
	void previousVersionWhenPropertySetThenOverridesDeleteSaganRelease() {
		var project = project(Map.of("previousVersion", "0.9.0"));
		assertThat(task(project, DeleteSaganReleaseTask.class).getVersion().get()).isEqualTo("0.9.0");
	}

	@Test
	void gitHubAccessTokenWhenPropertySetThenPassedToTasks() {
		var project = project(Map.of("gitHubAccessToken", "secret"));
		assertThat(task(project, CreateReleaseTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, CreateGitHubReleaseTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, CreateSaganReleaseTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, DeleteSaganReleaseTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, CloseMilestoneTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, ScheduleNextReleaseTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, GetGitHubUserNameTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, GenerateChangelogTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, GetNextReleaseMilestoneTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, GetPreviousReleaseMilestoneTask.class).getGitHubAccessToken().get())
			.isEqualTo("secret");
		assertThat(task(project, CheckMilestoneHasOpenIssuesTask.class).getGitHubAccessToken().get())
			.isEqualTo("secret");
		assertThat(task(project, CheckMilestoneIsDueTodayTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, CheckBranchHasOssSupportTask.class).getGitHubAccessToken().get()).isEqualTo("secret");
		assertThat(task(project, CheckBranchHasCommercialSupportTask.class).getGitHubAccessToken().get())
			.isEqualTo("secret");
	}

	@Test
	void gitHubAccessTokenWhenNotSetThenAbsent() {
		var project = project(Map.of());
		assertThat(task(project, CreateReleaseTask.class).getGitHubAccessToken().isPresent()).isFalse();
		assertThat(task(project, CloseMilestoneTask.class).getGitHubAccessToken().isPresent()).isFalse();
	}

	@Test
	void branchWhenPropertySetThenOverridesTasks() {
		var project = project(Map.of("branch", "6.5.x"));
		assertThat(task(project, CreateReleaseTask.class).getBranch().get()).isEqualTo("6.5.x");
		assertThat(task(project, CreateGitHubReleaseTask.class).getBranch().get()).isEqualTo("6.5.x");
		assertThat(task(project, CheckBranchHasOssSupportTask.class).getBranch().get()).isEqualTo("6.5.x");
		assertThat(task(project, CheckBranchHasCommercialSupportTask.class).getBranch().get()).isEqualTo("6.5.x");
	}

	@Test
	void branchWhenNotSetThenReleaseTasksDefaultToMainAndCheckTasksAreAbsent() {
		var project = project(Map.of());
		assertThat(task(project, CreateReleaseTask.class).getBranch().get()).isEqualTo("main");
		assertThat(task(project, CreateGitHubReleaseTask.class).getBranch().get()).isEqualTo("main");
		assertThat(task(project, CheckBranchHasOssSupportTask.class).getBranch().isPresent()).isFalse();
		assertThat(task(project, CheckBranchHasCommercialSupportTask.class).getBranch().isPresent()).isFalse();
	}

	@Test
	void createReleaseWhenExtensionConfiguredThenUrlsAndFlagsComeFromIt() {
		var project = project(Map.of());
		springRelease(project).getReferenceDocUrl().set("https://docs/{version}/ref");
		springRelease(project).getApiDocUrl().set("https://docs/api");
		springRelease(project).getReplaceVersionInReferenceDocUrl().set(true);
		var task = task(project, CreateReleaseTask.class);
		var saganTask = task(project, CreateSaganReleaseTask.class);
		assertThat(task.getReferenceDocUrl().get()).isEqualTo("https://docs/{version}/ref");
		assertThat(task.getApiDocUrl().get()).isEqualTo("https://docs/api");
		assertThat(task.getReplaceVersionInReferenceDocUrl().get()).isTrue();
		assertThat(saganTask.getReferenceDocUrl().get()).isEqualTo("https://docs/{version}/ref");
		assertThat(saganTask.getApiDocUrl().get()).isEqualTo("https://docs/api");
		assertThat(saganTask.getReplaceVersionInReferenceDocUrl().get()).isTrue();
	}

	@Test
	void createReleaseWhenDryRunThenPrintsResolvedReferenceDocUrlAndRepository() throws Exception {
		var project = project(Map.of("nextVersion", "6.5.1"));
		springRelease(project).getRepositoryOwner().set("my-org");
		springRelease(project).getRepositoryName().set("my-repo");
		springRelease(project).getReferenceDocUrl().set("https://docs/{version}/ref");
		springRelease(project).getApiDocUrl().set("https://docs/api");
		springRelease(project).getReplaceVersionInReferenceDocUrl().set(true);
		var task = task(project, CreateReleaseTask.class);
		task.getReleaseNotes().set("notes");

		var output = capture(task::createRelease);

		assertThat(output).contains("[DRY RUN] Creating release for my-org/my-repo@6.5.1");
		assertThat(output).contains("referenceDocUrl=https://docs/6.5/ref");
		assertThat(output).contains("apiDocUrl=https://docs/api");
	}

	@Test
	void createReleaseWhenNotReplacingThenPrintsPlaceholder() throws Exception {
		var project = project(Map.of("nextVersion", "6.5.1"));
		springRelease(project).getReferenceDocUrl().set("https://docs/{version}/ref");
		springRelease(project).getApiDocUrl().set("https://docs/api");
		var task = task(project, CreateReleaseTask.class);
		task.getReleaseNotes().set("notes");

		assertThat(capture(task::createRelease)).contains("referenceDocUrl=https://docs/{version}/ref");
	}

	@Test
	void createReleaseWhenCreatingWithoutAccessTokenThenFails() {
		var project = project(Map.of("nextVersion", "6.5.1", "createRelease", "true"));
		springRelease(project).getReferenceDocUrl().set("https://docs/ref");
		springRelease(project).getApiDocUrl().set("https://docs/api");
		var task = task(project, CreateReleaseTask.class);
		task.getReleaseNotes().set("notes");

		assertThat(task.getCreateRelease().get()).isTrue();
		assertThatExceptionOfType(MissingPropertyException.class).isThrownBy(task::createRelease)
			.withMessageContaining("-PgitHubAccessToken");
	}

	@Test
	void createReleaseWhenPropertyNotSetThenDryRun() {
		var project = project(Map.of());
		assertThat(task(project, CreateReleaseTask.class).getCreateRelease().get()).isFalse();
		assertThat(task(project, CreateGitHubReleaseTask.class).getCreateRelease().get()).isFalse();
	}

	@Test
	void createGitHubReleaseWhenPrefixConfiguredThenUsesIt() {
		var project = project(Map.of());
		springRelease(project).getReleaseVersionPrefix().set("v");
		assertThat(task(project, CreateGitHubReleaseTask.class).getVersionPrefix().get()).isEqualTo("v");
	}

	@Test
	void scheduleNextReleaseWhenExtensionConfiguredThenUsesSchedule() {
		var project = project(Map.of());
		springRelease(project).getWeekOfMonth().set(3);
		springRelease(project).getDayOfWeek().set(4);
		var task = task(project, ScheduleNextReleaseTask.class);
		assertThat(task.getWeekOfMonth().get()).isEqualTo(3);
		assertThat(task.getDayOfWeek().get()).isEqualTo(4);
	}

	private static String capture(Runnable runnable) {
		var original = System.out;
		var bytes = new ByteArrayOutputStream();
		try (var out = new PrintStream(bytes, true)) {
			System.setOut(out);
			runnable.run();
		}
		finally {
			System.setOut(original);
		}
		return bytes.toString();
	}

}
