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
	private static final String REFERENCE_DOC_URL = "https://docs.spring.io/spring-security/reference/{version}/index.html";
	private static final String API_DOC_URL = "https://docs.spring.io/spring-security/docs/{version}/api/";
	private static final String PROJECT_NAME = "spring-security";
	private static final String RELEASE_VERSION = "6.1.0";

	private SaganApi saganApi;

	private Release release;

	private MockWebServer server;

	@BeforeEach
	public void setUp() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		this.saganApi = new SaganApi(this.server.url("/").toString(), "user", "personal-access-token");
		this.release = new Release(RELEASE_VERSION, REFERENCE_DOC_URL, API_DOC_URL, ReleaseStatus.GENERAL_AVAILABILITY, true);
	}

	@AfterEach
	public void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	public void createReleaseWhenValidParametersThenSuccess() throws Exception {
		this.server.enqueue(new MockResponse().setResponseCode(204));
		this.saganApi.createRelease(PROJECT_NAME, this.release);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.POST.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/releases");
		assertThat(recordedRequest.getBody().readString(Charset.defaultCharset()))
				.isEqualTo(string("CreateReleaseRequest.json"));
	}

	@Test
	public void deleteReleaseWhenValidParametersThenSuccess() throws Exception {
		this.server.enqueue(new MockResponse().setResponseCode(204));
		this.saganApi.deleteRelease(PROJECT_NAME, RELEASE_VERSION);

		var recordedRequest = this.server.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo(HttpMethod.DELETE.name());
		assertThat(recordedRequest.getPath()).isEqualTo("/projects/spring-security/releases/6.1.0");
	}

	@Test
	public void getReleasesWhenExistsThenSuccess() throws Exception {
		this.server.enqueue(json("ReleasesResponse.json"));

		var releases = this.saganApi.getReleases(PROJECT_NAME);
		assertThat(releases).hasSize(8);

		var currentReleases = releases.stream().filter(Release::current).toList();
		assertThat(currentReleases).hasSize(1);
		assertThat(currentReleases.get(0).version()).isEqualTo(RELEASE_VERSION);
	}

	@Test
	public void getReleasesWhenEndOfLifeThenEmpty() throws Exception {
		this.server.enqueue(json("EmptyReleasesResponse.json"));

		var releases = this.saganApi.getReleases("spring-social");
		assertThat(releases).isEmpty();
	}

	private static MockResponse json(String path) throws IOException {
		return new MockResponse()
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody(string(path));
	}

	private static String string(String path) throws IOException {
		try (var inputStream = new ClassPathResource(path).getInputStream()) {
			return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
		}
	}
}
