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

package io.spring.gradle.plugin.release.workflows;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.gradle.api.Project;

/**
 * A stand-in for the commit of spring-security-actions that a project pins, so that tests
 * need no network: its {@code .github/specs}, kept in the test resources, are placed in
 * the cache where the tasks look first.
 */
final class Fixtures {

	/**
	 * The pin of spring-security-actions in the fixture's own versions.
	 */
	static final WorkflowVersions.Action PIN = versions().action(TemplateSource.DEFAULT_REPOSITORY);

	private static final Path CACHE = cache();

	private Fixtures() {
	}

	static Path specs() {
		try {
			return Path.of(Fixtures.class.getResource("specs/versions.json").toURI()).getParent();
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @return the versions in the fixture, which are the base of a project's
	 */
	static WorkflowVersions versions() {
		try (InputStream in = Fixtures.class.getResourceAsStream("specs/versions.json")) {
			return WorkflowVersions.load(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * @return the fixture's templates, as the tasks see them
	 */
	static Map<String, WorkflowTemplates.Template> templates() {
		return WorkflowTemplates.project(specs());
	}

	/**
	 * Pin the fixture in the project, and have its tasks read it from the cache.
	 */
	static void pin(Project project, File projectDir) throws IOException {
		Path versions = projectDir.toPath().resolve(".github/specs/versions.json");
		Files.createDirectories(versions.getParent());
		Files.writeString(versions, pin());
		// configured when the tasks are, so as not to realize them before a test has
		// set what their conventions read
		project.getTasks()
			.named(UpdateWorkflowsTask.TASK_NAME, UpdateWorkflowsTask.class,
					(task) -> task.getTemplatesCacheDirectory().set(CACHE.toFile()));
		project.getTasks()
			.named(VerifyWorkflowsTask.TASK_NAME, VerifyWorkflowsTask.class,
					(task) -> task.getTemplatesCacheDirectory().set(CACHE.toFile()));
	}

	/**
	 * @return a project's versions file that names the repository that has the templates
	 * and pins the fixture
	 */
	static String pin() {
		return "{\"templates\": \"" + TemplateSource.DEFAULT_REPOSITORY + "\", \"actions\": {\""
				+ TemplateSource.DEFAULT_REPOSITORY + "\": {\"ref\": \"" + PIN.ref() + "\", \"sha\": \"" + PIN.sha()
				+ "\"}}}";
	}

	private static Path cache() {
		try {
			Path cache = Files.createTempDirectory("spring-security-actions");
			Path target = cache.resolve(TemplateSource.DEFAULT_REPOSITORY.replace('/', '-')).resolve(PIN.sha());
			Path source = specs();
			try (Stream<Path> files = Files.walk(source)) {
				for (Path file : files.toList()) {
					Path to = target.resolve(source.relativize(file).toString());
					if (Files.isDirectory(file)) {
						Files.createDirectories(to);
					}
					else {
						Files.copy(file, to);
					}
				}
			}
			cache.toFile().deleteOnExit();
			return cache;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
