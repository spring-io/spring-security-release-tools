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

import java.util.Objects;

import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.gradle.plugin.core.RegularFileUtils;
import io.spring.release.SpringReleases;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * @author Steve Riesenberg
 */
public abstract class GetPreviousReleaseMilestoneTask extends DefaultTask {

	public static final String TASK_NAME = "getPreviousReleaseMilestone";

	private static final String OUTPUT_VERSION_PATH = "previous-release-milestone-version.txt";

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getProjectName();

	@Input
	public abstract Property<String> getVersion();

	@OutputFile
	public abstract RegularFileProperty getPreviousReleaseMilestoneFile();

	@TaskAction
	public void getPreviousReleaseMilestone() {
		var gitHubAccessToken = getGitHubAccessToken().get();
		var projectName = getProjectName().get();
		var version = getVersion().get();
		var outputFile = getPreviousReleaseMilestoneFile().get();

		var springReleases = new SpringReleases(gitHubAccessToken);
		var previousReleaseMilestone = springReleases.getPreviousReleaseMilestone(projectName, version);
		if (previousReleaseMilestone != null) {
			RegularFileUtils.writeString(outputFile, previousReleaseMilestone);
			System.out.println(previousReleaseMilestone);
		}
		else {
			RegularFileUtils.writeString(outputFile, "");
			System.out.println();
			getLogger().warn(
					"Unable to determine previous release milestone, either because multiple matches were found or none exists");
		}
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Objects.requireNonNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, GetPreviousReleaseMilestoneTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Finds the previous release version based on the current version.");
			task.doNotTrackState("API call to api.spring.io needs to check for releases every time");

			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.CURRENT_VERSION_PROPERTY)
				.orElse(project.getRootProject().getVersion().toString());

			var name = springRelease.getRepositoryName().get();
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getProjectName().set(name);
			task.getVersion().set(versionProvider);
			task.getPreviousReleaseMilestoneFile()
				.set(project.getLayout().getBuildDirectory().file(OUTPUT_VERSION_PATH));
		});
	}

}
