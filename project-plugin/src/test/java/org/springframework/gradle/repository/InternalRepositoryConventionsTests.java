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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InternalRepositoryConventions}.
 *
 * @author Josh Cummings
 */
public class InternalRepositoryConventionsTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
	}

	@Test
	public void applyWhenReleaseChannelUnspecifiedThenNoInternalRepositories() {
		new InternalRepositoryConventions().apply(this.project);

		assertThat(names(this.project)).doesNotContain("spring-internal-release", "spring-lts-release",
				"spring-lts-snapshot");
	}

	@Test
	public void applyWhenReleaseChannelIsOssThenNoInternalRepositories() {
		givenReleaseChannel("oss");

		assertThat(names(this.project)).doesNotContain("spring-internal-release", "spring-lts-release",
				"spring-lts-snapshot");
	}

	@ParameterizedTest
	@ValueSource(strings = { "lts", "internal", "hotfix" })
	public void applyWhenReleaseChannelIsNotOssThenAddsReleaseRepositories(String releaseChannel) {
		givenReleaseChannel(releaseChannel);

		assertThat(names(this.project)).contains("spring-internal-release", "spring-lts-release");
	}

	@Test
	public void applyWhenReleaseChannelIsNotOssAndVersionIsReleaseThenNoSnapshotRepository() {
		this.project.setVersion("1.0.0");
		givenReleaseChannel("lts");

		assertThat(names(this.project)).doesNotContain("spring-lts-snapshot");
	}

	@Test
	public void applyWhenReleaseChannelIsNotOssAndVersionIsSnapshotThenAddsSnapshotRepository() {
		this.project.setVersion("1.0.0-SNAPSHOT");
		givenReleaseChannel("lts");

		assertThat(names(this.project)).contains("spring-lts-snapshot");
	}

	@Test
	public void applyWhenArtifactoryPropertiesPresentThenCredentialsUsed() {
		this.project.getExtensions().getExtraProperties().set("artifactoryUsername", "user");
		this.project.getExtensions().getExtraProperties().set("artifactoryPassword", "password");
		givenReleaseChannel("lts");

		MavenArtifactRepository repository = mavenRepository(this.project, "spring-internal-release");
		assertThat(repository.getCredentials().getUsername()).isEqualTo("user");
		assertThat(repository.getCredentials().getPassword()).isEqualTo("password");
	}

	private void givenReleaseChannel(String releaseChannel) {
		this.project.getExtensions().getExtraProperties().set("releaseChannel", releaseChannel);
		new InternalRepositoryConventions().apply(this.project);
	}

	private static Set<String> names(Project project) {
		return StreamSupport.stream(project.getRepositories().spliterator(), false)
			.map(ArtifactRepository::getName)
			.collect(Collectors.toSet());
	}

	private static MavenArtifactRepository mavenRepository(Project project, String name) {
		return (MavenArtifactRepository) project.getRepositories().getByName(name);
	}

}
