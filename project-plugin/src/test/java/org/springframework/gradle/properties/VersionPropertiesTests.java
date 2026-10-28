/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.gradle.properties;

import java.io.File;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link VersionProperties}.
 *
 * @author Josh Cummings
 */
public class VersionPropertiesTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
	}

	@Test
	public void getWhenUnsetThenDefault() {
		assertThat(VersionProperties.get(this.project, "toolVersion", "1.0.0")).isEqualTo("1.0.0");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  " })
	public void getWhenBlankThenDefault(String value) {
		set(value);

		assertThat(VersionProperties.get(this.project, "toolVersion", "1.0.0")).isEqualTo("1.0.0");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2.3.4", " 2.3.4 ", "10.12.1", "1.0.0-RC1", "1.0.0-SNAPSHOT", "0.0.42" })
	public void getWhenValidThenTrimmedValue(String value) {
		set(value);

		assertThat(VersionProperties.get(this.project, "toolVersion", "1.0.0")).isEqualTo(value.trim());
	}

	@ParameterizedTest
	@ValueSource(strings = { "1.0.0:evil", "1.0.0@jar", "../1.0", "1.0 0", "-1.0", "1.0.0;rm", "[1.0,)", "1.+" })
	public void getWhenInvalidThenFails(String value) {
		set(value);

		assertThatThrownBy(() -> VersionProperties.get(this.project, "toolVersion", "1.0.0"))
			.isInstanceOf(GradleException.class)
			.hasMessageContaining("toolVersion");
	}

	private void set(String value) {
		this.project.getExtensions().getExtraProperties().set("toolVersion", value);
	}

}
