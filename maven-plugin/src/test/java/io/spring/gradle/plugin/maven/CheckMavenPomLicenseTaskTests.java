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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CheckMavenPomLicenseTask}.
 *
 * @author Josh Cummings
 */
public class CheckMavenPomLicenseTaskTests {

	private static final String APACHE_LICENSE_TEXT = """


			Apache License
			Version 2.0, January 2004
			https://www.apache.org/licenses/

			TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
			""";

	private static final String NON_APACHE_LICENSE_TEXT = """
			Broadcom Foundation Agreement

			This is a different license entirely.
			""";

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).build();
	}

	@Test
	public void checkPomLicenseWhenApacheLicenseAndPomMatchesThenSucceeds() throws Exception {
		var task = createTask(APACHE_LICENSE_TEXT,
				pomWithLicense("Apache License, Version 2.0", "https://www.apache.org/licenses/LICENSE-2.0"));
		assertThatCode(task::checkPomLicense).doesNotThrowAnyException();
	}

	@Test
	public void checkPomLicenseWhenApacheLicenseAndPomHasNoLicensesThenFails() throws Exception {
		var task = createTask(APACHE_LICENSE_TEXT, "<project></project>");
		assertThatThrownBy(task::checkPomLicense).isInstanceOf(GradleException.class)
			.hasMessageContaining("no <license> elements");
	}

	@Test
	public void checkPomLicenseWhenApacheLicenseAndPomLicenseNameIsWrongThenFails() throws Exception {
		var task = createTask(APACHE_LICENSE_TEXT,
				pomWithLicense("Broadcom Foundation License", "https://www.apache.org/licenses/LICENSE-2.0"));
		assertThatThrownBy(task::checkPomLicense).isInstanceOf(GradleException.class)
			.hasMessageContaining("Broadcom Foundation License")
			.hasMessageContaining("Apache License, Version 2.0");
	}

	@Test
	public void checkPomLicenseWhenApacheLicenseAndPomLicenseUrlIsWrongThenFails() throws Exception {
		var task = createTask(APACHE_LICENSE_TEXT,
				pomWithLicense("Apache License, Version 2.0", "https://example.com/wrong"));
		assertThatThrownBy(task::checkPomLicense).isInstanceOf(GradleException.class)
			.hasMessageContaining("https://example.com/wrong");
	}

	@Test
	public void checkPomLicenseWhenApacheLicenseAndPomHasMultipleLicensesOneMatchingThenSucceeds() throws Exception {
		// @formatter:off
		var pom = """
				<project>
					<licenses>
						<license>
							<name>Eclipse Public License</name>
							<url>https://www.eclipse.org/legal/epl-2.0/</url>
						</license>
						<license>
							<name>Apache License, Version 2.0</name>
							<url>https://www.apache.org/licenses/LICENSE-2.0</url>
						</license>
					</licenses>
				</project>
				""";
		// @formatter:on
		var task = createTask(APACHE_LICENSE_TEXT, pom);
		assertThatCode(task::checkPomLicense).doesNotThrowAnyException();
	}

	@Test
	public void checkPomLicenseWhenLicenseFileIsNotApacheThenSucceedsRegardlessOfPom() throws Exception {
		var task = createTask(NON_APACHE_LICENSE_TEXT, "<project></project>");
		assertThatCode(task::checkPomLicense).doesNotThrowAnyException();
	}

	@Test
	public void checkPomLicenseWhenLicenseFileDoesNotExistThenSucceedsRegardlessOfPom() throws Exception {
		var task = this.project.getTasks().register("checkPomLicense", CheckMavenPomLicenseTask.class).get();
		task.getLicenseFile().set(new File(this.projectDir, "does-not-exist/LICENSE.txt"));
		task.getPomFile().set(writeFile("pom.xml", "<project></project>"));
		task.getPublicationName().set("mavenJava");

		assertThatCode(task::checkPomLicense).doesNotThrowAnyException();
	}

	@Test
	public void checkPomLicenseWhenApacheLicenseAndPomFileDoesNotExistThenFails() throws Exception {
		var task = this.project.getTasks().register("checkPomLicense", CheckMavenPomLicenseTask.class).get();
		task.getLicenseFile().set(writeFile("LICENSE.txt", APACHE_LICENSE_TEXT));
		task.getPomFile().set(new File(this.projectDir, "does-not-exist/pom.xml"));
		task.getPublicationName().set("mavenJava");

		assertThatThrownBy(task::checkPomLicense).isInstanceOf(GradleException.class)
			.hasMessageContaining("Generated POM file does not exist");
	}

	private CheckMavenPomLicenseTask createTask(String licenseText, String pomXml) throws Exception {
		var task = this.project.getTasks().register("checkPomLicense", CheckMavenPomLicenseTask.class).get();
		task.getLicenseFile().set(writeFile("LICENSE.txt", licenseText));
		task.getPomFile().set(writeFile("pom.xml", pomXml));
		task.getPublicationName().set("mavenJava");
		return task;
	}

	private String pomWithLicense(String name, String url) {
		// @formatter:off
		return """
				<project>
					<licenses>
						<license>
							<name>%s</name>
							<url>%s</url>
						</license>
					</licenses>
				</project>
				""".formatted(name, url);
		// @formatter:on
	}

	private File writeFile(String name, String content) throws Exception {
		Path path = this.projectDir.toPath().resolve(name);
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path.toFile();
	}

}
