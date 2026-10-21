/*
 * Copyright 2026 the original author or authors.
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

package io.spring.gradle.plugin.release.workflows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The little of the GitHub API that workflows need: a release's assets. Redirects are
 * followed by hand so that the token is only ever sent to GitHub's API, not to the
 * storage a release asset is served from.
 *
 * @author Josh Cummings
 */
final class GitHubReleases {

	private static final int MAX_REDIRECTS = 5;

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration API_TIMEOUT = Duration.ofSeconds(30);

	// a jar is some 18MB, and this bounds the whole transfer
	private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

	private final HttpClient client = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NEVER)
		.connectTimeout(CONNECT_TIMEOUT)
		.build();

	private final ObjectMapper mapper = new ObjectMapper();

	private final String apiUrl;

	private final String token;

	GitHubReleases(String apiUrl, String token) {
		this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
		this.token = token;
	}

	/**
	 * Download a release's asset.
	 * @param repository {@code owner/repo}
	 * @param tag the release's tag
	 * @param assetName the name of the asset
	 * @param target where to write it
	 */
	void downloadAsset(String repository, String tag, String assetName, Path target) {
		HttpResponse<byte[]> release = send(this.apiUrl + "/repos/" + repository + "/releases/tags/" + tag,
				"application/vnd.github+json", true, API_TIMEOUT, HttpResponse.BodyHandlers.ofByteArray());
		JsonNode assets = json(release, repository + " " + tag).path("assets");
		for (JsonNode asset : assets) {
			if (assetName.equals(asset.path("name").asText())) {
				send(this.apiUrl + "/repos/" + repository + "/releases/assets/" + asset.path("id").asLong(),
						"application/octet-stream", true, DOWNLOAD_TIMEOUT, HttpResponse.BodyHandlers.ofFile(target));
				return;
			}
		}
		throw new IllegalStateException("release " + tag + " of " + repository + " has no asset named " + assetName);
	}

	private <T> HttpResponse<T> send(String url, String accept, boolean authenticated, Duration timeout,
			HttpResponse.BodyHandler<T> handler) {
		try {
			for (int i = 0; i <= MAX_REDIRECTS; i++) {
				HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
					.timeout(timeout)
					.header("Accept", accept);
				if (authenticated && this.token != null) {
					request.header("Authorization", "Bearer " + this.token);
				}
				HttpResponse<T> response = this.client.send(request.build(), handler);
				int status = response.statusCode();
				if (status >= 300 && status < 400 && response.headers().firstValue("Location").isPresent()) {
					url = URI.create(url).resolve(response.headers().firstValue("Location").get()).toString();
					authenticated = false;
					continue;
				}
				if (status == 404 || status == 401 || status == 403) {
					throw new IllegalStateException(url + " returned " + status + ((this.token != null) ? ""
							: ". If the repository is private or internal, pass a token with -PgitHubAccessToken"
									+ " or set GITHUB_TOKEN"));
				}
				if (status / 100 != 2) {
					throw new IllegalStateException(url + " returned " + status);
				}
				return response;
			}
			throw new IllegalStateException("too many redirects for " + url);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while calling " + url, ex);
		}
	}

	private JsonNode json(HttpResponse<byte[]> response, String what) {
		try {
			return this.mapper.readTree(response.body());
		}
		catch (IOException ex) {
			throw new IllegalStateException("unreadable response for " + what, ex);
		}
	}

}
