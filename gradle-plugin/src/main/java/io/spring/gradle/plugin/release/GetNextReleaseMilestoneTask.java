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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import org.springframework.util.Assert;

/**
 * @author Steve Riesenberg
 */
public abstract class GetNextReleaseMilestoneTask extends DefaultTask {

	public static final String TASK_NAME = "getNextReleaseMilestone";

	private static final String OUTPUT_VERSION_PATH = "next-release-milestone-version.txt";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getVersion();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@OutputFile
	public abstract RegularFileProperty getNextReleaseMilestoneFile();

	@TaskAction
	public void getNextReleaseMilestone() {
		var gitHubAccessToken = getGitHubAccessToken().getOrNull();
		var repository = getRepository().get();
		var version = getVersion().get();
		var springReleases = new SpringReleases(gitHubAccessToken);
		var nextReleaseMilestone = springReleases.getNextReleaseMilestone(repository.owner(), repository.name(),
				version);

		var outputFile = getNextReleaseMilestoneFile().get();
		RegularFileUtils.writeString(outputFile, nextReleaseMilestone);
		System.out.println(nextReleaseMilestone);
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, GetNextReleaseMilestoneTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription(
					"Calculates the next release version based on the current version and outputs the version number");
			task.doNotTrackState("API call to GitHub needs to check for new milestones every time");

			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.CURRENT_VERSION_PROPERTY)
				.orElse(project.getRootProject().getVersion().toString());

			var owner = springRelease.getRepositoryOwner().get();
			var name = springRelease.getRepositoryName().get();
			task.getRepository().set(new Repository(owner, name));
			task.getVersion().set(versionProvider);
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getNextReleaseMilestoneFile().set(project.getLayout().getBuildDirectory().file(OUTPUT_VERSION_PATH));
		});
	}

}
