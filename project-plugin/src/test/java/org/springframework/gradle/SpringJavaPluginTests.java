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

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringJavaPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringJavaPluginTests {

	@TempDir
	private File projectDir;

	@Test
	public void applyWhenKotlinDslBuildFileAndNoKotlinSourcesThenKotlinPluginNotApplied() throws IOException {
		new File(this.projectDir, "src/main/java").mkdirs();
		File buildFile = new File(this.projectDir, "build.gradle.kts");
		assertThat(buildFile.createNewFile()).isTrue();
		Project project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
		project.getPluginManager().apply(SpringJavaPlugin.class);

		assertThat(project.getPlugins().hasPlugin("org.jetbrains.kotlin.jvm")).isFalse();
		assertThat(project.getPlugins().hasPlugin("java")).isTrue();
	}

}
