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
package com.github.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Steve Riesenberg
 */
public class GitHubApi {
	private final WebClient webClient;

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
		var objectMapper = Jackson2ObjectMapperBuilder.json()
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.build();
		var encoder = new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON);
		var decoder = new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON);
		this.webClient = WebClient.builder()
				.codecs((configurer) -> configurer
						.defaultCodecs()
						.jackson2JsonEncoder(encoder)
				)
				.codecs((configurer) -> configurer
						.defaultCodecs()
						.jackson2JsonDecoder(decoder)
				)
				.baseUrl(baseUrl)
				.filter(new BearerAuthFilterFunction(accessToken))
				.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
				.build();
	}

	/**
	 * Retrieve a user by their personal access token.
	 *
	 * @return A GitHub User
	 */
	public User getUser() {
		return this.webClient.get()
				.uri("/user")
				.retrieve()
				.bodyToMono(User.class)
				.block();
	}

	/**
	 * Publish a release with no binary attachments.
	 *
	 * @param repository The repository owner/name
	 * @param release The contents of the release
	 */
	public void publishRelease(Repository repository, Release release) {
		this.webClient.post()
				.uri("/repos/{owner}/{name}/releases", repository.owner(), repository.name())
				.bodyValue(release)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
	}

	/**
	 * Create a milestone.
	 *
	 * @param repository The repository owner/name
	 * @param milestone The milestone containing a title and due date
	 */
	public void createMilestone(Repository repository, Milestone milestone) {
		this.webClient.post()
				.uri("/repos/{owner}/{name}/milestones", repository.owner(), repository.name())
				.bodyValue(milestone)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
	}

	/**
	 * Get the first 100 open milestones of a repository.
	 *
	 * @param repository The repository owner/name
	 * @return A list of the first 100 milestones for the repository
	 */
	public List<Milestone> getMilestones(Repository repository) {
		return this.webClient.get()
				.uri("/repos/{owner}/{name}/milestones?per_page=100", repository.owner(), repository.name())
				.retrieve()
				.bodyToFlux(Milestone.class)
				.collectList()
				.block();
	}

	/**
	 * Find an open milestone by milestone title.
	 *
	 * @param repository The repository owner/name
	 * @param title The milestone title
	 * @return The milestone, or null if not found within the first 100 milestones
	 */
	public Milestone getMilestone(Repository repository, String title) {
		return this.webClient.get()
				.uri("/repos/{owner}/{name}/milestones?per_page=100", repository.owner(), repository.name())
				.retrieve()
				.bodyToFlux(Milestone.class)
				.filter((milestone) -> milestone.title().equals(title))
				.next()
				.block();
	}

	/**
	 * Determine if a milestone has open issues.
	 *
	 * @param repository The repository owner/name
	 * @param milestone The milestone number
	 * @return true if the milestone has open issues, false otherwise
	 */
	public boolean hasOpenIssues(Repository repository, Long milestone) {
		Boolean result = this.webClient.get()
				.uri("/repos/{owner}/{name}/issues?per_page=1&milestone={milestone}",
						repository.owner(), repository.name(), milestone)
				.retrieve()
				.bodyToFlux(Issue.class)
				.count()
				.map((num) -> num > 0)
				.block();
		return (result != null && result);
	}
}
