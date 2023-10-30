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

import java.util.Objects;

import com.github.api.Repository;
import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.gradle.plugin.core.RegularFileUtils;
import io.spring.release.SpringReleases;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/**
 * @author Steve Riesenberg
 */
public abstract class ScheduleNextReleaseTask extends DefaultTask {

	public static final String TASK_NAME = "scheduleNextRelease";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<Integer> getWeekOfMonth();

	@Input
	public abstract Property<Integer> getDayOfWeek();

	@TaskAction
	public void scheduleNextRelease() {
		var repository = getRepository().get();
		var gitHubAccessToken = getGitHubAccessToken().get();
		var version = getVersion().get();
		var weekOfMonth = getWeekOfMonth().get();
		var dayOfWeek = getDayOfWeek().get();

		var springReleases = new SpringReleases(gitHubAccessToken);
		springReleases.scheduleReleaseIfNotExists(repository.owner(), repository.name(), version, weekOfMonth,
				dayOfWeek);
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Objects.requireNonNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, ScheduleNextReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription(
					"Schedule the next release (even months only) or release train (series of milestones starting in January or July) based on the current version");
			task.doNotTrackState("API call to GitHub needs to check for new milestones every time");

			// @formatter:off
			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.NEXT_VERSION_PROPERTY)
					.orElse(ProjectUtils.findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));
			// @formatter:on

			var owner = springRelease.getRepositoryOwner().get();
			var name = springRelease.getRepositoryName().get();
			task.getRepository().set(new Repository(owner, name));
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getVersion().set(versionProvider);
			task.getWeekOfMonth().set(springRelease.getWeekOfMonth());
			task.getDayOfWeek().set(springRelease.getDayOfWeek());
		});
	}

}
