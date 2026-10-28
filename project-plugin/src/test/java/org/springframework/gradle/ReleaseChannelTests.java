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

package org.springframework.gradle;

import java.io.File;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ReleaseChannel}.
 *
 * @author Josh Cummings
 */
public class ReleaseChannelTests {

	@TempDir
	private File projectDir;

	@Test
	public void fromProjectWhenUnsetThenNull() {
		assertThat(ReleaseChannel.from(project())).isNull();
	}

	@Test
	public void fromProjectWhenSetThenParsedIgnoringCaseAndWhitespace() {
		Project project = project();
		project.getExtensions().getExtraProperties().set("releaseChannel", " Lts ");

		assertThat(ReleaseChannel.from(project)).isEqualTo(ReleaseChannel.LTS);
	}

	@Test
	public void fromProjectWhenNotStringThenDoesNotThrowClassCastException() {
		Project project = project();
		project.getExtensions().getExtraProperties().set("releaseChannel", new StringBuilder("oss"));

		assertThat(ReleaseChannel.from(project)).isEqualTo(ReleaseChannel.OSS);
	}

	@Test
	public void fromProjectWhenInvalidThenFailsListingAllowedValues() {
		Project project = project();
		project.getExtensions().getExtraProperties().set("releaseChannel", "prod");

		assertThatThrownBy(() -> ReleaseChannel.from(project)).isInstanceOf(GradleException.class)
			.hasMessageContaining("Invalid value 'prod' for releaseChannel")
			.hasMessageContaining("oss, internal, hotfix, lts");
	}

	private Project project() {
		return ProjectBuilder.builder().withProjectDir(this.projectDir).build();
	}

}
