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
package io.spring.release.gradle.plugin.release;

import com.github.api.Repository;
import io.spring.release.SpringReleases;
import io.spring.release.gradle.plugin.core.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import org.springframework.util.Assert;

import static io.spring.release.gradle.plugin.core.ProjectUtils.findTaskByType;
import static io.spring.release.gradle.plugin.core.ProjectUtils.getProperty;
import static io.spring.release.gradle.plugin.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;
import static io.spring.release.gradle.plugin.release.SpringReleasePlugin.NEXT_VERSION_PROPERTY;

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
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, ScheduleNextReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription(
					"Schedule the next release (even months only) or release train (series of milestones starting in January or July) based on the current version");
			task.doNotTrackState("API call to GitHub needs to check for new milestones every time");

			// @formatter:off
			var versionProvider = getProperty(project, NEXT_VERSION_PROPERTY)
					.orElse(findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));
			// @formatter:on

			var owner = springRelease.getRepositoryOwner().get();
			var name = project.getRootProject().getName();
			task.getRepository().set(new Repository(owner, name));
			task.getGitHubAccessToken().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getVersion().set(versionProvider);
			task.getWeekOfMonth().set(springRelease.getWeekOfMonth());
			task.getDayOfWeek().set(springRelease.getDayOfWeek());
		});
	}

}
