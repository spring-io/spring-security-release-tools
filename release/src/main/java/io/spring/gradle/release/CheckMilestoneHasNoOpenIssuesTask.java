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
import com.github.api.RepositoryRef;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import org.springframework.util.Assert;

import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.NEXT_VERSION_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class CheckMilestoneHasNoOpenIssuesTask extends DefaultTask {
	public static final String TASK_NAME = "checkMilestoneHasNoOpenIssues";

	@Input
	public abstract Property<RepositoryRef> getRepository();

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getVersion();

	@TaskAction
	public void checkMilestoneHasNoOpenIssues() {
		var gitHubAccessToken = getGitHubAccessToken().get();
		var repository = getRepository().get();
		var version = getVersion().get();

		GitHubApi gitHubApi = new GitHubApi(gitHubAccessToken);
		var milestone = gitHubApi.getMilestone(repository, version);
		var hasOpenIssues = gitHubApi.hasOpenIssues(repository, milestone.number());
		System.out.println(!hasOpenIssues);
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, CheckMilestoneHasNoOpenIssuesTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Checks if there are any open issues for the specified repository and milestone and outputs true or false");
			task.doNotTrackState("API call to GitHub needs to check for open issues every time");

			var owner = springRelease.getRepositoryOwner().get();
			var repo = project.getRootProject().getName();
			task.getRepository().set(new RepositoryRef(owner, repo));
			task.getGitHubAccessToken().set((String) project.findProperty(GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getVersion().set((String) project.findProperty(NEXT_VERSION_PROPERTY));
		});
	}
}
