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

import java.util.List;

import io.spring.gradle.core.BasicAuthFilterFunction;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Steve Riesenberg
 */
public class SaganApi {
	private final WebClient webClient;

	public SaganApi(String username, String accessToken) {
		this("https://api.spring.io", username, accessToken);
	}

	public SaganApi(String baseUrl, String username, String accessToken) {
		this.webClient = WebClient.builder()
				.baseUrl(baseUrl)
				.filter(new BasicAuthFilterFunction(username, accessToken))
				.build();
	}

	public void createRelease(String project, Release release) {
		this.webClient.post()
				.uri("/projects/{project}/releases", project)
				.bodyValue(release)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
	}

	public void deleteRelease(String project, String release) {
		this.webClient.delete()
				.uri("/projects/{project}/releases/{release}", project, release)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
	}

	public List<Release> getReleases(String project) {
		return this.webClient.get()
				.uri("/projects/{project}/releases", project)
				.retrieve()
				.bodyToMono(EmbeddedReleasesWrapper.class)
				.map(EmbeddedReleasesWrapper::_embedded)
				.map(EmbeddedReleases::releases)
				.block();
	}

	private record EmbeddedReleasesWrapper(EmbeddedReleases _embedded) {}
	private record EmbeddedReleases(List<Release> releases) {}
}
