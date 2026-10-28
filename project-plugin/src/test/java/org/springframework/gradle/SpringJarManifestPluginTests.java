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

package org.springframework.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringJarManifestPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringJarManifestPluginTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).withName("jar-manifest-test").build();
		this.project.setVersion("1.0.0");
		this.project.getPluginManager().apply("java");
		this.project.getPluginManager().apply(SpringJarManifestPlugin.class);
	}

	@Test
	public void applyWhenJarTaskExistsThenSetsManifestAttributes() {
		Jar jar = (Jar) this.project.getTasks().getByName("jar");
		var attributes = jar.getManifest().getAttributes();
		assertThat(attributes).containsEntry("Implementation-Title", "jar-manifest-test");
		assertThat(attributes).containsEntry("Implementation-Version", "1.0.0");
		assertThat(attributes).containsEntry("Automatic-Module-Name", "jar.manifest.test");
		assertThat(attributes).containsKey("Build-Jdk-Spec");
	}

	@Test
	public void applyWhenNoToolchainThenBuildJdkSpecIsRunningJdk() throws IOException {
		assertThat(manifest()).contains("Build-Jdk-Spec: " + System.getProperty("java.specification.version"));
	}

	@Test
	public void applyWhenToolchainConfiguredThenBuildJdkSpecIsToolchainVersion() throws IOException {
		JavaPluginExtension java = this.project.getExtensions().getByType(JavaPluginExtension.class);
		java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(11));

		assertThat(manifest()).contains("Build-Jdk-Spec: 11");
	}

	private String manifest() throws IOException {
		Jar jar = (Jar) this.project.getTasks().getByName("jar");
		File file = new File(this.projectDir, "MANIFEST.MF");
		jar.getManifest().writeTo(file);
		return Files.readString(file.toPath());
	}

}
