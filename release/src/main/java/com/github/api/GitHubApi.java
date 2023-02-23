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

	public User getUser() {
		return this.webClient.get()
				.uri("/user")
				.retrieve()
				.bodyToMono(User.class)
				.block();
	}
}
