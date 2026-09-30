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

package io.spring.gradle.plugin.maven;

import java.io.File;
import java.net.URI;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringDeploymentRepositoryPublishPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringDeploymentRepositoryPublishPluginTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
		this.project.getPluginManager().apply(MavenPublishPlugin.class);
	}

	@Test
	public void applyWhenDeploymentRepositoryUnspecifiedThenNoDeploymentRepository() {
		this.project.getPluginManager().apply(SpringDeploymentRepositoryPublishPlugin.class);

		assertThat(publishingRepositories(this.project).findByName("deployment")).isNull();
	}

	@Test
	public void applyWhenDeploymentRepositorySpecifiedThenAddsDeploymentRepository() {
		this.project.getExtensions().getExtraProperties().set("deploymentRepository", "https://example.com/deployment");
		this.project.getPluginManager().apply(SpringDeploymentRepositoryPublishPlugin.class);

		MavenArtifactRepository repository = (MavenArtifactRepository) publishingRepositories(this.project)
			.getByName("deployment");
		assertThat(repository.getUrl()).isEqualTo(URI.create("https://example.com/deployment"));
	}

	@Test
	public void applyWhenMavenPublishPluginNotAppliedThenNoDeploymentRepository() {
		Project otherProject = ProjectBuilder.builder().build();
		otherProject.getExtensions().getExtraProperties().set("deploymentRepository", "https://example.com/deployment");
		otherProject.getPluginManager().apply(SpringDeploymentRepositoryPublishPlugin.class);

		assertThat(otherProject.getExtensions().findByType(PublishingExtension.class)).isNull();
	}

	private static RepositoryHandler publishingRepositories(Project project) {
		return project.getExtensions().getByType(PublishingExtension.class).getRepositories();
	}

}
