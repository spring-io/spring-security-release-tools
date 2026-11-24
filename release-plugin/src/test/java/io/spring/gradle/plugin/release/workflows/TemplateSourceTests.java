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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link TemplateSource}, {@link LocalCheckout} and what {@link GitHubReleases}
 * does to find the templates.
 */
class TemplateSourceTests {

	private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

	private static final String REPOSITORY = TemplateSource.DEFAULT_REPOSITORY;

	@TempDir
	Path directory;

	private final List<String> log = new ArrayList<>();

	@Test
	void resolveWhenPinnedThenSpecsAreDownloadedOnceAndVersionsLaidOverTheRepositorys() throws Exception {
		Path config = pinned();
		Path local = versions("{\"actions\":{\"a/b\":{\"ref\":\"v2\",\"sha\":\"" + SHA + "\"}}}");
		try (FakeGitHub github = new FakeGitHub(archive())) {
			Path cache = this.directory.resolve("cache");
			TemplateSource.Resolved source = TemplateSource.resolve(config, null, null, cache,
					new GitHubReleases(github.url(), null), this.log::add);
			WorkflowVersions versions = WorkflowSupport.versions(local, source);
			assertThat(source.changed()).isFalse();
			assertThat(versions.action("a/b").ref()).isEqualTo("v2");
			assertThat(versions.action("c/d").ref()).isEqualTo("v1");
			assertThat(versions.action(REPOSITORY).ref()).isEqualTo("0.0.1");
			assertThat(WorkflowTemplates.project(source.directory())).containsOnlyKeys("workflows/ci.yml");
			TemplateSource.resolve(config, null, null, cache, new GitHubReleases(github.url(), null), this.log::add);
			assertThat(github.requests).filteredOn((request) -> request.contains("/zipball/")).hasSize(1);
			assertThat(this.log).anyMatch((line) -> line.startsWith("Downloading"))
				.anyMatch((line) -> line.startsWith("Using"));
			assertThat(TemplateSource.recorded(config, source)).isNull();
		}
	}

	@Test
	void resolveWhenNothingNamesTheTemplatesThenNothingIsResolved() {
		TemplateSource.Resolved source = TemplateSource.resolve(this.directory.resolve("workflow-generator.yml"), null,
				null, this.directory.resolve("cache"), new GitHubReleases("http://127.0.0.1:1", null), this.log::add);
		assertThat(source.directory()).isNull();
		assertThat(source.overlay().actions()).isEmpty();
	}

	@Test
	void resolveWhenNoTagInTheCommentThenSaysHowToSayIt() throws Exception {
		Path config = config("uses: " + REPOSITORY + "@" + SHA);
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.resolve(config, null, null, this.directory.resolve("cache"),
					new GitHubReleases("http://127.0.0.1:1", null), this.log::add))
			.withMessageContaining("which tag the commit is of")
			.withMessageContaining("# 1.2.3");
	}

	@Test
	void resolveWhenNotARepositoryAtACommitThenSaysWhatItShouldLookLike() throws Exception {
		Path config = config("uses: nothing # 1.2.3");
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.resolve(config, null, null, this.directory.resolve("cache"),
					new GitHubReleases("http://127.0.0.1:1", null), this.log::add))
			.withMessageContaining("owner/repo@<commit> # <tag>");
	}

	@Test
	void resolveWhenCommitIsNotWhollyThereThenSaysToHaveItFilledIn() throws Exception {
		Path config = config("uses: " + REPOSITORY + "@{SHA} # 0.0.36");
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.resolve(config, null, null, this.directory.resolve("cache"),
					new GitHubReleases("http://127.0.0.1:1", null), this.log::add))
			.withMessageContaining("has to name the whole commit")
			.withMessageContaining("--templates-version latest");
	}

	@Test
	void resolveWhenVersionAskedForThenAnUnusableUsesIsReplaced() throws Exception {
		Path config = Files.writeString(this.directory.resolve("workflow-generator.yml"),
				"workflow:\n  generator:\n    workflows:\n      release-tools:\n        uses: " + REPOSITORY
						+ "@{SHA}\n        docs-base-path: .\n");
		try (FakeGitHub github = new FakeGitHub(archive())) {
			TemplateSource.Resolved source = TemplateSource.resolve(config, null, "latest",
					this.directory.resolve("cache"), new GitHubReleases(github.url(), null), this.log::add);
			assertThat(TemplateSource.recorded(config, source))
				.isEqualTo("workflow:\n  generator:\n    workflows:\n      release-tools:\n        uses: " + REPOSITORY
						+ "@" + SHA + " # 0.0.36\n        docs-base-path: .\n");
		}
	}

	@Test
	void resolveWhenDirectoryGivenThenItIsUsedAsItIsAndCannotBeCombinedWithAVersion() {
		Path explicit = this.directory.resolve("mine");
		TemplateSource.Resolved source = TemplateSource.resolve(this.directory.resolve("workflow-generator.yml"),
				explicit, null, this.directory.resolve("cache"), null, this.log::add);
		assertThat(source.directory()).isEqualTo(explicit);
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.resolve(this.directory.resolve("workflow-generator.yml"), explicit,
					"latest", this.directory.resolve("cache"), null, this.log::add));
	}

	@Test
	void resolveWhenVersionAskedForThenItIsOverlaidAndRecordedUnderReleaseTools() throws Exception {
		Path config = Files.writeString(this.directory.resolve("workflow-generator.yml"),
				"workflow:\n  generator:\n    workflows:\n      # the tools\n      release-tools:\n"
						+ "        docs-base-path: .\n");
		try (FakeGitHub github = new FakeGitHub(archive())) {
			TemplateSource.Resolved source = TemplateSource.resolve(config, null, "0.0.36",
					this.directory.resolve("cache"), new GitHubReleases(github.url(), null), this.log::add);
			assertThat(source.changed()).isTrue();
			assertThat(source.overlay().action(REPOSITORY).ref()).isEqualTo("0.0.36");
			assertThat(config).content().doesNotContain("uses:");
			assertThat(TemplateSource.recorded(config, source))
				.isEqualTo("workflow:\n  generator:\n    workflows:\n      # the tools\n      release-tools:\n"
						+ "        uses: " + REPOSITORY + "@" + SHA + " # 0.0.36\n        docs-base-path: .\n");
		}
	}

	@Test
	void resolveWhenLatestThenTheLatestReleaseIsUsed() throws Exception {
		try (FakeGitHub github = new FakeGitHub(archive())) {
			TemplateSource.Resolved source = TemplateSource.resolve(this.directory.resolve("workflow-generator.yml"),
					null, "latest", this.directory.resolve("cache"), new GitHubReleases(github.url(), null),
					this.log::add);
			assertThat(source.pin().ref()).isEqualTo("0.0.36");
			assertThat(source.repository()).isEqualTo(REPOSITORY);
		}
	}

	@Test
	void recordedWhenThereIsAUsesThenOnlyThatLineChanges() throws Exception {
		Path config = config("uses: " + REPOSITORY + "@" + SHA + " # 0.0.1", "docs-base-path: .");
		String recorded = TemplateSource.recorded(config, new TemplateSource.Resolved(null, REPOSITORY,
				new WorkflowVersions.Action("0.0.37", "1".repeat(40)), true));
		assertThat(recorded)
			.isEqualTo(Files.readString(config).replace(SHA + " # 0.0.1", "1".repeat(40) + " # 0.0.37"));
	}

	@Test
	void recordedWhenThereIsNoReleaseToolsThenSaysWhatToAdd() throws Exception {
		Path config = Files.writeString(this.directory.resolve("workflow-generator.yml"),
				"workflow:\n  generator: {}\n");
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.recorded(config,
					new TemplateSource.Resolved(null, REPOSITORY, new WorkflowVersions.Action("0.0.37", SHA), true)))
			.withMessageContaining("nowhere to record")
			.withMessageContaining("uses: " + REPOSITORY + "@" + SHA + " # 0.0.37");
	}

	@Test
	void recordedWhenThereIsNoConfigurationThenSaysSo() {
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.recorded(this.directory.resolve("workflow-generator.yml"),
					new TemplateSource.Resolved(null, REPOSITORY, new WorkflowVersions.Action("0.0.37", SHA), true)))
			.withMessageContaining("does not exist");
	}

	@Test
	void resolveWhenArchiveHasEntryOutsideSpecsDirectoryThenFailsAndWritesNothingThere() throws Exception {
		Path local = pinned();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			entry(zip, "root/.github/specs/../../../../evil", "no\n");
		}
		try (FakeGitHub github = new FakeGitHub(bytes.toByteArray())) {
			Path cache = this.directory.resolve("cache");
			assertThatExceptionOfType(GradleException.class).isThrownBy(() -> TemplateSource.resolve(local, null, null,
					cache, new GitHubReleases(github.url(), null), this.log::add));
			assertThat(this.directory.resolve("evil")).doesNotExist();
		}
	}

	@Test
	void resolveWhenArchiveHasNoSpecsThenSaysSo() throws Exception {
		Path local = pinned();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			entry(zip, "root/README.md", "none\n");
		}
		try (FakeGitHub github = new FakeGitHub(bytes.toByteArray())) {
			assertThatExceptionOfType(GradleException.class)
				.isThrownBy(() -> TemplateSource.resolve(local, null, null, this.directory.resolve("cache"),
						new GitHubReleases(github.url(), null), this.log::add))
				.withMessageContaining("nothing under .github/specs");
		}
	}

	@Test
	void checkoutWhenGivenThenTagsAndSpecsComeFromIt() throws Exception {
		Path checkout = repository();
		Path local = this.directory.resolve("workflow-generator.yml");
		Path cache = this.directory.resolve("cache");
		TemplateRepository repository = TemplateSource.repository(null, checkout, null);
		TemplateSource.Resolved source = TemplateSource.resolve(local, null, "0.0.1", cache, repository, this.log::add);
		assertThat(source.pin().sha()).matches("[0-9a-f]{40}");
		assertThat(WorkflowTemplates.project(source.directory())).containsOnlyKeys("workflows/ci.yml");
		Files.writeString(checkout.resolve("README.md"), "two\n");
		git(checkout, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-qam", "two");
		git(checkout, "tag", "0.0.2");
		git(checkout, "tag", "not-a-version");
		assertThat(repository.latestVersion(REPOSITORY)).isEqualTo("0.0.2");
		assertThat(repository.tagCommit(REPOSITORY, "0.0.2")).isNotEqualTo(source.pin().sha());
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.resolve(local, null, "9.9.9", cache, repository, this.log::add))
			.withMessageContaining("9.9.9");
	}

	@Test
	void checkoutWhenNotAGitCheckoutThenSaysSo() throws Exception {
		Path notGit = Files.createDirectories(this.directory.resolve("plain"));
		assertThatExceptionOfType(GradleException.class).isThrownBy(() -> new LocalCheckout(notGit))
			.withMessageContaining("is not a git checkout");
	}

	@Test
	void repositoryWhenCheckoutAndDirectoryBothGivenThenFails() throws Exception {
		assertThatExceptionOfType(GradleException.class)
			.isThrownBy(() -> TemplateSource.repository(this.directory, repository(), null))
			.withMessageContaining("cannot both be given");
	}

	@Test
	void latestWhenThereIsNoReleaseThenTheHighestTagOnAnyPageIsUsed() throws Exception {
		try (FakeGitHub github = new FakeGitHub(archive())) {
			github.latestStatus = 404;
			List<String> tags = new ArrayList<>();
			for (int i = 0; i < 100; i++) {
				tags.add("0.0." + i);
			}
			tags.add("not-a-version");
			tags.add("0.1.0");
			github.tags = tags;
			assertThat(new GitHubReleases(github.url(), null).latestVersion(REPOSITORY)).isEqualTo("0.1.0");
		}
	}

	@Test
	void latestWhenReleaseCannotBeReadForAnyOtherReasonThenFailsRatherThanReadTags() throws Exception {
		try (FakeGitHub github = new FakeGitHub(archive())) {
			github.latestStatus = 403;
			github.tags = List.of("0.1.0");
			assertThatExceptionOfType(IllegalStateException.class)
				.isThrownBy(() -> new GitHubReleases(github.url(), null).latestVersion(REPOSITORY))
				.withMessageContaining("returned 403");
		}
	}

	@Test
	void resolveWhenArchiveHasTooManyFilesThenFails() throws Exception {
		Path local = pinned();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (int i = 0; i <= 1000; i++) {
				entry(zip, "root/.github/specs/workflows/w" + i + ".spec.yml", "name: W\n");
			}
		}
		try (FakeGitHub github = new FakeGitHub(bytes.toByteArray())) {
			assertThatExceptionOfType(GradleException.class)
				.isThrownBy(() -> TemplateSource.resolve(local, null, null, this.directory.resolve("cache"),
						new GitHubReleases(github.url(), null), this.log::add))
				.withMessageContaining("more than 1000 files");
		}
	}

	@Test
	void compareWhenVersionsThenEachNumberIsComparedInTurn() {
		assertThat(TemplateRepository.compare("0.0.10", "0.0.9")).isPositive();
		assertThat(TemplateRepository.compare("v1.0", "1.0.0")).isZero();
		assertThat(TemplateRepository.compare("1.2", "1.10")).isNegative();
	}

	private Path pinned() throws IOException {
		return config("uses: " + REPOSITORY + "@" + SHA + " # 0.0.1");
	}

	private Path config(String... releaseTools) throws IOException {
		StringBuilder yaml = new StringBuilder("workflow:\n  generator:\n    workflows:\n      release-tools:\n");
		for (String line : releaseTools) {
			yaml.append("        ").append(line).append("\n");
		}
		return Files.writeString(this.directory.resolve("workflow-generator.yml"), yaml);
	}

	private Path versions(String json) throws IOException {
		Path file = this.directory.resolve("versions.json");
		return Files.writeString(file, json);
	}

	private Path repository() throws Exception {
		Path checkout = Files.createDirectories(this.directory.resolve("actions"));
		git(checkout, "init", "-q");
		Files.createDirectories(checkout.resolve(".github/specs/workflows"));
		Files.writeString(checkout.resolve(".github/specs/workflows/ci.spec.yml"), "name: CI\n");
		Files.writeString(checkout.resolve("README.md"), "one\n");
		git(checkout, "add", ".");
		git(checkout, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-qm", "one");
		git(checkout, "tag", "0.0.1");
		return checkout;
	}

	private static void git(Path directory, String... arguments) throws Exception {
		String[] command = new String[arguments.length + 1];
		command[0] = "git";
		System.arraycopy(arguments, 0, command, 1, arguments.length);
		Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor()).as(output).isZero();
	}

	private static byte[] archive() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
				entry(zip, "spring-io-spring-security-actions-0123456/.github/specs/workflows/ci.spec.yml",
						"name: CI\n");
				entry(zip, "spring-io-spring-security-actions-0123456/.github/specs/versions.json",
						"{\"actions\":{\"a/b\":{\"ref\":\"v1\",\"sha\":\"" + SHA
								+ "\"},\"c/d\":{\"ref\":\"v1\",\"sha\":\"" + SHA + "\"}}}");
				entry(zip, "spring-io-spring-security-actions-0123456/README.md", "not a spec\n");
			}
			return bytes.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

}
