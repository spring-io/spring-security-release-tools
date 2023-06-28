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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import com.github.api.GitHubApi;
import com.github.api.Milestone;
import com.github.api.Repository;
import io.spring.gradle.core.RegularFileUtils;
import io.spring.gradle.core.SpringReleaseTrain;
import io.spring.gradle.core.SpringReleaseTrainSpec;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import org.springframework.util.Assert;

import static io.spring.gradle.core.ProjectUtils.findTaskByType;
import static io.spring.gradle.core.ProjectUtils.getProperty;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.NEXT_VERSION_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class ScheduleNextReleaseTask extends DefaultTask {
	public static final String TASK_NAME = "scheduleNextRelease";

	@Input
	public abstract Property<Repository> getRepository();

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<Integer> getWeekOfMonth();

	@Input
	public abstract Property<Integer> getDayOfWeek();

	@TaskAction
	public void scheduleNextRelease() {
		var repository = getRepository().get();
		var gitHubAccessToken = getGitHubAccessToken().get();
		var version = getVersion().get();

		var gitHubApi = new GitHubApi(gitHubAccessToken);
		var hasExistingMilestone = gitHubApi.getMilestones(repository).stream()
				.anyMatch((milestone) -> version.equals(milestone.title()));
		if (hasExistingMilestone) {
			return;
		}

		// Next milestone is either a patch version or minor version
		// Note: Major versions will be handled like minor and get a release
		// train which can be manually updated to match the desired schedule.
		var releaseTrain = getReleaseTrain(version);
		if (version.endsWith(".0")) {
			// Create M1, M2, M3, RC1 and GA milestones for release train
			releaseTrain.getTrainDates().forEach((milestoneTitle, dueOn) -> {
				// Note: GitHub seems to store full date/time as UTC then displays
				// as a date (no time) in your timezone, which means the date will
				// not always be the same date as we intend.
				// For example, midnight UTC is actually 8pm CDT (the previous day).
				// We use 12pm/noon UTC to be as far from anybody's midnight as we can.
				var milestone = new Milestone(milestoneTitle, null, dueOn.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC));
				gitHubApi.createMilestone(repository, milestone);
			});
		} else {
			// Create GA milestone for patch release on the next even month
			var startDate = LocalDate.now();
			var dueOn = releaseTrain.getNextReleaseDate(startDate);
			var milestone = new Milestone(version, null, dueOn.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC));
			gitHubApi.createMilestone(repository, milestone);
		}
	}

	private SpringReleaseTrain getReleaseTrain(String nextReleaseMilestone) {
		var weekOfMonth = getWeekOfMonth().get();
		var dayOfWeek = getDayOfWeek().get();

		SpringReleaseTrainSpec releaseTrainSpec =
				SpringReleaseTrainSpec.builder()
						.nextTrain()
						.version(nextReleaseMilestone)
						.weekOfMonth(weekOfMonth)
						.dayOfWeek(dayOfWeek)
						.build();

		return new SpringReleaseTrain(releaseTrainSpec);
	}

	public static void register(Project project) {
		var springRelease = project.getExtensions().findByType(SpringReleasePluginExtension.class);
		Assert.notNull(springRelease, "Cannot find " + SpringReleasePluginExtension.class);

		project.getTasks().register(TASK_NAME, ScheduleNextReleaseTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Schedule the next release (even months only) or release train (series of milestones starting in January or July) based on the current version");
			task.doNotTrackState("API call to GitHub needs to check for new milestones every time");

			var versionProvider = getProperty(project, NEXT_VERSION_PROPERTY)
					.orElse(findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));

			var owner = springRelease.getRepositoryOwner().get();
			var name = project.getRootProject().getName();
			task.getRepository().set(new Repository(owner, name));
			task.getGitHubAccessToken().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getVersion().set(versionProvider);
			task.getWeekOfMonth().set(springRelease.getWeekOfMonth());
			task.getDayOfWeek().set(springRelease.getDayOfWeek());
		});
	}
}
