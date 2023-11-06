/*
 * Copyright 2002-2022 the original author or authors.
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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/**
 * @author Steve Riesenberg
 */
public abstract class CheckMilestoneHasNoOpenIssuesTask extends DefaultTask {

	public static final String TASK_NAME = "checkMilestoneHasNoOpenIssues";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getVersion();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@TaskAction
	public void checkMilestoneHasNoOpenIssues() {
		var gitHubAccessToken = getGitHubAccessToken().getOrNull();
		var repository = getRepository().get();
		var version = getVersion().get();

		var springReleases = new SpringReleases(gitHubAccessToken);
		var hasOpenIssues = springReleases.hasNoOpenIssues(repository.owner(), repository.name(), version);
		System.out.println(!hasOpenIssues);
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Objects.requireNonNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, CheckMilestoneHasNoOpenIssuesTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription(
					"Checks if there are any open issues for the specified repository and milestone and outputs true or false");
			task.doNotTrackState("API call to GitHub needs to check for open issues every time");

			// @formatter:off
			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.NEXT_VERSION_PROPERTY)
					.orElse(ProjectUtils.findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));
			// @formatter:on

			var owner = springRelease.getRepositoryOwner().get();
			var name = springRelease.getRepositoryName().get();
			task.getRepository().set(new Repository(owner, name));
			task.getVersion().set(versionProvider);
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
		});
	}

}
