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

package io.spring.gradle.plugin.module;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.gradle.repository.SpringRepositoryPlugin;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringProjectPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringProjectPluginTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
	}

	@Test
	public void applyWhenRootProjectThenAppliesRepositoryPlugin() {
		this.project.getPluginManager().apply(SpringProjectPlugin.class);

		assertThat(this.project.getPlugins().hasPlugin(SpringRepositoryPlugin.class)).isTrue();
	}

	@Test
	public void applyWhenRootProjectAndReleaseChannelIsLtsThenAddsInternalRepositories() {
		this.project.getExtensions().getExtraProperties().set("releaseChannel", "lts");
		this.project.getPluginManager().apply(SpringProjectPlugin.class);
		((ProjectInternal) this.project).evaluate();

		assertThat(names(this.project)).contains("spring-internal-release", "spring-lts-release");
	}

	@Test
	public void applyWhenRootProjectAndReleaseChannelIsOssThenNoInternalRepositories() {
		this.project.getExtensions().getExtraProperties().set("releaseChannel", "oss");
		this.project.getPluginManager().apply(SpringProjectPlugin.class);
		((ProjectInternal) this.project).evaluate();

		assertThat(names(this.project)).doesNotContain("spring-internal-release", "spring-lts-release");
	}

	private static Set<String> names(Project project) {
		return StreamSupport.stream(project.getRepositories().spliterator(), false)
			.map(ArtifactRepository::getName)
			.collect(Collectors.toSet());
	}

}
