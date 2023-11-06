/*
 * Copyright 2019-2023 the original author or authors.
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * @author Steve Riesenberg
 */
public class SaganApi {

	private final HttpClient httpClient;

	private final ObjectMapper objectMapper;

	private final String baseUrl;

	private final String username;

	private final String accessToken;

	public SaganApi(String username, String accessToken) {
		this("https://api.spring.io", username, accessToken);
	}

	public SaganApi(String baseUrl, String username, String accessToken) {
		this.httpClient = HttpClient.newHttpClient();
		this.objectMapper = getObjectMapper();
		this.baseUrl = baseUrl;
		this.username = username;
		this.accessToken = accessToken;
	}

	private static ObjectMapper getObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectMapper.registerModule(new JavaTimeModule());

		return objectMapper;
	}

	public List<Project> getProjects() {
		var httpRequest = requestBuilder("/projects").GET().build();
		var wrapper = performRequest(httpRequest, EmbeddedProjectsWrapper.class);
		return (wrapper._embedded != null) ? wrapper._embedded.projects : Collections.emptyList();
	}

	public Project getProject(String slug) {
		var uri = "/projects/%s".formatted(slug);
		var httpRequest = requestBuilder(uri).GET().build();
		return performRequest(httpRequest, Project.class);
	}

	public List<Release> getReleases(String slug) {
		var uri = "/projects/%s/releases".formatted(slug);
		var httpRequest = requestBuilder(uri).GET().build();
		var wrapper = performRequest(httpRequest, EmbeddedReleasesWrapper.class);
		return (wrapper._embedded != null) ? wrapper._embedded.releases : Collections.emptyList();
	}

	public void createRelease(String slug, Release release) {
		var uri = "/projects/%s/releases".formatted(slug);
		var httpRequest = requestBuilder(uri).POST(bodyValue(release)).build();
		performRequest(httpRequest, Void.class);
	}

	public Release getRelease(String slug, String version) {
		var uri = "/projects/%s/releases/%s".formatted(slug, version);
		var httpRequest = requestBuilder(uri).GET().build();
		return performRequest(httpRequest, Release.class);
	}

	public void deleteRelease(String slug, String version) {
		var uri = "/projects/%s/releases/%s".formatted(slug, version);
		var httpRequest = requestBuilder(uri).DELETE().build();
		performRequest(httpRequest, Void.class);
	}

	public List<Generation> getGenerations(String slug) {
		var uri = "/projects/%s/generations".formatted(slug);
		var httpRequest = requestBuilder(uri).GET().build();
		var wrapper = performRequest(httpRequest, EmbeddedGenerationsWrapper.class);
		return (wrapper._embedded != null) ? wrapper._embedded.generations : Collections.emptyList();
	}

	public Generation getGeneration(String slug, String name) {
		var uri = "/projects/%s/generations/%s".formatted(slug, name);
		var httpRequest = requestBuilder(uri).GET().build();
		return performRequest(httpRequest, Generation.class);
	}

	private HttpRequest.Builder requestBuilder(String uri) {
		// @formatter:off
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create(this.baseUrl + uri).normalize());
		// @formatter:on
		if (this.username != null && this.accessToken != null) {
			var credentials = "%s:%s".formatted(this.username, this.accessToken);
			var basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
			builder.setHeader("Authorization", "Basic %s".formatted(basicAuth));
		}
		return builder;
	}

	private <T> T performRequest(HttpRequest httpRequest, Class<T> responseType) {
		try {
			var httpResponse = this.httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (httpResponse.statusCode() >= 300) {
				throw new HttpClientException(httpResponse.statusCode(), httpResponse.body());
			}
			String responseBody = Void.class.isAssignableFrom(responseType) ? "null" : httpResponse.body();
			return this.objectMapper.readValue(responseBody, responseType);
		}
		catch (IOException | InterruptedException ex) {
			throw new RuntimeException("Unable to perform request:", ex);
		}
	}

	private <T> HttpRequest.BodyPublisher bodyValue(T body) {
		try {
			return HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(body));
		}
		catch (JsonProcessingException ex) {
			throw new RuntimeException("Unable to serialize json:", ex);
		}
	}

	public static class HttpClientException extends RuntimeException {

		private final int statusCode;

		private final String responseBody;

		private HttpClientException(int statusCode, String responseBody) {
			super(statusCode + "[" + responseBody + "]");
			this.statusCode = statusCode;
			this.responseBody = responseBody;
		}

		public int getStatusCode() {
			return this.statusCode;
		}

		public String getResponseBody() {
			return this.responseBody;
		}

		@Override
		public String toString() {
			return this.statusCode + "[" + this.responseBody + "]";
		}

	}

	private record EmbeddedProjectsWrapper(EmbeddedProjects _embedded) {
	}

	private record EmbeddedProjects(List<Project> projects) {
	}

	private record EmbeddedReleasesWrapper(EmbeddedReleases _embedded) {
	}

	private record EmbeddedReleases(List<Release> releases) {
	}

	private record EmbeddedGenerationsWrapper(EmbeddedGenerations _embedded) {
	}

	private record EmbeddedGenerations(List<Generation> generations) {
	}

}
