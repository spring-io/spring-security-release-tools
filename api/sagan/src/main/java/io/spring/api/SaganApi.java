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

import java.util.Collections;
import java.util.List;

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
		// @formatter:off
		this.webClient = WebClient.builder()
				.baseUrl(baseUrl)
				.filter(new BasicAuthFilterFunction(username, accessToken))
				.build();
		// @formatter:on
	}

	public List<Project> getProjects() {
		// @formatter:off
		return this.webClient.get()
				.uri("/projects")
				.retrieve()
				.bodyToMono(EmbeddedProjectsWrapper.class)
				.mapNotNull(EmbeddedProjectsWrapper::_embedded)
				.map(EmbeddedProjects::projects)
				.defaultIfEmpty(Collections.emptyList())
				.block();
		// @formatter:on
	}

	public Project getProject(String slug) {
		// @formatter:off
		return this.webClient.get()
				.uri("/projects/{slug}", slug)
				.retrieve()
				.bodyToMono(Project.class)
				.block();
		// @formatter:on
	}

	public List<Release> getReleases(String slug) {
		// @formatter:off
		return this.webClient.get()
				.uri("/projects/{slug}/releases", slug)
				.retrieve()
				.bodyToMono(EmbeddedReleasesWrapper.class)
				.mapNotNull(EmbeddedReleasesWrapper::_embedded)
				.map(EmbeddedReleases::releases)
				.defaultIfEmpty(Collections.emptyList())
				.block();
		// @formatter:on
	}

	public void createRelease(String slug, Release release) {
		// @formatter:off
		this.webClient.post()
				.uri("/projects/{slug}/releases", slug)
				.bodyValue(release)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
		// @formatter:on
	}

	public Release getRelease(String slug, String version) {
		// @formatter:off
		return this.webClient.get()
				.uri("/projects/{slug}/releases/{version}", slug, version)
				.retrieve()
				.bodyToMono(Release.class)
				.block();
		// @formatter:on
	}

	public void deleteRelease(String slug, String version) {
		// @formatter:off
		this.webClient.delete()
				.uri("/projects/{slug}/releases/{version}", slug, version)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
		// @formatter:on
	}

	public List<Generation> getGenerations(String slug) {
		// @formatter:off
		return this.webClient.get()
				.uri("/projects/{slug}/generations", slug)
				.retrieve()
				.bodyToMono(EmbeddedGenerationsWrapper.class)
				.mapNotNull(EmbeddedGenerationsWrapper::_embedded)
				.map(EmbeddedGenerations::generations)
				.defaultIfEmpty(Collections.emptyList())
				.block();
		// @formatter:on
	}

	public Generation getGeneration(String slug, String name) {
		// @formatter:off
		return this.webClient.get()
				.uri("/projects/{slug}/generations/{name}", slug, name)
				.retrieve()
				.bodyToMono(Generation.class)
				.block();
		// @formatter:on
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
