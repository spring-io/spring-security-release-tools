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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.spring.api.SaganApi;
import io.spring.gradle.core.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import static io.spring.gradle.core.ProjectUtils.findTaskByType;
import static io.spring.gradle.core.ProjectUtils.getProperty;
import static io.spring.gradle.release.SpringReleasePlugin.CURRENT_VERSION_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_USER_NAME_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class GetPreviousReleaseMilestoneTask extends DefaultTask {
	public static final String TASK_NAME = "getPreviousReleaseMilestone";

	private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)(-.+)?$");
	private static final String OUTPUT_VERSION_PATH = "previous-release-milestone-version.txt";

	@Input
	public abstract Property<String> getUsername();

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
		var username = getUsername().get();
		var gitHubAccessToken = getGitHubAccessToken().get();
		var projectName = getProjectName().get();
		var version = getVersion().get();
		var saganApi = new SaganApi(username, gitHubAccessToken);

		var versionMatcher = versionMatcher(version);
		var major = versionMatcher.group(1);
		var minor = versionMatcher.group(2);

		var releases = saganApi.getReleases(projectName);
		releases.removeIf((release) -> {
			if (version.endsWith("-SNAPSHOT") != release.version().endsWith("-SNAPSHOT")) {
				return true;
			}
			var matcher = versionMatcher(release.version());
			return !matcher.group(1).equals(major) || !matcher.group(2).equals(minor);
		});

		if (releases.size() == 1) {
			var release = releases.get(0);
			var outputFile = getPreviousReleaseMilestoneFile().get();
			RegularFileUtils.writeString(outputFile, release.version());
			System.out.println(release.version());
		} else if (releases.isEmpty()) {
			System.out.println("No previous release milestone found");
		} else {
			System.out.println("Unable to determine previous release milestone because multiple matches were found");
		}
	}

	private static Matcher versionMatcher(String version) {
		var versionMatcher = VERSION_PATTERN.matcher(version);
		if (!versionMatcher.find()) {
			throw new IllegalArgumentException(
					"Given version is not a valid version: " + version);
		}
		return versionMatcher;
	}

	public static void register(Project project) {
		project.getTasks().register(TASK_NAME, GetPreviousReleaseMilestoneTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Finds the previous release version based on the current version.");

			var usernameProvider = getProperty(project, GITHUB_USER_NAME_PROPERTY)
					.orElse(findTaskByType(project, GetGitHubUserNameTask.class)
							.getUsernameFile()
							.map(RegularFileUtils::readString));

			var versionProvider = getProperty(project, CURRENT_VERSION_PROPERTY)
					.orElse(project.getRootProject().getVersion().toString());

			task.getUsername().set(usernameProvider);
			task.getGitHubAccessToken().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getProjectName().set(project.getRootProject().getName());
			task.getVersion().set(versionProvider);
			task.getPreviousReleaseMilestoneFile().set(project.getLayout().getBuildDirectory().file(OUTPUT_VERSION_PATH));
		});
	}
}
