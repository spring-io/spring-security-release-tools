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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * @author Steve Riesenberg
 */
public class GitHubApi {

	private static final Logger LOGGER = Logger.getLogger(GitHubApi.class.getName());

	private final HttpClient httpClient;

	private final ObjectMapper objectMapper;

	private final String baseUrl;

	private final String accessToken;

	/**
	 * @param accessToken The optional access token for the GitHub API
	 */
	public GitHubApi(String accessToken) {
		this("https://api.github.com", accessToken);
	}

	/**
	 * @param baseUrl The base URL of the GitHub API (for testing)
	 * @param accessToken The optional access token for the GitHub API
	 */
	public GitHubApi(String baseUrl, String accessToken) {
		this.httpClient = HttpClient.newHttpClient();
		this.objectMapper = getObjectMapper();
		this.baseUrl = baseUrl;
		this.accessToken = accessToken;
	}

	private static ObjectMapper getObjectMapper() {
		var objectMapper = new ObjectMapper();
		objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectMapper.registerModule(new JavaTimeModule());

		return objectMapper;
	}

	/**
	 * Retrieve a user by their personal access token.
	 * @return A GitHub User
	 */
	public User getUser() {
		var httpRequest = requestBuilder("/user").GET().build();
		return performRequest(httpRequest, User.class);
	}

	/**
	 * Create a release with no binary attachments.
	 * @param repository The repository owner/name
	 * @param release The contents of the release
	 */
	public void createRelease(Repository repository, Release release) {
		var uri = "/repos/%s/%s/releases".formatted(repository.owner(), repository.name());
		// @formatter:off
		var httpRequest = requestBuilder(uri)
			.header("Content-Type", "application/json")
			.POST(bodyValue(release))
			.build();
		// @formatter:on
		performRequest(httpRequest, Void.class);
	}

	/**
	 * Create a milestone.
	 * @param repository The repository owner/name
	 * @param milestone The milestone containing a title and due date
	 */
	public void createMilestone(Repository repository, Milestone milestone) {
		var uri = "/repos/%s/%s/milestones".formatted(repository.owner(), repository.name());
		// @formatter:off
		var httpRequest = requestBuilder(uri)
			.header("Content-Type", "application/json")
			.POST(bodyValue(milestone))
			.build();
		// @formatter:on
		try {
			performRequest(httpRequest, Void.class);
		}
		catch (HttpClientException ex) {
			if (ex.getStatusCode() == 422) {
				LOGGER.warning("Unable to create milestone %s: response=%s".formatted(milestone.title(),
						ex.getResponseBody()));
			}
			else {
				throw ex;
			}
		}
	}

	/**
	 * Get the first 100 open milestones of a repository.
	 * @param repository The repository owner/name
	 * @return A list of the first 100 milestones for the repository
	 */
	public List<Milestone> getMilestones(Repository repository) {
		var uri = "/repos/%s/%s/milestones?per_page=100".formatted(repository.owner(), repository.name());
		var httpRequest = requestBuilder(uri).GET().build();
		return new ArrayList<>(Arrays.asList(performRequest(httpRequest, Milestone[].class)));
	}

	/**
	 * Find an open milestone by milestone title.
	 * @param repository The repository owner/name
	 * @param title The milestone title
	 * @return The milestone, or null if not found within the first 100 milestones
	 */
	public Milestone getMilestone(Repository repository, String title) {
		var uri = "/repos/%s/%s/milestones?per_page=100".formatted(repository.owner(), repository.name());
		var httpRequest = requestBuilder(uri).GET().build();
		var milestones = performRequest(httpRequest, Milestone[].class);
		for (var m : milestones) {
			if (m.title().equals(title)) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Close a milestone.
	 * @param repository The repository owner/name
	 * @param milestone The milestone number
	 */
	public void closeMilestone(Repository repository, Long milestone) {
		var uri = "/repos/%s/%s/milestones/%s".formatted(repository.owner(), repository.name(), milestone);
		var request = Map.of("state", "closed");
		var httpRequest = requestBuilder(uri).method("PATCH", bodyValue(request)).build();
		performRequest(httpRequest, Void.class);
	}

	/**
	 * Determine if a milestone has open issues.
	 * @param repository The repository owner/name
	 * @param milestone The milestone number
	 * @return true if the milestone has open issues, false otherwise
	 */
	public boolean hasOpenIssues(Repository repository, Long milestone) {
		var uri = "/repos/%s/%s/issues?per_page=1&milestone=%s".formatted(repository.owner(), repository.name(),
				milestone);
		var httpRequest = requestBuilder(uri).GET().build();
		var issues = performRequest(httpRequest, Issue[].class);
		return (issues.length > 0);
	}

	private HttpRequest.Builder requestBuilder(String uri) {
		// @formatter:off
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create(this.baseUrl + uri).normalize())
			.header("Accept", "application/json")
			.header("X-GitHub-Api-Version", "2022-11-28");
		// @formatter:on
		if (this.accessToken != null) {
			builder.setHeader("Authorization", "Bearer %s".formatted(this.accessToken));
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

}
