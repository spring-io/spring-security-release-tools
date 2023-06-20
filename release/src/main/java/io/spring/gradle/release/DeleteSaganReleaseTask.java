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
package io.spring.gradle.release;

import io.spring.api.SaganApi;
import io.spring.gradle.core.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import static io.spring.gradle.core.ProjectUtils.findTaskByType;
import static io.spring.gradle.core.ProjectUtils.getProperty;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_USER_NAME_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.PREVIOUS_VERSION_PROPERTY;

public abstract class DeleteSaganReleaseTask extends DefaultTask {
	public static final String TASK_NAME = "deleteSaganRelease";

	@Input
	public abstract Property<String> getUsername();

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<String> getProjectName();

	@TaskAction
	public void createRelease() {
		String username = getUsername().get();
		SaganApi sagan = new SaganApi(username, getGitHubAccessToken().get());
		sagan.deleteRelease(getProjectName().get(), getVersion().get());
	}

	public static TaskProvider<DeleteSaganReleaseTask> register(Project project) {
		return project.getTasks().register(TASK_NAME, DeleteSaganReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Delete a version for the specified project on spring.io.");

			var usernameProvider = getProperty(project, GITHUB_USER_NAME_PROPERTY)
					.orElse(findTaskByType(project, GetGitHubUserNameTask.class)
							.getUsernameFile()
							.map(RegularFileUtils::readString));

			task.getUsername().set(usernameProvider);
			task.getGitHubAccessToken().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getProjectName().set(project.getRootProject().getName());
			task.getVersion().set(getProperty(project, PREVIOUS_VERSION_PROPERTY));
		});
	}
}
