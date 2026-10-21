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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkflowVerifier}.
 */
class WorkflowVerifierTests {

	private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

	@TempDir
	Path directory;

	@Test
	void verifyPinsWhenPinnedWithCommentThenNoProblems() throws IOException {
		write("workflows/a.yml",
				"steps:\n  - uses: \"actions/checkout@" + SHA + "\" # v7\n  - uses: ./.github/actions/x\n");
		assertThat(WorkflowVerifier.verifyPins(this.directory.resolve("workflows"), versions())).isEmpty();
	}

	@Test
	void verifyPinsWhenBranchOrTagThenReported() throws IOException {
		write("workflows/a.yml", "steps:\n  - uses: a/b@main # TODO\n  - uses: c/d@v1\n");
		assertThat(WorkflowVerifier.verifyPins(this.directory.resolve("workflows"), versions())).hasSize(2)
			.anyMatch((problem) -> problem.contains("a.yml:2") && problem.contains("a/b@main"))
			.anyMatch((problem) -> problem.contains("a.yml:3") && problem.contains("c/d@v1"));
	}

	@Test
	void verifyPinsWhenNoCommentThenReported() throws IOException {
		write("actions/b.yml", "  - uses: a/b@" + SHA + "\n");
		assertThat(WorkflowVerifier.verifyPins(this.directory.resolve("actions"), versions())).hasSize(1)
			.allMatch((problem) -> problem.contains("comment"));
	}

	@Test
	void verifyPinsWhenDirectoryAbsentThenNoProblems() {
		assertThat(WorkflowVerifier.verifyPins(this.directory.resolve("nope"), versions())).isEmpty();
	}

	@Test
	void verifyRenderedWhenInSyncThenNoProblems() throws IOException {
		WorkflowRenderer renderer = renderer();
		write("specs/ci.yml.template", "uses: a/b\n");
		write("workflows/ci.yml", renderer.render("uses: a/b\n"));
		assertThat(WorkflowVerifier.verifyRendered(WorkflowTemplates.project(this.directory.resolve("specs")),
				this.directory.resolve("workflows"), renderer))
			.isEmpty();
	}

	@Test
	void verifyRenderedWhenStaleOrMissingThenReported() throws IOException {
		write("specs/ci.yml.template", "uses: a/b\n");
		write("specs/pr.yml.template", "uses: a/b\n");
		write("workflows/ci.yml", "uses: a/b@" + "f".repeat(40) + " # v0\n");
		assertThat(WorkflowVerifier.verifyRendered(WorkflowTemplates.project(this.directory.resolve("specs")),
				this.directory.resolve("workflows"), renderer()))
			.hasSize(2)
			.anyMatch((problem) -> problem.contains("ci.yml is out of date"))
			.anyMatch((problem) -> problem.contains("pr.yml is missing"));
	}

	@Test
	void verifyRenderedWhenSpecCannotBeRenderedThenReported() throws IOException {
		write("specs/ci.yml.template", "uses: unknown/repo\n");
		assertThat(WorkflowVerifier.verifyRendered(WorkflowTemplates.project(this.directory.resolve("specs")),
				this.directory.resolve("workflows"), renderer()))
			.singleElement()
			.satisfies((problem) -> assertThat(problem).contains("ci.yml.template").contains("unknown/repo"));
	}

	private static WorkflowRenderer renderer() {
		return new WorkflowRenderer(versions(), Map.of());
	}

	private void write(String path, String content) throws IOException {
		Path file = this.directory.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	@Test
	void verifyPinsWhenShaIsNotTheManifestsForTheCommentedVersionThenReported() throws IOException {
		write("workflows/a.yml", "uses: a/b@" + "f".repeat(40) + " # v1\nuses: a/b@" + "f".repeat(40) + " # v0\n"
				+ "uses: c/d@" + "f".repeat(40) + " # v1\nuses: a/b@" + SHA + " # v1 (pinned)\n");
		assertThat(WorkflowVerifier.verifyPins(this.directory.resolve("workflows"), versions())).singleElement()
			.satisfies((problem) -> assertThat(problem).contains("a.yml:1").contains(SHA));
	}

	@Test
	void verifyPinsWhenWorkflowIsGeneratedThenLeftAlone() throws IOException {
		write("workflows/release-train-build.yml", "uses: a/b@main\n");
		write("actions/release-train-build/action.yml", "uses: a/b@main\n");
		write("actions/mine/action.yml", "uses: a/b@main\n");
		assertThat(WorkflowVerifier.verifyPins(this.directory.resolve("workflows"), versions())).isEmpty();
		assertThat(WorkflowVerifier.verifyPins(this.directory.resolve("actions"), versions())).singleElement()
			.satisfies((problem) -> assertThat(problem).contains("mine").contains("a/b@main"));
	}

	@Test
	void verifyRenderedWhenTemplateRendersToNothingThenWorkflowMustBeAbsent() throws IOException {
		write("specs/ci.yml.template", "<<#x>>\nuses: a/b\n<</x>>\n");
		Map<String, WorkflowTemplates.Template> templates = WorkflowTemplates.project(this.directory.resolve("specs"));
		assertThat(WorkflowVerifier.verifyRendered(templates, this.directory.resolve("workflows"), renderer()))
			.isEmpty();
		write("workflows/ci.yml", "uses: a/b@" + SHA + " # v1\n");
		assertThat(WorkflowVerifier.verifyRendered(templates, this.directory.resolve("workflows"), renderer()))
			.singleElement()
			.satisfies((problem) -> assertThat(problem).contains("ci.yml should not exist"));
	}

	private static WorkflowVersions versions() {
		return new WorkflowVersions(null, Map.of("a/b", new WorkflowVersions.Action("v1", SHA)));
	}

}
