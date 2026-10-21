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

import io.spring.gradle.plugin.release.workflows.UpdateWorkflowsTask;
import io.spring.gradle.plugin.release.workflows.VerifyWorkflowsTask;
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
	static final String VERIFY_WORKFLOWS_PROPERTY = "verifyWorkflows";

	@Override
	public void apply(Project project) {
		// Register springRelease extension for DSL usage
		var springRelease = project.getExtensions().create(EXTENSION_NAME, SpringReleasePluginExtension.class);
		springRelease.getRepositoryOwner().convention("spring-projects");
		springRelease.getRepositoryName().convention(project.getRootProject().getName());
		springRelease.getReplaceVersionInReferenceDocUrl().convention(false);
		springRelease.getReleaseVersionPrefix().convention("");

		// Calculate the GitHub username for the provided access token
		GetGitHubUserNameTask.register(project);

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

		// Create release with release notes using GitHub and Sagan APIs
		CreateReleaseTask.register(project);

		// Create release with release notes using GitHub API
		CreateGitHubReleaseTask.register(project);

		// Create release version using Sagan API
		CreateSaganReleaseTask.register(project);

		// Check if the next milestone has open issues (prints true or false)
		CheckMilestoneHasOpenIssuesTask.register(project);

		// Check if the next milestone is due today (prints true or false)
		CheckMilestoneIsDueTodayTask.register(project);

		// Check if the current branch has OSS support
		CheckBranchHasOssSupportTask.register(project);

		// Check if the current branch has commercial support
		CheckBranchHasCommercialSupportTask.register(project);

		// Create release milestone if necessary using GitHub API
		ScheduleNextReleaseTask.register(project);

		// Close release milestone using GitHub API
		CloseMilestoneTask.register(project);

		// Verify that workflows are pinned and match their specs
		VerifyWorkflowsTask.register(project);
		project.getTasks().named(VerifyWorkflowsTask.TASK_NAME, (task) -> task.setGroup(TASK_GROUP));

		// Render workflow specs and run the workflow generator (local use only)
		UpdateWorkflowsTask.register(project);
		project.getTasks().named(UpdateWorkflowsTask.TASK_NAME, (task) -> task.setGroup(TASK_GROUP));

		// Make check verify the workflows too, for builds that ask for it with
		// -PverifyWorkflows, so that adopting this plugin doesn't break anyone's build.
		// Not project.findProperty, which would also find the task of the same name
		if (verifyWorkflowsRequested(project.getProviders().gradleProperty(VERIFY_WORKFLOWS_PROPERTY).getOrNull())) {
			verifyOnCheck(project);
		}
	}

	static boolean verifyWorkflowsRequested(String value) {
		return value != null && !"false".equalsIgnoreCase(value.trim());
	}

	static void verifyOnCheck(Project project) {
		project.getTasks()
			.matching((task) -> task.getName().equals("check"))
			.configureEach((check) -> check.dependsOn(VerifyWorkflowsTask.TASK_NAME));
	}

}
