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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.github.api.GitHubApi;
import com.github.api.Milestone;
import com.github.api.Repository;
import io.spring.api.Release;
import io.spring.api.Release.ReleaseStatus;
import io.spring.api.SaganApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Steve Riesenberg
 */
public class SpringReleasesTests {

	private static final String OWNER = "spring-projects";

	private static final String REPO = "spring-security";

	// @formatter:off
	private static final List<Milestone> MILESTONES = List.of(new Milestone("6.0.4", 6L, toInstant("2023-06-19")),
			new Milestone("6.1.x", 100L, null),
			new Milestone("6.1.0-RC1", 4L, toInstant("2023-04-17")),
			new Milestone("6.1.0-M3", 3L, toInstant("2023-03-20")),
			new Milestone("6.1.0", 5L, toInstant("2023-05-22")),
			new Milestone("6.1.0-M1", 1L, toInstant("2023-01-16")),
			new Milestone("6.1.0-M2", 2L, toInstant("2023-02-20")));

	private static final List<Release> RELEASES = List.of(
			new Release("6.1.1", null, null, ReleaseStatus.GENERAL_AVAILABILITY, true),
			new Release("6.1.2-SNAPSHOT", null, null, ReleaseStatus.SNAPSHOT, true),
			new Release("6.0.5-SNAPSHOT", null, null, ReleaseStatus.SNAPSHOT, false),
			new Release("6.0.4", null, null, ReleaseStatus.GENERAL_AVAILABILITY, false),
			new Release("5.8.5-SNAPSHOT", null, null, ReleaseStatus.SNAPSHOT, false),
			new Release("5.8.4", null, null, ReleaseStatus.GENERAL_AVAILABILITY, false),
			new Release("5.7.10-SNAPSHOT", null, null, ReleaseStatus.SNAPSHOT, false),
			new Release("5.7.9", null, null, ReleaseStatus.GENERAL_AVAILABILITY, false));
	// @formatter:on

	private GitHubApi gitHubApi;

	private SaganApi saganApi;

	private SpringReleases springReleases;

	@BeforeEach
	public void setUp() {
		this.gitHubApi = mock(GitHubApi.class);
		this.saganApi = mock(SaganApi.class);
		this.springReleases = new SpringReleases(this.gitHubApi, this.saganApi);
	}

	@Test
	public void getNextReleaseMilestoneWhenReleaseVersionThenCurrentVersion() {
		var version = "6.1.0";
		var nextReleaseMilestone = this.springReleases.getNextReleaseMilestone(REPO, OWNER, version);
		assertThat(nextReleaseMilestone).isEqualTo(version);

		verifyNoInteractions(this.gitHubApi);
	}

	@Test
	public void getNextReleaseMilestoneWhenSnapshotVersionThenNextPreRelease() {
		when(this.gitHubApi.getMilestones(any(Repository.class))).thenReturn(MILESTONES);

		var version = "6.1.0-SNAPSHOT";
		var nextReleaseMilestone = this.springReleases.getNextReleaseMilestone(OWNER, REPO, version);
		assertThat(nextReleaseMilestone).isEqualTo("6.1.0-M1");

		var repositoryCaptor = forClass(Repository.class);
		verify(this.gitHubApi).getMilestones(repositoryCaptor.capture());
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);
	}

	@Test
	public void getNextReleaseMilestoneWhenMilestoneDoesNotExistThenBaseVersion() {
		var milestone = new Milestone("6.0.4", 6L, toInstant("2023-06-19"));
		when(this.gitHubApi.getMilestones(any(Repository.class))).thenReturn(List.of(milestone));

		var version = "6.1.0-SNAPSHOT";
		var nextReleaseMilestone = this.springReleases.getNextReleaseMilestone(OWNER, REPO, version);
		assertThat(nextReleaseMilestone).isEqualTo("6.1.0");

		var repositoryCaptor = forClass(Repository.class);
		verify(this.gitHubApi).getMilestones(repositoryCaptor.capture());
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);
	}

	@Test
	public void getNextReleaseMilestoneWhenPatchVersionThenBaseVersion() {
		var version = "6.1.1-SNAPSHOT";
		var nextReleaseMilestone = this.springReleases.getNextReleaseMilestone(REPO, OWNER, version);
		assertThat(nextReleaseMilestone).isEqualTo("6.1.1");

		verifyNoInteractions(this.gitHubApi);
	}

	@Test
	public void getPreviousReleaseMilestoneWhenReleaseVersionThenPreviousReleaseMilestone() {
		when(this.saganApi.getReleases(REPO)).thenReturn(new ArrayList<>(RELEASES));

		var version = "6.0.5";
		var previousReleaseMilestone = this.springReleases.getPreviousReleaseMilestone(REPO, version);
		assertThat(previousReleaseMilestone).isEqualTo("6.0.4");

		verify(this.saganApi).getReleases(REPO);
		verifyNoMoreInteractions(this.saganApi);
	}

	@Test
	public void getPreviousReleaseMilestoneWhenSnapshotVersionThenPreviousSnapshotVersion() {
		when(this.saganApi.getReleases(REPO)).thenReturn(new ArrayList<>(RELEASES));

		var version = "6.0.6-SNAPSHOT";
		var previousReleaseMilestone = this.springReleases.getPreviousReleaseMilestone(REPO, version);
		assertThat(previousReleaseMilestone).isEqualTo("6.0.5-SNAPSHOT");

		verify(this.saganApi).getReleases(REPO);
		verifyNoMoreInteractions(this.saganApi);
	}

	@Test
	public void getPreviousReleaseMilestoneWhenNoPreviousReleaseExistsThenNull() {
		when(this.saganApi.getReleases(REPO)).thenReturn(new ArrayList<>(RELEASES));

		var version = "6.2.0";
		var nextReleaseMilestone = this.springReleases.getPreviousReleaseMilestone(REPO, version);
		assertThat(nextReleaseMilestone).isNull();

		verify(this.saganApi).getReleases(REPO);
		verifyNoMoreInteractions(this.saganApi);
	}

	@Test
	public void getPreviousReleaseMilestoneWhenMultiplePreviousReleasesExistsThenNull() {
		var releases = new ArrayList<>(RELEASES);
		releases.add(new Release("6.1.0", null, null, ReleaseStatus.GENERAL_AVAILABILITY, false));
		when(this.saganApi.getReleases(REPO)).thenReturn(releases);

		var version = "6.1.2";
		var nextReleaseMilestone = this.springReleases.getPreviousReleaseMilestone(REPO, version);
		assertThat(nextReleaseMilestone).isNull();

		verify(this.saganApi).getReleases(REPO);
		verifyNoMoreInteractions(this.saganApi);
	}

	@Test
	public void isDueTodayWhenDueTodayThenTrue() {
		var version = "6.1.0";
		var milestone = new Milestone(version, 1L, Instant.now());
		when(this.gitHubApi.getMilestone(any(Repository.class), anyString())).thenReturn(milestone);

		var isDueToday = this.springReleases.isDueToday(OWNER, REPO, version);
		assertThat(isDueToday).isTrue();

		var repositoryCaptor = forClass(Repository.class);
		verify(this.gitHubApi).getMilestone(repositoryCaptor.capture(), eq(version));
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);
	}

	@Test
	public void isDueTodayWhenPastDueThenTrue() {
		var version = "6.1.0";
		var milestone = new Milestone(version, 1L, Instant.now().minus(1, ChronoUnit.DAYS));
		when(this.gitHubApi.getMilestone(any(Repository.class), anyString())).thenReturn(milestone);

		var isDueToday = this.springReleases.isDueToday(OWNER, REPO, version);
		assertThat(isDueToday).isTrue();

		var repositoryCaptor = forClass(Repository.class);
		verify(this.gitHubApi).getMilestone(repositoryCaptor.capture(), eq(version));
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);
	}

	@Test
	public void isDueTodayWhenDueTomorrowThenFalse() {
		var version = "6.1.0";
		var milestone = new Milestone(version, 1L, Instant.now().plus(1, ChronoUnit.DAYS));
		when(this.gitHubApi.getMilestone(any(Repository.class), anyString())).thenReturn(milestone);

		var isDueToday = this.springReleases.isDueToday(OWNER, REPO, version);
		assertThat(isDueToday).isFalse();

		var repositoryCaptor = forClass(Repository.class);
		verify(this.gitHubApi).getMilestone(repositoryCaptor.capture(), eq(version));
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);
	}

	@Test
	public void hasNoOpenIssuesWhenNoOpenIssuesThenTrue() {
		var version = "6.1.0";
		var milestone = new Milestone(version, 1L, Instant.now());
		when(this.gitHubApi.getMilestone(any(Repository.class), anyString())).thenReturn(milestone);
		when(this.gitHubApi.hasOpenIssues(any(Repository.class), anyLong())).thenReturn(false);

		var hasNoOpenIssues = this.springReleases.hasNoOpenIssues(OWNER, REPO, version);
		assertThat(hasNoOpenIssues).isTrue();

		var repositoryCaptor = forClass(Repository.class);
		verify(this.gitHubApi).getMilestone(repositoryCaptor.capture(), eq(version));
		verify(this.gitHubApi).hasOpenIssues(repositoryCaptor.getValue(), milestone.number());
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);
	}

	@Test
	public void createReleaseWhenValidParametersThenSuccess() {
		var version = "6.1.0";
		var branch = "main";
		var body = "release notes";
		var referenceDocUrl = "ref";
		var apiDocUrl = "api";
		this.springReleases.createRelease(OWNER, REPO, version, branch, body, referenceDocUrl, apiDocUrl);

		var repositoryCaptor = forClass(Repository.class);
		var gitHubReleaseCaptor = forClass(com.github.api.Release.class);
		var saganReleaseCaptor = forClass(Release.class);
		verify(this.gitHubApi).createRelease(repositoryCaptor.capture(), gitHubReleaseCaptor.capture());
		verify(this.saganApi).createRelease(eq(REPO), saganReleaseCaptor.capture());
		verifyNoMoreInteractions(this.gitHubApi, this.saganApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);

		var gitHubRelease = gitHubReleaseCaptor.getValue();
		assertThat(gitHubRelease.tag()).isEqualTo(version);
		assertThat(gitHubRelease.commit()).isEqualTo(branch);
		assertThat(gitHubRelease.body()).isEqualTo(body);
		assertThat(gitHubRelease.preRelease()).isFalse();

		var saganRelease = saganReleaseCaptor.getValue();
		assertThat(saganRelease.version()).isEqualTo(version);
		assertThat(saganRelease.referenceDocUrl()).isEqualTo(referenceDocUrl);
		assertThat(saganRelease.apiDocUrl()).isEqualTo(apiDocUrl);
		assertThat(saganRelease.status()).isNull();
		assertThat(saganRelease.current()).isFalse();
	}

	@Test
	public void deleteReleaseWhenValidParametersThenSuccess() {
		var version = "6.1.0";
		this.springReleases.deleteRelease(REPO, version);

		verify(this.saganApi).deleteRelease(REPO, version);
		verifyNoMoreInteractions(this.saganApi);
	}

	@Test
	public void getNextSnapshotVersionWhenReleaseVersionThenNextPatchVersion() {
		assertThat(SpringReleases.getNextSnapshotVersion("6.1.0")).isEqualTo("6.1.1-SNAPSHOT");
		assertThat(SpringReleases.getNextSnapshotVersion("6.1.1")).isEqualTo("6.1.2-SNAPSHOT");
	}

	@Test
	public void getNextSnapshotVersionWhenSnapshotVersionThenSnapshotVersion() {
		assertThat(SpringReleases.getNextSnapshotVersion("6.1.0-SNAPSHOT")).isEqualTo("6.1.0-SNAPSHOT");
		assertThat(SpringReleases.getNextSnapshotVersion("6.1.1-SNAPSHOT")).isEqualTo("6.1.1-SNAPSHOT");
	}

	@Test
	public void getNextSnapshotVersionWhenPreReleaseVersionThenSnapshotVersion() {
		assertThat(SpringReleases.getNextSnapshotVersion("6.1.0-M1")).isEqualTo("6.1.0-SNAPSHOT");
		assertThat(SpringReleases.getNextSnapshotVersion("6.1.0-RC1")).isEqualTo("6.1.0-SNAPSHOT");
	}

	@Test
	public void scheduleReleaseIfNotExistsWhenMinorVersionThenReleaseTrainCreated() {
		var version = "6.2.0";
		this.springReleases.scheduleReleaseIfNotExists(OWNER, REPO, version, 3, 1);

		var repositoryCaptor = forClass(Repository.class);
		var milestoneCaptor = forClass(Milestone.class);
		verify(this.gitHubApi).getMilestone(repositoryCaptor.capture(), eq(version));
		verify(this.gitHubApi, times(5)).createMilestone(repositoryCaptor.capture(), milestoneCaptor.capture());
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);

		var milestones = milestoneCaptor.getAllValues();
		assertThat(milestones.stream().map(Milestone::title).toList()).containsExactly("6.2.0-M1", "6.2.0-M2",
				"6.2.0-M3", "6.2.0-RC1", "6.2.0");
	}

	@Test
	public void scheduleReleaseIfNotExistsWhenPatchVersionThenPatchReleaseCreated() {
		var version = "6.2.1";
		this.springReleases.scheduleReleaseIfNotExists(OWNER, REPO, version, 3, 1);

		var repositoryCaptor = forClass(Repository.class);
		var milestoneCaptor = forClass(Milestone.class);
		verify(this.gitHubApi).getMilestone(repositoryCaptor.capture(), eq(version));
		verify(this.gitHubApi).createMilestone(repositoryCaptor.capture(), milestoneCaptor.capture());
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);

		var milestone = milestoneCaptor.getValue();
		assertThat(milestone.title()).isEqualTo("6.2.1");
	}

	@Test
	public void scheduleReleaseIfNotExistsWhenSnapshotVersionThenNotCreated() {
		var version = "6.2.0-SNAPSHOT";
		this.springReleases.scheduleReleaseIfNotExists(OWNER, REPO, version, 3, 1);

		verifyNoInteractions(this.gitHubApi);
	}

	@Test
	public void scheduleReleaseIfNotExistsWhenExistsThenNotCreated() {
		var version = "6.2.0";
		var milestone = new Milestone(version, 1L, null);
		when(this.gitHubApi.getMilestone(any(Repository.class), anyString())).thenReturn(milestone);

		this.springReleases.scheduleReleaseIfNotExists(OWNER, REPO, version, 3, 1);

		var repositoryCaptor = forClass(Repository.class);
		verify(this.gitHubApi).getMilestone(repositoryCaptor.capture(), eq(version));
		verifyNoMoreInteractions(this.gitHubApi);

		var repository = repositoryCaptor.getValue();
		assertThat(repository.owner()).isEqualTo(OWNER);
		assertThat(repository.name()).isEqualTo(REPO);
	}

	private static Instant toInstant(String date) {
		return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
	}

}
