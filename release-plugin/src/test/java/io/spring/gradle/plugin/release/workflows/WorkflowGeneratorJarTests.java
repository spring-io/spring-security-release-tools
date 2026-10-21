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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link WorkflowGeneratorJar} and {@link GitHubReleases}.
 */
class WorkflowGeneratorJarTests {

	private static final byte[] JAR = "pretend this is a jar".getBytes(StandardCharsets.UTF_8);

	@TempDir
	Path cache;

	@Test
	void resolveWhenNotCachedThenDownloadsAndVerifies() throws Exception {
		try (FakeGitHub github = new FakeGitHub(JAR)) {
			Path jar = WorkflowGeneratorJar.resolve(generator(JAR), this.cache,
					new GitHubReleases(github.url(), "secret"));
			assertThat(jar).hasBinaryContent(JAR);
			assertThat(jar)
				.isEqualTo(this.cache.resolve("v0.0.7").resolve("github-actions-workflow-generator-0.0.7.jar"));
			assertThat(github.requests).anyMatch((path) -> path.endsWith("/releases/assets/42"));
		}
	}

	@Test
	void resolveWhenDownloadingThenTokenIsNotSentToStorage() throws Exception {
		try (FakeGitHub github = new FakeGitHub(JAR)) {
			WorkflowGeneratorJar.resolve(generator(JAR), this.cache, new GitHubReleases(github.url(), "secret"));
			assertThat(github.storageAuthorization).containsExactly("null");
		}
	}

	@Test
	void resolveWhenCachedAndMatchingThenNothingDownloaded() throws Exception {
		try (FakeGitHub github = new FakeGitHub(JAR)) {
			GitHubReleases releases = new GitHubReleases(github.url(), null);
			WorkflowGeneratorJar.resolve(generator(JAR), this.cache, releases);
			int requests = github.requests.size();
			WorkflowGeneratorJar.resolve(generator(JAR), this.cache, releases);
			assertThat(github.requests).hasSize(requests);
		}
	}

	@Test
	void resolveWhenCachedJarWasTamperedWithThenDownloadedAgain() throws Exception {
		try (FakeGitHub github = new FakeGitHub(JAR)) {
			GitHubReleases releases = new GitHubReleases(github.url(), null);
			Path jar = WorkflowGeneratorJar.resolve(generator(JAR), this.cache, releases);
			Files.writeString(jar, "tampered");
			assertThat(WorkflowGeneratorJar.resolve(generator(JAR), this.cache, releases)).hasBinaryContent(JAR);
		}
	}

	@Test
	void resolveWhenChecksumDoesNotMatchThenFailsAndLeavesNothingCached() throws Exception {
		try (FakeGitHub github = new FakeGitHub("something else".getBytes(StandardCharsets.UTF_8))) {
			assertThatIllegalStateException()
				.isThrownBy(() -> WorkflowGeneratorJar.resolve(generator(JAR), this.cache,
						new GitHubReleases(github.url(), null)))
				.withMessageContaining("checksum");
		}
		try (var files = Files.walk(this.cache)) {
			assertThat(files.filter(Files::isRegularFile)).isEmpty();
		}
	}

	@Test
	void downloadAssetWhenReleaseNotFoundThenExplainsToken() throws Exception {
		try (FakeGitHub github = new FakeGitHub(JAR)) {
			Path target = this.cache.resolve("jar");
			assertThatIllegalStateException()
				.isThrownBy(
						() -> new GitHubReleases(github.url(), null).downloadAsset("a/b", "missing", "x.jar", target))
				.withMessageContaining("gitHubAccessToken");
			assertThatIllegalStateException().isThrownBy(
					() -> new GitHubReleases(github.url(), "secret").downloadAsset("a/b", "missing", "x.jar", target))
				.withMessageNotContaining("gitHubAccessToken");
		}
	}

	private static WorkflowVersions.Generator generator(byte[] jar) throws Exception {
		return new WorkflowVersions.Generator("v0.0.7",
				HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar)), null);
	}

}
