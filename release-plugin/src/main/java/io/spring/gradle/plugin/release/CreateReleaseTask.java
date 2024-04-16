/*
 * Copyright 2002-2023 the original author or authors.
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
public abstract class CreateReleaseTask extends DefaultTask {

	public static final String TASK_NAME = "createRelease";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<String> getReleaseNotes();

	@Input
	public abstract Property<String> getBranch();

	@Input
	public abstract Property<String> getReferenceDocUrl();

	@Input
	public abstract Property<String> getApiDocUrl();

	@Input
	public abstract Property<Boolean> getCreateRelease();

	@Input
	@Optional
	@Deprecated
	public abstract Property<Boolean> getReplaceSnapshotVersionInReferenceDocUrl();

	@Input
	public abstract Property<Boolean> getReplaceVersionInReferenceDocUrl();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@TaskAction
	public void createRelease() {
		var gitHubAccessToken = getGitHubAccessToken().getOrNull();
		var repository = getRepository().get();
		var version = getVersion().get();
		var branch = getBranch().get();
		var body = getReleaseNotes().get();
		var referenceDocUrl = getReferenceDocUrl().get();
		var apiDocUrl = getApiDocUrl().get();
		var createRelease = getCreateRelease().get();
		if (createRelease && gitHubAccessToken == null) {
			throw new MissingPropertyException("Please provide an access token with -PgitHubAccessToken=...");
		}

		// replace version numbers in referenceDocUrl for Antora
		var replaceVersion = getReplaceVersionInReferenceDocUrl().get();
		var replaceSnapshotVersion = getReplaceSnapshotVersionInReferenceDocUrl().getOrNull();
		if (replaceSnapshotVersion != null) {
			replaceVersion = replaceSnapshotVersion;
		}

		if (replaceVersion) {
			var versionMatcher = SpringReleases.versionMatcher(version);
			var majorVersion = versionMatcher.group(1);
			var minorVersion = versionMatcher.group(2);
			String majorMinorVersion;
			if (version.endsWith("-SNAPSHOT")) {
				majorMinorVersion = "%s.%s-SNAPSHOT".formatted(majorVersion, minorVersion);
			}
			else {
				majorMinorVersion = "%s.%s".formatted(majorVersion, minorVersion);
			}

			referenceDocUrl = referenceDocUrl.replace("{version}", majorMinorVersion);
		}

		System.out.printf("%sCreating release for %s/%s@%s%n", createRelease ? "" : "[DRY RUN] ", repository.owner(),
				repository.name(), version);
		System.out.printf("%nRelease Notes:%n%n----%n%s%n----%n%n", body.trim());
		System.out.printf("%nreferenceDocUrl=%s%napiDocUrl=%s%n", referenceDocUrl, apiDocUrl);

		if (createRelease) {
			var springReleases = new SpringReleases(gitHubAccessToken);
			springReleases.createRelease(repository.owner(), repository.name(), version, branch, body, referenceDocUrl,
					apiDocUrl);
		}
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Objects.requireNonNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, CreateReleaseTask.class, (task) -> {
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
			var name = project.getRootProject().getName();
			task.getRepository().set(new Repository(owner, name));
			task.getVersion().set(versionProvider);
			task.getReleaseNotes().set(releaseNotesProvider);
			task.getBranch().set(ProjectUtils.getProperty(project, SpringReleasePlugin.BRANCH_PROPERTY).orElse("main"));
			task.getReferenceDocUrl().set(springRelease.getReferenceDocUrl());
			task.getApiDocUrl().set(springRelease.getApiDocUrl());
			task.getReplaceSnapshotVersionInReferenceDocUrl()
				.set(springRelease.getReplaceSnapshotVersionInReferenceDocUrl());
			task.getReplaceVersionInReferenceDocUrl().set(springRelease.getReplaceVersionInReferenceDocUrl());
			task.getCreateRelease().set(createReleaseProvider.orElse(false));
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
		});
	}

}
