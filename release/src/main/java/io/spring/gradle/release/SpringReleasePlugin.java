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
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * @author Steve Riesenberg
 */
public class SpringReleasePlugin implements Plugin<Project> {
	private static final String TASK_GROUP = "Release";
	private static final String EXTENSION_NAME = "springRelease";

	private static final String GENERATE_CHANGELOG_CONFIGURATION = "changelogGenerator";
	private static final String GENERATE_CHANGELOG_PATH = "changelog/release-notes.md";
	private static final String GENERATE_CHANGELOG_DEPENDENCY = "spring-io:github-changelog-generator:0.0.8";
	private static final String GENERATE_CHANGELOG_REPO_LAYOUT = "[organization]/[artifact]/releases/download/v[revision]/[artifact].[ext]";
	private static final String GENERATE_CHANGELOG_REPO_URL = "https://github.com/";
	private static final String GENERATE_CHANGELOG_GROUP = "spring-io";

	private static final String GENERATE_CHANGELOG_TASK = "generateChangelog";
	private static final String GET_GITHUB_USERNAME_TASK = "getGitHubUsername";
	private static final String CREATE_SAGAN_RELEASE_TASK = "createSaganRelease";
	private static final String DELETE_SAGAN_RELEASE_TASK = "deleteSaganRelease";

	private static final String GITHUB_ACCESS_TOKEN_PROPERTY = "gitHubAccessToken";
	private static final String NEXT_VERSION_PROPERTY = "nextVersion";
	private static final String PREVIOUS_VERSION_PROPERTY = "previousVersion";

	@Override
	public void apply(Project project) {
		SpringReleasePluginExtension release =
				project.getExtensions().create(EXTENSION_NAME, SpringReleasePluginExtension.class);
		release.getReplaceSnapshotVersionInReferenceDocUrl().convention(true);

		registerGenerateChangelogTask(project);
		Provider<String> usernameProvider = createUsernameProvider(project);
		registerCreateSaganReleaseTask(project, usernameProvider, release);
		registerDeleteSaganReleaseTask(project, usernameProvider);
	}

	private void registerGenerateChangelogTask(Project project) {
		createGenerateChangelogConfiguration(project);
		createGenerateChangelogRepository(project);
		project.getTasks().register(GENERATE_CHANGELOG_TASK, GenerateChangelogTask.class, (task) -> {
			task.setGroup(TASK_GROUP);
			task.setDescription("Generate the release notes (changelog) for a milestone.");
			task.setWorkingDir(project.getRootDir());
			task.classpath(project.getConfigurations().getAt(GENERATE_CHANGELOG_CONFIGURATION));
			task.getVersion().set((String) project.findProperty(NEXT_VERSION_PROPERTY));
			task.getReleaseNotes().set(project.getLayout().getBuildDirectory().file(GENERATE_CHANGELOG_PATH));
		});
	}

	private void createGenerateChangelogConfiguration(Project project) {
		project.getConfigurations().create(GENERATE_CHANGELOG_CONFIGURATION, (configuration) ->
				configuration.defaultDependencies((dependencies) ->
						dependencies.add(project.getDependencies().create(GENERATE_CHANGELOG_DEPENDENCY))));
	}

	private void createGenerateChangelogRepository(Project project) {
		IvyArtifactRepository repository = project.getRepositories().ivy((repo) -> {
			repo.setUrl(GENERATE_CHANGELOG_REPO_URL);
			repo.patternLayout((layout) -> layout.artifact(GENERATE_CHANGELOG_REPO_LAYOUT));
			repo.getMetadataSources().artifact();
		});
		project.getRepositories().exclusiveContent((exclusiveContentRepository) -> {
			exclusiveContentRepository.forRepositories(repository);
			exclusiveContentRepository.filter((descriptor) -> descriptor.includeGroup(GENERATE_CHANGELOG_GROUP));
		});
	}

	private Provider<String> createUsernameProvider(Project project) {
		TaskProvider<GetGitHubUsernameTask> usernameTaskProvider =
				project.getTasks().register(GET_GITHUB_USERNAME_TASK, GetGitHubUsernameTask.class, (task) -> {
					task.setGroup(TASK_GROUP);
					task.setDescription("Use gitHubAccessToken to automatically set username property.");
					task.getGitHubAccessToken().set((String) project.findProperty(GITHUB_ACCESS_TOKEN_PROPERTY));
					task.getUsernameFile().set(project.getLayout().getBuildDirectory().file("github-username.txt"));
				});

		return usernameTaskProvider
				.flatMap(GetGitHubUsernameTask::getUsernameFile)
				.map(RegularFileUtils::readString);
	}

	private void registerCreateSaganReleaseTask(Project project, Provider<String> usernameProvider,
			SpringReleasePluginExtension release) {
		project.getTasks().register(CREATE_SAGAN_RELEASE_TASK, CreateSaganReleaseTask.class, (task) -> {
			task.setGroup(TASK_GROUP);
			task.setDescription("Create a new version for the specified project on spring.io.");
			task.getUsername().set(usernameProvider);
			task.getGitHubAccessToken().set((String) project.findProperty(GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getProjectName().set(project.getRootProject().getName());
			task.getVersion().set((String) project.findProperty(NEXT_VERSION_PROPERTY));
			task.getReferenceDocUrl().set(release.getReferenceDocUrl());
			task.getApiDocUrl().set(release.getApiDocUrl());
			task.getReplaceSnapshotVersionInReferenceDocUrl().set(release.getReplaceSnapshotVersionInReferenceDocUrl());
		});
	}

	private void registerDeleteSaganReleaseTask(Project project, Provider<String> usernameProvider) {
		project.getTasks().register(DELETE_SAGAN_RELEASE_TASK, DeleteSaganReleaseTask.class, (task) -> {
			task.setGroup(TASK_GROUP);
			task.setDescription("Delete a version for the specified project on spring.io.");
			task.getUsername().set(usernameProvider);
			task.getGitHubAccessToken().set((String) project.findProperty(GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getProjectName().set(project.getRootProject().getName());
			task.getVersion().set((String) project.findProperty(PREVIOUS_VERSION_PROPERTY));
		});
	}
}
