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
package io.spring.api;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;

import io.spring.api.Release.ReleaseStatus;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Steve Riesenberg
 */
public class SaganApiTests {

	private SaganApi saganApi;

	private Release release;

	private MockWebServer server;

	@BeforeEach
	public void setUp() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		this.saganApi = new SaganApi(this.server.url("/").toString(), "user", "personal-access-token");
		// @formatter:off
		this.release = new Release("6.1.0",
				"https://docs.spring.io/spring-security/reference/{version}/index.html",
				"https://docs.spring.io/spring-security/site/docs/{version}/api/",
				ReleaseStatus.GENERAL_AVAILABILITY, true);
		// @formatter:on
	}

	@AfterEach
	public void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	public void getProjectsWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("ProjectsResponse.json"));

		var projects = this.saganApi.getProjects();
		assertThat(projects).hasSize(2);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects");
	}

	@Test
	public void getProjectWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("ProjectResponse.json"));

		var project = this.saganApi.getProject("spring-security");
		assertThat(project).isNotNull();
		assertThat(project.slug()).isEqualTo("spring-security");

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security");
	}

	@Test
	public void getReleasesWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("ReleasesResponse.json"));

		var releases = this.saganApi.getReleases("spring-security");
		assertThat(releases).hasSize(8);

		var currentReleases = releases.stream().filter(Release::current).toList();
		assertThat(currentReleases).hasSize(1);
		assertThat(currentReleases.get(0).version()).isEqualTo(this.release.version());

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/releases");
	}

	@Test
	public void getReleasesWhenEndOfLifeThenEmpty() throws Exception {
		this.server.enqueue(json("EmptyReleasesResponse.json"));

		var releases = this.saganApi.getReleases("spring-social");
		assertThat(releases).isEmpty();

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-social/releases");
	}

	@Test
	public void createReleaseWhenValidParametersThenSuccess() throws Exception {
		this.server.enqueue(new MockResponse().setResponseCode(204));
		this.saganApi.createRelease("spring-security", this.release);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.POST.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/releases");
		assertThat(recordedRequest.getBody().readString(Charset.defaultCharset()))
				.isEqualTo(string("CreateReleaseRequest.json"));
	}

	@Test
	public void getReleaseWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("ReleaseResponse.json"));

		var release = this.saganApi.getRelease("spring-security", this.release.version());
		assertThat(release.version()).isEqualTo(this.release.version());
		assertThat(release.referenceDocUrl()).isEqualTo(this.release.referenceDocUrl());
		assertThat(release.apiDocUrl()).isEqualTo(this.release.apiDocUrl());

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/releases/6.1.0");
	}

	@Test
	public void deleteReleaseWhenValidParametersThenSuccess() throws Exception {
		this.server.enqueue(new MockResponse().setResponseCode(204));
		this.saganApi.deleteRelease("spring-security", this.release.version());

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.DELETE.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/releases/6.1.0");
	}

	@Test
	public void getGenerationsWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("GenerationsResponse.json"));

		var generations = this.saganApi.getGenerations("spring-security");
		assertThat(generations).hasSize(6);

		// @formatter:off
		var currentGeneration = generations.stream()
				.max(Comparator.comparing(Generation::initialReleaseDate))
				.orElse(null);
		// @formatter:on
		assertThat(currentGeneration).isNotNull();
		assertThat(currentGeneration.name()).isEqualTo("6.1.x");
		assertThat(currentGeneration.initialReleaseDate()).isEqualTo(LocalDate.parse("2023-05-15"));
		assertThat(currentGeneration.ossSupportEndDate()).isEqualTo(LocalDate.parse("2024-05-15"));
		assertThat(currentGeneration.commercialSupportEndDate()).isEqualTo(LocalDate.parse("2025-09-15"));

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/generations");
	}

	@Test
	public void getGenerationsWhenEndOfLifeThenEmpty() throws Exception {
		this.server.enqueue(json("EmptyGenerationsResponse.json"));

		var generations = this.saganApi.getGenerations("spring-social");
		assertThat(generations).isEmpty();

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-social/generations");
	}

	@Test
	public void getGenerationWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("GenerationResponse.json"));

		var generation = this.saganApi.getGeneration("spring-security", "6.1.x");
		assertThat(generation).isNotNull();
		assertThat(generation.name()).isEqualTo("6.1.x");
		assertThat(generation.initialReleaseDate()).isEqualTo(LocalDate.parse("2023-05-15"));
		assertThat(generation.ossSupportEndDate()).isEqualTo(LocalDate.parse("2024-05-15"));
		assertThat(generation.commercialSupportEndDate()).isEqualTo(LocalDate.parse("2025-09-15"));

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.GET.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/generations/6.1.x");
	}

	private static MockResponse json(String path) throws IOException {
		// @formatter:off
		return new MockResponse()
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody(string(path));
		// @formatter:on
	}

	private static String string(String path) throws IOException {
		try (var inputStream = new ClassPathResource(path).getInputStream()) {
			return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
		}
	}

}
