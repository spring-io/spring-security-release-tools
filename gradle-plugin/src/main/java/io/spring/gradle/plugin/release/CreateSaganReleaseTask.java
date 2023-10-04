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

import com.github.api.Repository;
import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.gradle.plugin.core.RegularFileUtils;
import io.spring.release.SpringReleases;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import org.springframework.util.Assert;

/**
 * @author Steve Riesenberg
 */
public abstract class CreateSaganReleaseTask extends DefaultTask {

	public static final String TASK_NAME = "createSaganRelease";

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<String> getReferenceDocUrl();

	@Input
	public abstract Property<String> getApiDocUrl();

	@Input
	public abstract Property<Boolean> getReplaceSnapshotVersionInReferenceDocUrl();

	@TaskAction
	public void createSaganRelease() {
		var gitHubAccessToken = getGitHubAccessToken().get();
		var repository = getRepository().get();
		var version = getVersion().get();
		var referenceDocUrl = getReferenceDocUrl().get();
		var apiDocUrl = getApiDocUrl().get();

		// replace "-SNAPSHOT" in version numbers in referenceDocUrl for Antora
		var replaceSnapshotVersion = getReplaceSnapshotVersionInReferenceDocUrl().get();
		if (replaceSnapshotVersion && version.endsWith("-SNAPSHOT")) {
			var versionMatcher = SpringReleases.versionMatcher(version);
			var majorVersion = versionMatcher.group(1);
			var minorVersion = versionMatcher.group(2);
			var majorMinorVersion = "%s.%s-SNAPSHOT".formatted(majorVersion, minorVersion);
			referenceDocUrl = referenceDocUrl.replace("{version}", majorMinorVersion);
		}

		var springReleases = new SpringReleases(gitHubAccessToken);
		springReleases.createSaganRelease(repository.name(), version, referenceDocUrl, apiDocUrl);
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, CreateSaganReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Create a version for the specified project on spring.io.");
			task.doNotTrackState("API call to api.spring.io needs to check for releases every time");

			// @formatter:off
			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.NEXT_VERSION_PROPERTY)
					.orElse(ProjectUtils.findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));
			// @formatter:on

			var owner = springRelease.getRepositoryOwner().get();
			var name = project.getRootProject().getName();
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getRepository().set(new Repository(owner, name));
			task.getVersion().set(versionProvider);
			task.getReferenceDocUrl().set(springRelease.getReferenceDocUrl());
			task.getApiDocUrl().set(springRelease.getApiDocUrl());
			task.getReplaceSnapshotVersionInReferenceDocUrl()
				.set(springRelease.getReplaceSnapshotVersionInReferenceDocUrl());
		});
	}

}
