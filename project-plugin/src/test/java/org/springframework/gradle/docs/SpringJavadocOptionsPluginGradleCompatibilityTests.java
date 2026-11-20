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
package org.springframework.gradle.docs;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that {@link SpringJavadocOptionsPlugin} works on the oldest Gradle versions that
 * the projects that use it still build with, and not only on the Gradle that builds this
 * project.
 */
public class SpringJavadocOptionsPluginGradleCompatibilityTests {

	private static final String CURRENT = "current";

	@TempDir
	private File projectDir;

	@ParameterizedTest
	@ValueSource(strings = { "7.4.2", "8.2.1", CURRENT })
	public void applyWhenGradleVersionThenConfiguresJavadocOptions(String gradleVersion)
			throws IOException, URISyntaxException {
		assumeTrue(isSupportedByRunningJdk(gradleVersion),
				"Gradle " + gradleVersion + " cannot run on JDK " + Runtime.version().feature());
		Files.writeString(new File(this.projectDir, "settings.gradle").toPath(), "rootProject.name = 'sample'\n");
		File pluginClasses = new File(
				SpringJavadocOptionsPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Files.writeString(new File(this.projectDir, "build.gradle").toPath(), """
				buildscript {
					dependencies {
						classpath files('%s')
					}
				}
				plugins {
					id 'java'
				}
				apply plugin: org.springframework.gradle.docs.SpringJavadocOptionsPlugin
				tasks.register('writeJavadocOptions') {
					doLast {
						javadoc.options.write(file('javadoc.options'))
					}
				}
				""".formatted(pluginClasses.getAbsolutePath().replace("\\", "/")));
		GradleRunner runner = GradleRunner.create()
			.withProjectDir(this.projectDir)
			.withArguments("writeJavadocOptions", "--stacktrace");
		if (!CURRENT.equals(gradleVersion)) {
			runner.withGradleVersion(gradleVersion);
		}

		BuildResult result = runner.build();

		assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
		String options = Files.readString(new File(this.projectDir, "javadoc.options").toPath());
		assertThat(options).contains("-Xdoclint:none");
		if (Runtime.version().feature() >= 23) {
			assertThat(options).contains("--no-fonts");
		}
		else {
			assertThat(options).doesNotContain("no-fonts");
		}
	}

	/**
	 * Gradle only runs on the JDK versions that it knew about when it was released.
	 */
	private static boolean isSupportedByRunningJdk(String gradleVersion) {
		int jdk = Runtime.version().feature();
		return switch (gradleVersion) {
			case "7.4.2" -> jdk <= 17;
			case "8.2.1" -> jdk <= 19;
			default -> true;
		};
	}

}
