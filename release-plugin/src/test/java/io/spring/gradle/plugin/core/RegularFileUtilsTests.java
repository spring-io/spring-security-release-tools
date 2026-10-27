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

package io.spring.gradle.plugin.core;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link RegularFileUtils}.
 */
class RegularFileUtilsTests {

	@TempDir
	File projectDir;

	@Test
	void mkdirsWhenDirectoryAlreadyExistsThenDoesNotFail() {
		Project project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
		RegularFileUtils.mkdirs(project.getLayout().getBuildDirectory());
		assertThatNoException().isThrownBy(() -> RegularFileUtils.mkdirs(project.getLayout().getBuildDirectory()));
		assertThat(new File(this.projectDir, "build")).isDirectory();
	}

}
