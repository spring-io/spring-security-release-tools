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

import com.github.api.GitHubApi;
import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.gradle.plugin.core.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * @author Steve Riesenberg
 */
public abstract class GetGitHubUserNameTask extends DefaultTask {

	public static final String TASK_NAME = "getGitHubUserName";

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@OutputFile
	public abstract RegularFileProperty getUsernameFile();

	@TaskAction
	public void getGitHubUsername() {
		var gitHubAccessToken = getGitHubAccessToken().get();

		var github = new GitHubApi(gitHubAccessToken);
		var user = github.getUser();
		if (user == null) {
			throw new IllegalStateException(
					"Unable to retrieve GitHub username. Please check the personal access token and try again.");
		}

		RegularFileUtils.writeString(getUsernameFile().get(), user.login());
		System.out.println(user.login());
	}

	public static void register(Project project) {
		project.getTasks().register(TASK_NAME, GetGitHubUserNameTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Use gitHubAccessToken to automatically set username property.");
			task.getGitHubAccessToken().set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getUsernameFile().set(project.getLayout().getBuildDirectory().file("github-username.txt"));
		});
	}

}
