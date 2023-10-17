/*
 * Copyright 2019-2023 the original author or authors.
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

import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.gradle.plugin.core.RegularFileUtils;
import io.spring.release.SpringReleases;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import org.springframework.util.Assert;

public abstract class DeleteSaganReleaseTask extends DefaultTask {

	public static final String TASK_NAME = "deleteSaganRelease";

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getProjectName();

	@Input
	@Optional
	public abstract Property<String> getVersion();

	@TaskAction
	public void deleteRelease() {
		var version = getVersion().getOrNull();
		if (version == null) {
			System.out.println("No version provided");
			return;
		}

		var gitHubAccessToken = getGitHubAccessToken().get();
		var projectName = getProjectName().get();
		var springReleases = new SpringReleases(gitHubAccessToken);
		springReleases.deleteRelease(projectName, version);
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, DeleteSaganReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Delete a version for the specified project on spring.io.");
			task.doNotTrackState("API call to api.spring.io needs to check for releases every time");

			// @formatter:off
			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.PREVIOUS_VERSION_PROPERTY)
					.orElse(ProjectUtils.findTaskByType(project, GetPreviousReleaseMilestoneTask.class)
							.getPreviousReleaseMilestoneFile()
							.map(RegularFileUtils::readString));
			// @formatter:on

			var name = springRelease.getRepositoryName().get();
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getProjectName().set(name);
			task.getVersion().set(versionProvider);
		});
	}

}
