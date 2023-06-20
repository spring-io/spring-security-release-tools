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

/**
 * @author Steve Riesenberg
 */
public class SpringReleasePlugin implements Plugin<Project> {
	static final String TASK_GROUP = "Release";
	static final String EXTENSION_NAME = "springRelease";

	static final String GITHUB_USER_NAME_PROPERTY = "gitHubUserName";
	static final String GITHUB_ACCESS_TOKEN_PROPERTY = "gitHubAccessToken";
	static final String PREVIOUS_VERSION_PROPERTY = "previousVersion";
	static final String NEXT_VERSION_PROPERTY = "nextVersion";
	static final String CREATE_RELEASE_PROPERTY = "createRelease";
	static final String BRANCH_PROPERTY = "branch";

	@Override
	public void apply(Project project) {
		var springRelease = project.getExtensions()
				.create(EXTENSION_NAME, SpringReleasePluginExtension.class);
		springRelease.getReplaceSnapshotVersionInReferenceDocUrl().convention(true);

		var usernameProvider = GetGitHubUserNameTask.register(project)
				.flatMap(GetGitHubUserNameTask::getUsernameFile)
				.map(RegularFileUtils::readString);

		var releaseNotesProvider = GenerateChangelogTask.register(project)
				.flatMap(GenerateChangelogTask::getReleaseNotes)
				.map(RegularFileUtils::readString);

		CreateSaganReleaseTask.register(project).configure((task) ->
				task.getUsername().set(usernameProvider));

		DeleteSaganReleaseTask.register(project).configure((task) ->
				task.getUsername().set(usernameProvider));

		CreateGitHubReleaseTask.register(project).configure((task) ->
				task.getReleaseNotes().set(releaseNotesProvider));

		CheckMilestoneHasNoOpenIssuesTask.register(project);
		CheckMilestoneIsDueTodayTask.register(project);
	}

}
