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

package io.spring.gradle.release;

import com.github.api.GitHubApi;
import com.github.api.Repository;
import io.spring.gradle.core.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import org.springframework.util.Assert;

import static io.spring.gradle.core.ProjectUtils.findTaskByType;
import static io.spring.gradle.core.ProjectUtils.getProperty;
import static io.spring.gradle.release.SpringReleasePlugin.CURRENT_VERSION_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class CheckMilestoneHasNoOpenIssuesTask extends DefaultTask {
	public static final String TASK_NAME = "checkMilestoneHasNoOpenIssues";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getCurrentVersion();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@TaskAction
	public void checkMilestoneHasNoOpenIssues() {
		var gitHubAccessToken = getGitHubAccessToken().get();
		var repository = getRepository().get();
		var currentVersion = getCurrentVersion().get();

		GitHubApi gitHubApi = new GitHubApi(gitHubAccessToken);
		var milestone = gitHubApi.getMilestone(repository, currentVersion);
		var hasOpenIssues = gitHubApi.hasOpenIssues(repository, milestone.number());
		System.out.println(!hasOpenIssues);
	}

	public static TaskProvider<CheckMilestoneHasNoOpenIssuesTask> register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		return project.getTasks().register(TASK_NAME, CheckMilestoneHasNoOpenIssuesTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Checks if there are any open issues for the specified repository and milestone and outputs true or false");
			task.doNotTrackState("API call to GitHub needs to check for open issues every time");

			var versionProvider = getProperty(project, CURRENT_VERSION_PROPERTY)
					.orElse(findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));

			var owner = springRelease.getRepositoryOwner().get();
			var name = project.getRootProject().getName();
			task.getRepository().set(new Repository(owner, name));
			task.getCurrentVersion().set(versionProvider);
			task.getGitHubAccessToken().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
		});
	}
}
