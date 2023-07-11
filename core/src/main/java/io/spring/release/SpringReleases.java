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
 * @author Steve Riesenberg
 */
public class SpringReleases {

	private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)(-.+)?$");

	private final GitHubApi gitHubApi;

	private final SaganApi saganApi;

	public SpringReleases(String accessToken) {
		this.gitHubApi = new GitHubApi(accessToken);
		if (accessToken != null) {
			this.saganApi = new SaganApi(this.gitHubApi.getUser().login(), accessToken);
		}
		else {
			this.saganApi = new SaganApi("anonymous", "invalid");
		}
	}

	public SpringReleases(GitHubApi gitHubApi, SaganApi saganApi) {
		this.gitHubApi = gitHubApi;
		this.saganApi = saganApi;
	}

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

	public boolean hasNoOpenIssues(String owner, String repo, String version) {
		var repository = new Repository(owner, repo);
		var milestone = this.gitHubApi.getMilestone(repository, version);
		return !this.gitHubApi.hasOpenIssues(repository, milestone.number());
	}

	public boolean isDueToday(String owner, String repo, String version) {
		var repository = new Repository(owner, repo);
		var milestone = this.gitHubApi.getMilestone(repository, version);
		var today = LocalDate.now();
		var dueOn = milestone.dueOn() != null ? milestone.dueOn().atZone(ZoneOffset.UTC).toLocalDate() : null;
		return (dueOn != null && today.compareTo(dueOn) >= 0);
	}

	public void createRelease(String owner, String repo, String version, String branch, String body,
			String referenceDocUrl, String apiDocUrl) {
		var repository = new Repository(owner, repo);
		this.gitHubApi.createRelease(repository, gitHubRelease(version, branch, body));
		this.saganApi.createRelease(repo, saganRelease(version, referenceDocUrl, apiDocUrl));
	}

	public void deleteRelease(String repo, String version) {
		this.saganApi.deleteRelease(repo, version);
	}

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

	private static Matcher versionMatcher(String version) {
		var versionMatcher = VERSION_PATTERN.matcher(version);
		if (!versionMatcher.find()) {
			throw new IllegalArgumentException("Given version is not a valid version: %s".formatted(version));
		}
		return versionMatcher;
	}

}
