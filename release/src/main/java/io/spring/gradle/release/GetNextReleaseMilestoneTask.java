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

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.api.GitHubApi;
import com.github.api.Milestone;
import com.github.api.Repository;
import io.spring.gradle.core.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import org.springframework.util.Assert;

import static io.spring.gradle.core.ProjectUtils.getProperty;
import static io.spring.gradle.release.SpringReleasePlugin.CURRENT_VERSION_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class GetNextReleaseMilestoneTask extends DefaultTask {
	public static final String TASK_NAME = "getNextReleaseMilestone";

	private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)-SNAPSHOT$");
	private static final Pattern PRE_RELEASE_PATTERN = Pattern.compile("^.*-([A-Z]+)([0-9]+)$");
	private static final Map<String, Integer> MILESTONE_ORDER = Map.of("M", 1, "RC", 2);
	private static final String OUTPUT_VERSION_PATH = "next-release-milestone-version.txt";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getCurrentVersion();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@OutputFile
	public abstract RegularFileProperty getNextReleaseMilestoneFile();

	@TaskAction
	public void getNextReleaseMilestone() {
		var currentVersion = getCurrentVersion().get();
		var nextReleaseMilestone = findNextReleaseMilestone(currentVersion);
		var outputFile = getNextReleaseMilestoneFile().get();
		RegularFileUtils.writeString(outputFile, nextReleaseMilestone);
		System.out.println(nextReleaseMilestone);
	}

	private String findNextReleaseMilestone(String currentVersion) {
		if (!currentVersion.endsWith("-SNAPSHOT")) {
			return currentVersion;
		}

		var snapshotVersion = SNAPSHOT_PATTERN.matcher(currentVersion);
		if (!snapshotVersion.find()) {
			throw new IllegalArgumentException(
					"Cannot calculate next release version because given version is not a valid SNAPSHOT version");
		}

		var patchSegment = snapshotVersion.group(3);
		var baseVersion = currentVersion.replace("-SNAPSHOT", "");
		if (patchSegment.equals("0")) {
			var repository = getRepository().get();
			var gitHubAccessToken = getGitHubAccessToken().getOrNull();
			var gitHubApi = new GitHubApi(gitHubAccessToken);
			var milestones = gitHubApi.getMilestones(repository);
			var nextPreRelease = getNextPreRelease(baseVersion, milestones);
			if (nextPreRelease != null) {
				return nextPreRelease;
			}
		}

		return baseVersion;
	}

	private static String getNextPreRelease(String baseVersion, List<Milestone> milestones) {
		var versionPrefix = baseVersion + "-";
		return milestones.stream()
				.map(Milestone::title)
				.filter((milestone) -> milestone.startsWith(versionPrefix))
				.min(GetNextReleaseMilestoneTask::comparingMilestones)
				.orElse(null);
	}

	private static int comparingMilestones(String milestoneVersion1, String milestoneVersion2) {
		Matcher matcher1 = PRE_RELEASE_PATTERN.matcher(milestoneVersion1);
		Matcher matcher2 = PRE_RELEASE_PATTERN.matcher(milestoneVersion2);
		if (!matcher1.find() || !matcher2.find()) {
			return milestoneVersion1.compareTo(milestoneVersion2);
		}

		var milestoneType1 = matcher1.group(1);
		var milestoneType2 = matcher2.group(1);
		if (!milestoneType1.equals(milestoneType2)) {
			var order1 = MILESTONE_ORDER.getOrDefault(milestoneType1, 0);
			var order2 = MILESTONE_ORDER.getOrDefault(milestoneType2, 0);
			return order1.compareTo(order2);
		}

		var milestoneNumber1 = Integer.valueOf(matcher1.group(2));
		var milestoneNumber2 = Integer.valueOf(matcher2.group(2));
		return milestoneNumber1.compareTo(milestoneNumber2);
	}

	public static TaskProvider<GetNextReleaseMilestoneTask> register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		return project.getTasks().register(TASK_NAME, GetNextReleaseMilestoneTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Calculates the next release version based on the current version and outputs the version number");
			task.doNotTrackState("API call to GitHub needs to check for new milestones every time");

			var versionProvider = getProperty(project, CURRENT_VERSION_PROPERTY)
					.orElse(project.getRootProject().getVersion().toString());

			var owner = springRelease.getRepositoryOwner().get();
			var name = project.getRootProject().getName();
			task.getRepository().set(new Repository(owner, name));
			task.getCurrentVersion().set(versionProvider);
			task.getGitHubAccessToken().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getNextReleaseMilestoneFile().set(project.getLayout().getBuildDirectory().file(OUTPUT_VERSION_PATH));
		});
	}
}
