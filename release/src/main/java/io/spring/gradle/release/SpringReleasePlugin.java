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
	static final String CURRENT_VERSION_PROPERTY = "currentVersion";
	static final String CREATE_RELEASE_PROPERTY = "createRelease";
	static final String BRANCH_PROPERTY = "branch";

	@Override
	public void apply(Project project) {
		// Register springRelease extension for DSL usage
		var springRelease = project.getExtensions()
				.create(EXTENSION_NAME, SpringReleasePluginExtension.class);
		springRelease.getRepositoryOwner().convention("spring-projects");
		springRelease.getReplaceSnapshotVersionInReferenceDocUrl().convention(true);

		// Calculate the GitHub username for the provided access token
		GetGitHubUserNameTask.register(project);

		// Create release version using Sagan API
		CreateSaganReleaseTask.register(project);

		// Delete release version using Sagan API
		DeleteSaganReleaseTask.register(project);

		// Calculate the previous release milestone using Sagan API
		GetPreviousReleaseMilestoneTask.register(project);

		// Calculate the next release milestone using GitHub API
		GetNextReleaseMilestoneTask.register(project);

		// Calculate the next SNAPSHOT version
		GetNextSnapshotVersionTask.register(project);

		// Generate release notes for the next GitHub milestone
		GenerateChangelogTask.register(project);

		// Create release with release notes using GitHub API
		CreateGitHubReleaseTask.register(project);

		// Check if the next milestone has no open issues (prints true or false)
		CheckMilestoneHasNoOpenIssuesTask.register(project);

		// Check if the next milestone is due today (prints true or false)
		CheckMilestoneIsDueTodayTask.register(project);

		// Create release milestone if necessary using GitHub API
		ScheduleNextReleaseTask.register(project);
	}

}
