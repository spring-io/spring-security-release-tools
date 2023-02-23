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
package io.spring.gradle.release;

import io.spring.gradle.core.RegularFileUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * @author Steve Riesenberg
 */
public class SpringReleasePlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		SpringReleasePluginExtension release =
				project.getExtensions().create("springRelease", SpringReleasePluginExtension.class);

		Provider<String> usernameProvider = createUsernameProvider(project);
		registerCreateSaganReleaseTask(project, usernameProvider, release);
		registerDeleteSaganReleaseTask(project, usernameProvider);
	}

	private Provider<String> createUsernameProvider(Project project) {
		TaskProvider<GetGitHubUsernameTask> usernameTaskProvider =
				project.getTasks().register("getGitHubUsername", GetGitHubUsernameTask.class, (task) -> {
					task.setGroup("Release");
					task.setDescription("Use gitHubAccessToken to automatically set username property.");
					task.getGitHubAccessToken().set((String) project.findProperty("gitHubAccessToken"));
					task.getUsernameFile().set(project.getLayout().getBuildDirectory().file("github-username.txt"));
				});

		return usernameTaskProvider
				.flatMap(GetGitHubUsernameTask::getUsernameFile)
				.map(RegularFileUtils::readString);
	}

	private void registerCreateSaganReleaseTask(Project project, Provider<String> usernameProvider,
			SpringReleasePluginExtension release) {
		project.getTasks().register("createSaganRelease", CreateSaganReleaseTask.class, (task) -> {
			task.setGroup("Release");
			task.setDescription("Creates a new version for the specified project on spring.io");
			task.getUsername().set(usernameProvider);
			task.getGitHubAccessToken().set((String) project.findProperty("gitHubAccessToken"));
			task.getProjectName().set(project.getRootProject().getName());
			task.getVersion().set((String) project.findProperty("nextVersion"));
			task.getReferenceDocUrl().set(release.getReferenceDocUrl());
			task.getApiDocUrl().set(release.getApiDocUrl());
		});
	}

	private void registerDeleteSaganReleaseTask(Project project, Provider<String> usernameProvider) {
		project.getTasks().register("deleteSaganRelease", DeleteSaganReleaseTask.class, (task) -> {
			task.setGroup("Release");
			task.setDescription("Delete a version for the specified project on spring.io");
			task.getUsername().set(usernameProvider);
			task.getGitHubAccessToken().set((String) project.findProperty("gitHubAccessToken"));
			task.getProjectName().set(project.getRootProject().getName());
			task.getVersion().set((String) project.findProperty("previousVersion"));
		});
	}
}
