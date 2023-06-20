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

import io.spring.gradle.core.BearerAuthFilterFunction;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Steve Riesenberg
 */
public class GitHubApi {
	private final WebClient webClient;

	public GitHubApi(String accessToken) {
		this("https://api.github.com", accessToken);
	}

	public GitHubApi(String baseUrl, String accessToken) {
		this.webClient = WebClient.builder()
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
	public void publishRelease(RepositoryRef repository, Release release) {
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
	public void createMilestone(RepositoryRef repository, Milestone milestone) {
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
	 * @return A list of milestones for the repository
	 */
	public List<Milestone> getMilestones(RepositoryRef repository) {
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
	 * @return The milestone, or null if not found
	 */
	public Milestone getMilestone(RepositoryRef repository, String title) {
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
	public boolean hasOpenIssues(RepositoryRef repository, Long milestone) {
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
