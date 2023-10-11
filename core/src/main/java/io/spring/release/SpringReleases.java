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
package io.spring.release;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.api.GitHubApi;
import com.github.api.Milestone;
import com.github.api.Repository;
import io.spring.api.SaganApi;

/**
 * Perform automated releases of Spring projects using the GitHub and Sagan APIs.
 *
 * @author Steve Riesenberg
 */
public class SpringReleases {

	private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)(-.+)?$");

	private final GitHubApi gitHubApi;

	private final SaganApi saganApi;

	/**
	 * Create a new instance using a GitHub personal access token.
	 * <p>
	 * This constructor will immediately use the provided access token to look up the
	 * GitHub username of the user, which is required for the Sagan API.
	 * @param accessToken A GitHub personal access token, or null for anonymous access
	 * @see <a href=
	 * "https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens">
	 * Managing your personal access tokens</a>
	 */
	public SpringReleases(String accessToken) {
		this.gitHubApi = new GitHubApi(accessToken);
		if (accessToken != null) {
			this.saganApi = new SaganApi(this.gitHubApi.getUser().login(), accessToken);
		}
		else {
			this.saganApi = new SaganApi("anonymous", "invalid");
		}
	}

	/**
	 * Create a new instance.
	 * @param gitHubApi The pre-configured GitHubApi instance
	 * @param saganApi The pre-configured SaganApi instance
	 */
	public SpringReleases(GitHubApi gitHubApi, SaganApi saganApi) {
		this.gitHubApi = gitHubApi;
		this.saganApi = saganApi;
	}

	/**
	 * Finds or calculates the next release version based on the current version.
	 * <p>
	 * If the current version is a "SNAPSHOT" with a patch version of "0", the GitHub API
	 * is used to find the next milestone (sorted by due date) that matches the base
	 * version number. If no milestone exists, the base version is used instead. In all
	 * other cases, the base version is chosen automatically.
	 * @param owner The GitHub user or organization name
	 * @param repo The GitHub repository name
	 * @param version The current version used to find the next release version
	 * @return The version number of the next release milestone
	 */
	public String getNextReleaseMilestone(String owner, String repo, String version) {
		var versionMatcher = versionMatcher(version);
		if (!Objects.equals(versionMatcher.group(4), "-SNAPSHOT")) {
			return version;
		}

		var major = versionMatcher.group(1);
		var minor = versionMatcher.group(2);
		var patch = versionMatcher.group(3);
		var baseVersion = "%s.%s.%s".formatted(major, minor, patch);
		if (patch.equals("0")) {
			var milestones = this.gitHubApi.getMilestones(new Repository(owner, repo));
			var nextPreRelease = getNextPreRelease(baseVersion, milestones);
			if (nextPreRelease != null) {
				return nextPreRelease;
			}
		}

		return baseVersion;
	}

	/**
	 * Finds the previous release version based on the current version using the Sagan API
	 * (now backed by Contentful).
	 * <p>
	 * If the current version is a "SNAPSHOT", this method finds an existing "SNAPSHOT"
	 * version with the same major/minor version. If the current version is a GA version,
	 * this method finds an existing GA version with the same major/minor version. If
	 * multiple (ambiguous) options or no options exist (not found), this method returns
	 * null.
	 * @param repo The GitHub repository name
	 * @param version The current version used to find the next release version
	 * @return The version number of the previous release milestone, or null if not found
	 * @see <a href="https://api.spring.io/restdocs/index.html">Sagan API Docs</a>
	 */
	public String getPreviousReleaseMilestone(String repo, String version) {
		var versionMatcher = versionMatcher(version);
		var major = versionMatcher.group(1);
		var minor = versionMatcher.group(2);
		var versionIsSnapshot = Objects.equals(versionMatcher.group(4), "-SNAPSHOT");

		var releases = this.saganApi.getReleases(repo);
		releases.removeIf((candidate) -> {
			var matcher = versionMatcher(candidate.version());
			var candidateMajor = matcher.group(1);
			var candidateMinor = matcher.group(2);
			var candidateIsSnapshot = Objects.equals(matcher.group(4), "-SNAPSHOT");
			return versionIsSnapshot != candidateIsSnapshot || !candidateMajor.equals(major)
					|| !candidateMinor.equals(minor);
		});

		return (releases.size() == 1) ? releases.get(0).version() : null;
	}

	/**
	 * Checks if there are no open issues for the next release milestone.
	 * @param owner The GitHub user or organization name
	 * @param repo The GitHub repository name
	 * @param version The version used to check for open issues
	 * @return true if there are no open issues, or false otherwise
	 */
	public boolean hasNoOpenIssues(String owner, String repo, String version) {
		var repository = new Repository(owner, repo);
		var milestone = this.gitHubApi.getMilestone(repository, version);
		return !this.gitHubApi.hasOpenIssues(repository, milestone.number());
	}

	/**
	 * Checks if the given version is due today (or past due).
	 * @param owner The GitHub user or organization name
	 * @param repo The GitHub repository name
	 * @param version The version used to check the due date
	 * @return true if the release is due today (or past due), or false otherwise
	 */
	public boolean isDueToday(String owner, String repo, String version) {
		var repository = new Repository(owner, repo);
		var milestone = this.gitHubApi.getMilestone(repository, version);
		if (milestone == null || milestone.dueOn() == null) {
			return false;
		}

		var today = LocalDate.now();
		var dueOn = milestone.dueOn().atZone(ZoneOffset.UTC).toLocalDate();
		return !today.isBefore(dueOn);
	}

	/**
	 * Create a GitHub release with release notes using the GitHub API and a new release
	 * version for the current project on spring.io using the Sagan API.
	 * @param owner The GitHub user or organization name
	 * @param repo The GitHub repository name
	 * @param version The version used to create the release
	 * @param branch The branch used to tag the release
	 * @param body The body of the release notes (GitHub flavored markdown, can use the
	 * output of spring-io/github-release-notes-generator)
	 * @param referenceDocUrl The template URL for a version of the reference
	 * documentation (can contain the variable `{version}` which is automatically
	 * substituted based on the current version)
	 * @param apiDocUrl The template URL for a version of the API documentation (can
	 * contain the variable `{version}` which is automatically substituted based on the
	 * current version)
	 */
	public void createRelease(String owner, String repo, String version, String branch, String body,
			String referenceDocUrl, String apiDocUrl) {
		var repository = new Repository(owner, repo);
		this.gitHubApi.createRelease(repository, gitHubRelease(version, branch, body));
		this.saganApi.createRelease(repo, saganRelease(version, referenceDocUrl, apiDocUrl));
	}

	/**
	 * Create a GitHub release with release notes using the GitHub API.
	 * @param owner The GitHub user or organization name
	 * @param repo The GitHub repository name
	 * @param version The version used to create the release
	 * @param branch The branch used to tag the release
	 * @param body The body of the release notes (GitHub flavored markdown, can use the
	 * output of spring-io/github-release-notes-generator)
	 */
	public void createGitHubRelease(String owner, String repo, String version, String branch, String body) {
		var repository = new Repository(owner, repo);
		this.gitHubApi.createRelease(repository, gitHubRelease(version, branch, body));
	}

	/**
	 * Create a new release version for the current project on spring.io using the Sagan
	 * API.
	 * @param repo The GitHub repository name
	 * @param version The version used to create the release
	 * @param referenceDocUrl The template URL for a version of the reference
	 * documentation (can contain the variable `{version}` which is automatically
	 * substituted based on the current version)
	 * @param apiDocUrl The template URL for a version of the API documentation (can
	 * contain the variable `{version}` which is automatically substituted based on the
	 * current version)
	 */
	public void createSaganRelease(String repo, String version, String referenceDocUrl, String apiDocUrl) {
		this.saganApi.createRelease(repo, saganRelease(version, referenceDocUrl, apiDocUrl));
	}

	/**
	 * Delete a release version for the current project on spring.io using the Sagan API.
	 * @param repo The GitHub repository name
	 * @param version The version used to delete the release
	 */
	public void deleteRelease(String repo, String version) {
		this.saganApi.deleteRelease(repo, version);
	}

	/**
	 * Schedule the next release (even months only) or release train (series of milestones
	 * starting in January or July) based on the current version.
	 * <p>
	 * This method works with the concept of a Spring release train to automate scheduling
	 * one or more milestones using the given {@code weekOfMonth} and {@code dayOfWeek}
	 * values. All dates are calculated based on the first Monday of the month.
	 * <p>
	 * For example, if the current date is June 1, 2023, the current version is
	 * "1.0.0-SNAPSHOT", {@code weekOfMonth} is 2 and {@code dayOfWeek} is 4 (i.e. Spring
	 * Framework's release day), then this method can schedule a release train for July
	 * 13, 2023 ("1.0.0-M1"), August 17, 2023 ("1.0.0-M2"), September 14, 2023
	 * ("1.0.0-M3"), October 12, 2023 ("1.0.0-RC1") and November 16, 2023 ("1.0.0").
	 * <p>
	 * However with all other values being the same, if the current version is
	 * "1.0.1-SNAPSHOT", this method will simply schedule a patch release on the next even
	 * month (which is the current month in this example) of June 15, 2023 ("1.0.1"). The
	 * logic to determine whether to schedule a release train or a single patch release is
	 * based on the value of the patch version, where "x.x.0" attempts to schedule a
	 * release train, and "x.x.1" or higher schedules a patch release.
	 * <p>
	 * This method does nothing if the next release milestone already exists.
	 * @param owner The GitHub user or organization name
	 * @param repo The GitHub repository name
	 * @param version The version used to schedule the next release milestone (or release
	 * train)
	 * @param weekOfMonth The week of the month when releases for this project are
	 * scheduled (1-3) where 1 is the first week with a Monday
	 * @param dayOfWeek The day of the week when releases for this project are scheduled
	 * (1-5) where 1 is Monday and 5 is Friday
	 */
	public void scheduleReleaseIfNotExists(String owner, String repo, String version, int weekOfMonth, int dayOfWeek) {
		var versionMatcher = versionMatcher(version);
		if (versionMatcher.group(4) != null) {
			return;
		}

		var repository = new Repository(owner, repo);
		if (this.gitHubApi.getMilestone(repository, version) != null) {
			return;
		}

		// @formatter:off
		var releaseTrainSpec = SpringReleaseTrainSpec.builder()
				.nextTrain()
				.version(version)
				.weekOfMonth(dayOfWeek)
				.dayOfWeek(weekOfMonth)
				.build();
		// @formatter:on

		var releaseTrain = new SpringReleaseTrain(releaseTrainSpec);

		// Next milestone is either a patch version or minor version
		// Note: Major versions will be handled like minor and get a release
		// train which can be manually updated to match the desired schedule.
		if (version.endsWith(".0")) {
			// Create M1, M2, M3, RC1 and GA milestones for release train
			releaseTrain.getTrainDates().forEach((milestoneTitle, dueOn) -> {
				// Note: GitHub seems to store full date/time as UTC then displays
				// as a date (no time) in your timezone, which means the date will
				// not always be the same date as we intend.
				// For example, midnight UTC is actually 8pm CDT (the previous day).
				// We use 12pm/noon UTC to be as far from anybody's midnight as we can.
				var milestone = new Milestone(milestoneTitle, null,
						dueOn.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC));
				this.gitHubApi.createMilestone(repository, milestone);
			});
		}
		else {
			// Create GA milestone for patch release on the next even month
			var startDate = LocalDate.now();
			var dueOn = releaseTrain.getNextReleaseDate(startDate);
			var milestone = new Milestone(version, null, dueOn.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC));
			this.gitHubApi.createMilestone(repository, milestone);
		}
	}

	/**
	 * Calculates the next snapshot version based on the current version.
	 * <p>
	 * For example, if the current version is a milestone such as "1.0.0-M2", then this
	 * method returns "1.0.0-SNAPSHOT". If the current version is a GA version such as
	 * "1.0.0", then this method increments the patch version and returns
	 * "1.0.1-SNAPSHOT".
	 * @param version The version used to calculate the next snapshot version
	 * @return The next snapshot version
	 */
	public static String getNextSnapshotVersion(String version) {
		var versionMatcher = versionMatcher(version);
		if (Objects.equals(versionMatcher.group(4), "-SNAPSHOT")) {
			return version;
		}

		var major = versionMatcher.group(1);
		var minor = versionMatcher.group(2);
		var patch = versionMatcher.group(3);
		var modifier = versionMatcher.group(4);
		if (modifier == null) {
			patch = String.valueOf(Integer.parseInt(patch) + 1);
		}

		return "%s.%s.%s-SNAPSHOT".formatted(major, minor, patch);
	}

	private static String getNextPreRelease(String baseVersion, List<Milestone> milestones) {
		var versionPrefix = baseVersion + "-";
		// @formatter:off
		return milestones.stream()
				.filter((milestone) -> milestone.title().startsWith(versionPrefix))
				.sorted(Comparator.comparing(Milestone::dueOn))
				.map(Milestone::title)
				.findFirst()
				.orElse(null);
		// @formatter:on
	}

	private static com.github.api.Release gitHubRelease(String version, String branch, String body) {
		// @formatter:off
		return com.github.api.Release.tag(version)
				.commit(branch)
				.body(body)
				.preRelease(version.contains("-"))
				.build();
		// @formatter:on
	}

	private static io.spring.api.Release saganRelease(String version, String referenceDocUrl, String apiDocUrl) {
		return new io.spring.api.Release(version, referenceDocUrl, apiDocUrl, null, false);
	}

	/**
	 * Check a version number (e.g. "1.0.0-SNAPSHOT") against a pattern with the following
	 * groups captured:
	 * <ol>
	 * <li>major version</li>
	 * <li>minor version</li>
	 * <li>patch version</li>
	 * <li>optional suffix (e.g. "-SNAPSHOT" or "-RC1")</li>
	 * </ol>
	 * @param version The version number
	 * @return The Matcher instance
	 */
	public static Matcher versionMatcher(String version) {
		var versionMatcher = VERSION_PATTERN.matcher(version);
		if (!versionMatcher.find()) {
			throw new IllegalArgumentException("Given version is not a valid version: %s".formatted(version));
		}
		return versionMatcher;
	}

}
