/*
 * Copyright 2002-2026 the original author or authors.
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

package org.springframework.gradle.repository;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringRepositoryPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringRepositoryPluginTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
	}

	@Test
	public void applyWhenReleaseThenMavenCentralAndReleaseRepository() {
		evaluate("1.0.0");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-release");
	}

	@Test
	public void applyWhenMilestoneThenIncludesMilestoneRepository() {
		evaluate("1.0.0-M1");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-milestone", "spring-oss-release");
	}

	@Test
	public void applyWhenReleaseCandidateThenIncludesMilestoneRepository() {
		evaluate("1.0.0-RC1");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-milestone", "spring-oss-release");
	}

	@Test
	public void applyWhenSnapshotThenIncludesSnapshotRepository() {
		evaluate("1.0.0-SNAPSHOT");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-snapshot", "spring-oss-milestone",
				"spring-oss-release");
	}

	@Test
	public void applyWhenReleaseAndForceMilestoneThenIncludesMilestoneRepository() {
		force("milestone");
		evaluate("1.0.0");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-milestone", "spring-oss-release");
	}

	@Test
	public void applyWhenReleaseAndForceSnapshotThenIncludesSnapshotRepository() {
		force("snapshot");
		evaluate("1.0.0");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-snapshot", "spring-oss-milestone",
				"spring-oss-release");
	}

	@Test
	public void applyWhenSnapshotAndForceReleaseThenExcludesSnapshotRepository() {
		force("release");
		evaluate("1.0.0-SNAPSHOT");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-release");
	}

	@Test
	public void applyWhenForceLocalThenMavenLocalIsFirst() {
		force("local");
		evaluate("1.0.0");

		assertThat(names()).containsExactly("MavenLocal", "MavenRepo", "spring-oss-release");
	}

	@Test
	public void applyWhenForceMilestoneAndLocalThenIncludesBoth() {
		force("milestone,local");
		evaluate("1.0.0");

		assertThat(names()).containsExactly("MavenLocal", "MavenRepo", "spring-oss-milestone", "spring-oss-release");
	}

	@Test
	public void addRepositoriesWhenReleaseTrainAndLtsSnapshotThenReleaseTrainIsAfterCentralAndInternalIsLast() {
		this.project.setVersion("1.0.0-SNAPSHOT");
		this.project.getExtensions().getExtraProperties().set("releaseChannel", "lts");
		this.project.getPlugins()
			.apply(SpringRepositoryPlugin.class)
			.addRepositories(this.project,
					Map.of("RELEASE_TRAIN_MAVEN_REPOSITORY_URL", "https://example.com/release-train")::get);

		assertThat(names()).containsExactly("MavenRepo", "spring-staged-deployment", "spring-oss-snapshot",
				"spring-oss-milestone", "spring-oss-release", "spring-internal-release", "spring-lts-release",
				"spring-lts-snapshot");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1.0.0.M1", "1.0.0.RC2", "1.0.0-M3", "1.0.0-RC10" })
	public void applyWhenMilestoneVersionFormatThenIncludesMilestoneRepository(String version) {
		evaluate(version);

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-milestone", "spring-oss-release");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1.0.0", "1.0.0-MYFEATURE", "1.0.0-RELEASE", "2.0.0-MAIN", "1.0.0-RCA" })
	public void applyWhenNotMilestoneVersionThenExcludesMilestoneRepository(String version) {
		evaluate(version);

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-release");
	}

	@Test
	public void applyWhenSnapshotAndForceReleaseThenExcludesMilestoneRepository() {
		force("release");
		evaluate("1.0.0-SNAPSHOT");

		assertThat(names()).containsExactly("MavenRepo", "spring-oss-release");
	}

	private void force(String repositories) {
		this.project.getExtensions().getExtraProperties().set("forceMavenRepositories", repositories);
	}

	private void evaluate(String version) {
		this.project.setVersion(version);
		this.project.getPluginManager().apply(SpringRepositoryPlugin.class);
		((ProjectInternal) this.project).evaluate();
	}

	private List<String> names() {
		return StreamSupport.stream(this.project.getRepositories().spliterator(), false)
			.map(ArtifactRepository::getName)
			.toList();
	}

}
