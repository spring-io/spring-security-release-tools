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

package com.github.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonassert.JsonAssert;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.CoreMatchers.is;

public class GitHubApiTests {

	private static final String AUTH_TOKEN = "personal-access-token";

	private GitHubApi githubApi;

	private Repository repository;

	private MockWebServer server;

	@BeforeEach
	public void setUp() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		this.githubApi = new GitHubApi(this.server.url("/").toString(), AUTH_TOKEN);
		this.repository = new Repository("spring-projects", "spring-security");
	}

	@AfterEach
	public void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	public void getUserWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("UserResponse.json"));

		var user = this.githubApi.getUser();
		assertThat(user.login()).isEqualTo("octocat");
		assertThat(user.name()).isEqualTo("The Octocat");
		assertThat(user.url()).isEqualTo("https://api.github.com/users/octocat");

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).isEqualTo("/user");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));
	}

	@Test
	public void getUserWhenAccessTokenIsNullThenNoAuthorizationHeader() throws Exception {
		this.githubApi = new GitHubApi(this.server.url("/").toString(), null);
		this.server.enqueue(new MockResponse().setResponseCode(401));

		// @formatter:off
		assertThatExceptionOfType(GitHubApi.HttpClientException.class)
			.isThrownBy(() -> this.githubApi.getUser());
		// @formatter:on

		var recordedRequest = this.server.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).isEqualTo("/user");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeaders().names().contains("Authorization")).isFalse();
	}

	@Test
	public void createReleaseWhenValidParametersThenSuccess() throws Exception {
		this.server.enqueue(json("CreateReleaseResponse.json").setResponseCode(201));

		var release = Release.tag("1.0.0").build();
		this.githubApi.createRelease(this.repository, release);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).isEqualTo("/repos/spring-projects/spring-security/releases");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));

		var json = JsonAssert.with(recordedRequest.getBody().readString(Charset.defaultCharset()));
		json.assertThat("$.tag_name", is("1.0.0"));
		json.assertThat("$.draft", is(false));
		json.assertThat("$.prerelease", is(false));
		json.assertThat("$.generate_release_notes", is(false));
	}

	@Test
	public void createMilestoneWhenValidParametersThenSuccess() throws Exception {
		this.server.enqueue(json("CreateMilestoneResponse.json").setResponseCode(201));

		var dueOn = Instant.parse("2022-05-04T12:00:00Z");
		var milestone = new Milestone("1.0.0", null, dueOn);
		this.githubApi.createMilestone(this.repository, milestone);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).isEqualTo("/repos/spring-projects/spring-security/milestones");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));

		var json = JsonAssert.with(recordedRequest.getBody().readString(Charset.defaultCharset()));
		json.assertThat("$.title", is("1.0.0"));
		json.assertThat("$.due_on", is("2022-05-04T12:00:00Z"));
	}

	@Test
	public void createMilestoneWhenAlreadyExistsThenSuccessWithWarning() throws Exception {
		this.server.enqueue(json("CreateMilestoneErrorResponse.json").setResponseCode(422));

		var dueOn = Instant.parse("2022-05-04T12:00:00Z");
		var milestone = new Milestone("1.0.0", null, dueOn);
		this.githubApi.createMilestone(this.repository, milestone);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).isEqualTo("/repos/spring-projects/spring-security/milestones");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));

		var json = JsonAssert.with(recordedRequest.getBody().readString(Charset.defaultCharset()));
		json.assertThat("$.title", is("1.0.0"));
		json.assertThat("$.due_on", is("2022-05-04T12:00:00Z"));
	}

	@Test
	public void getMilestonesWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("MilestonesResponse.json"));

		var milestones = this.githubApi.getMilestones(this.repository);
		assertThat(milestones).hasSize(2);
		assertThat(milestones.get(0).number()).isEqualTo(207);
		assertThat(milestones.get(1).number()).isEqualTo(191);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath())
			.isEqualTo("/repos/spring-projects/spring-security/milestones?per_page=100");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));
	}

	@Test
	public void getMilestoneWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("MilestonesResponse.json"));

		var milestone = this.githubApi.getMilestone(this.repository, "5.5.0-RC1");
		assertThat(milestone.number()).isEqualTo(191);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath())
			.isEqualTo("/repos/spring-projects/spring-security/milestones?per_page=100");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));
	}

	@Test
	public void getMilestoneWhenNotFoundThenNull() throws Exception {
		this.server.enqueue(json("MilestonesResponse.json"));

		var milestone = this.githubApi.getMilestone(this.repository, "missing");
		assertThat(milestone).isNull();

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath())
			.isEqualTo("/repos/spring-projects/spring-security/milestones?per_page=100");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));
	}

	@Test
	public void hasOpenIssuesWhenClosedThenFalse() throws Exception {
		this.server.enqueue(json("EmptyArrayResponse.json"));

		var hasOpenIssues = this.githubApi.hasOpenIssues(this.repository, 202L);
		assertThat(hasOpenIssues).isFalse();

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath())
			.isEqualTo("/repos/spring-projects/spring-security/issues?per_page=1&milestone=202");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));
	}

	@Test
	public void hasOpenIssuesWhenOpenThenTrue() throws Exception {
		this.server.enqueue(json("IssuesResponse.json"));

		var hasOpenIssues = this.githubApi.hasOpenIssues(this.repository, 191L);
		assertThat(hasOpenIssues).isTrue();

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath())
			.isEqualTo("/repos/spring-projects/spring-security/issues?per_page=1&milestone=191");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));
	}

	@Test
	public void closeMilestoneWhenValidParametersThenSuccess() throws Exception {
		this.server.enqueue(json("CreateMilestoneResponse.json"));

		this.githubApi.closeMilestone(this.repository, 191L);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PATCH");
		assertThat(recordedRequest.getPath()).isEqualTo("/repos/spring-projects/spring-security/milestones/191");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));

		var json = JsonAssert.with(recordedRequest.getBody().readString(Charset.defaultCharset()));
		json.assertThat("$.state", is("closed"));
	}

	@Test
	public void createReleaseIssueWhenMilestoneThenAssignedToMilestone() throws Exception {
		this.server.enqueue(json("CreateReleaseIssueResponse.json"));

		this.githubApi.createReleaseIssue(this.repository, new Milestone("6.1.9", 12L, Instant.now()));

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).isEqualTo("/repos/spring-projects/spring-security/issues");
		assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer %s".formatted(AUTH_TOKEN));

		var json = JsonAssert.with(recordedRequest.getBody().readString(Charset.defaultCharset()));
		json.assertThat("$.title", is("Release 6.1.9"));
	}

	private static MockResponse json(String path) throws IOException {
		return new MockResponse().addHeader("Content-Type", "application/json").setBody(string(path));
	}

	private static String string(String path) throws IOException {
		var outputStream = new ByteArrayOutputStream();
		try (var inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(path)) {
			Objects.requireNonNull(inputStream).transferTo(outputStream);
			return outputStream.toString(StandardCharsets.UTF_8);
		}
	}

}
