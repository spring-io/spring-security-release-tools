/*
 * Copyright 2002-2024 the original author or authors.
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
import groovy.lang.MissingPropertyException;
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
public abstract class CreateGitHubReleaseTask extends DefaultTask {

	public static final String TASK_NAME = "createGitHubRelease";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<String> getBranch();

	@Input
	public abstract Property<Boolean> getCreateRelease();

	@Input
	@Optional
	public abstract Property<String> getReleaseNotes();

	@Input
	@Optional
	public abstract Property<String> getVersionPrefix();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@TaskAction
	public void createGitHubRelease() {
		var gitHubAccessToken = getGitHubAccessToken().getOrNull();
		var repository = getRepository().get();
		var version = getVersion().get();
		var versionPrefix = getVersionPrefix().getOrElse("");
		var branch = getBranch().get();

		var createRelease = getCreateRelease().get();
		if (createRelease && gitHubAccessToken == null) {
			throw new MissingPropertyException("Please provide an access token with -PgitHubAccessToken=...");
		}

		var body = getReleaseNotes().getOrNull();
		if (body == null) {
			// @formatter:off
			throw new MissingPropertyException(("Nothing was generated by the release-notes-generator, " +
				"perhaps because no issues were available in release milestone %s. " +
				"Please ensure there is at least one issue in the release.").formatted(version));
			// @formatter:on
		}

		System.out.printf("%sCreating GitHub release for %s/%s@%s%s%n", createRelease ? "" : "[DRY RUN] ",
				repository.owner(), repository.name(), versionPrefix, version);
		System.out.printf("%nRelease Notes:%n%n----%n%s%n----%n%n", body.trim());

		if (createRelease) {
			var springReleases = new SpringReleases(gitHubAccessToken);
			springReleases.createGitHubRelease(repository.owner(), repository.name(), versionPrefix + version, branch,
					body);
		}
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Objects.requireNonNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, CreateGitHubReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Create a GitHub release with release notes");
			task.doNotTrackState("API call to GitHub needs to check for new issues and create a release every time");

			// @formatter:off
			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.NEXT_VERSION_PROPERTY)
					.orElse(ProjectUtils.findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));
			var releaseNotesProvider = ProjectUtils.findTaskByType(project, GenerateChangelogTask.class)
					.getReleaseNotesFile()
					.map(RegularFileUtils::readString);
			var createReleaseProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.CREATE_RELEASE_PROPERTY)
					.map(Boolean::valueOf);
			// @formatter:on

			var owner = springRelease.getRepositoryOwner().get();
			var name = springRelease.getRepositoryName().get();
			var releaseVersionPrefix = springRelease.getReleaseVersionPrefix().get();
			task.getRepository().set(new Repository(owner, name));
			task.getVersion().set(versionProvider);
			task.getVersionPrefix().set(releaseVersionPrefix);
			task.getReleaseNotes().set(releaseNotesProvider);
			task.getBranch().set(ProjectUtils.getProperty(project, SpringReleasePlugin.BRANCH_PROPERTY).orElse("main"));
			task.getCreateRelease().set(createReleaseProvider.orElse(false));
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
		});
	}

}
