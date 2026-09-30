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
import java.net.URI;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DeploymentRepositoryConventions}.
 *
 * @author Josh Cummings
 */
public class DeploymentRepositoryConventionsTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
	}

	@Test
	public void applyWhenUrlUnsetThenNoRepositoryAdded() {
		apply(Map.of());

		assertThat(this.project.getRepositories()).isEmpty();
	}

	@Test
	public void applyWhenCredentialsSpecifiedThenNameUrlAndCredentialsSet() {
		apply(Map.of("RELEASE_TRAIN_MAVEN_REPOSITORY_URL", "https://example.com/release-train",
				"RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME", "user", "RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD",
				"password"));

		MavenArtifactRepository repository = (MavenArtifactRepository) this.project.getRepositories()
			.getByName("spring-staged-deployment");
		assertThat(repository.getUrl()).isEqualTo(URI.create("https://example.com/release-train"));
		assertThat(repository.getCredentials().getUsername()).isEqualTo("user");
		assertThat(repository.getCredentials().getPassword()).isEqualTo("password");
	}

	@Test
	public void applyWhenOnlyUsernameSpecifiedThenNoCredentialsSet() {
		apply(Map.of("RELEASE_TRAIN_MAVEN_REPOSITORY_URL", "https://example.com/release-train",
				"RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME", "user"));

		MavenArtifactRepository repository = (MavenArtifactRepository) this.project.getRepositories()
			.getByName("spring-staged-deployment");
		assertThat(repository.getCredentials().getUsername()).isNull();
	}

	private void apply(Map<String, String> env) {
		new DeploymentRepositoryConventions().apply(this.project, env::get);
	}

}
