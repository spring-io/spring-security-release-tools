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

import com.github.api.GitHubApi;
import com.github.api.Release;
import com.github.api.Repository;
import groovy.lang.MissingPropertyException;
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
import static io.spring.gradle.release.SpringReleasePlugin.BRANCH_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.CREATE_RELEASE_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.CURRENT_VERSION_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class CreateGitHubReleaseTask extends DefaultTask {
	public static final String TASK_NAME = "createGitHubRelease";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getCurrentVersion();

	@Input
	public abstract Property<String> getReleaseNotes();

	@Input
	public abstract Property<String> getBranch();

	@Input
	public abstract Property<Boolean> getCreateRelease();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@TaskAction
	public void createGitHubRelease() {
		var repository = getRepository().get();
		var body = getReleaseNotes().get();
		var currentVersion = getCurrentVersion().get();
		var branch = getBranch().get();
		var release = Release.tag(currentVersion)
				.commit(branch)
				.body(body)
				.preRelease(currentVersion.contains("-"))
				.build();

		var createRelease = getCreateRelease().get();
		var gitHubAccessToken = getGitHubAccessToken().getOrNull();
		if (createRelease && gitHubAccessToken == null) {
			throw new MissingPropertyException("Please provide an access token with -PgitHubAccessToken=...");
		}

		System.out.printf("%sCreating GitHub release for %s/%s@%s%n",
				createRelease ? "" : "[DRY RUN] ",
				repository.owner(),
				repository.name(),
				currentVersion
		);
		System.out.printf("%nRelease Notes:%n%n----%n%s%n----%n%n", body.trim());

		if (createRelease) {
			var gitHubApi = new GitHubApi(gitHubAccessToken);
			gitHubApi.publishRelease(repository, release);
		}
	}

	public static TaskProvider<CreateGitHubReleaseTask> register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		return project.getTasks().register(TASK_NAME, CreateGitHubReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Create a github release");
			task.doNotTrackState("API call to GitHub needs to check for new issues and create a release every time");

			var versionProvider = getProperty(project, CURRENT_VERSION_PROPERTY)
					.orElse(findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));

			var releaseNotesProvider = findTaskByType(project, GenerateChangelogTask.class)
					.getReleaseNotesFile()
					.map(RegularFileUtils::readString);

			var createReleaseProvider = getProperty(project, CREATE_RELEASE_PROPERTY)
					.map(Boolean::valueOf);

			var owner = springRelease.getRepositoryOwner().get();
			var name = project.getRootProject().getName();
			task.getRepository().set(new Repository(owner, name));
			task.getCurrentVersion().set(versionProvider);
			task.getReleaseNotes().set(releaseNotesProvider);
			task.getBranch().set(getProperty(project, BRANCH_PROPERTY).orElse("main"));
			task.getCreateRelease().set(createReleaseProvider.orElse(false));
			task.getGitHubAccessToken().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
		});
	}
}
