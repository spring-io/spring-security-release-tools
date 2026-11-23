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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.gradle.api.GradleException;

/**
 * The templates to render, keyed by where each renders to, relative to the
 * {@code .github} directory (for example {@code workflows/ci.yml} or
 * {@code actions/release-train-build/action.yml}).
 * <p>
 * The default templates are those of the repository that a project's versions file names
 * (see {@link TemplateSource}), {@code spring-io/spring-security-actions}, which apply to
 * a project that is managed by the workflow generator, that is, one with a
 * {@code workflow-generator.yml}. A project replaces a default by having a template of
 * the same name, either {@code .github/specs/workflows/<workflow>.spec.yml} or
 * {@code .github/specs/actions/<action>/action.spec.yml}, and adds a workflow or action
 * by having a template that has no default.
 *
 * @author Josh Cummings
 */
final class WorkflowTemplates {

	private static final String WORKFLOWS = "workflows";

	private static final String ACTIONS = "actions";

	private static final String ACTION_TEMPLATE = "action" + WorkflowVerifier.SPEC_SUFFIX;

	private WorkflowTemplates() {
	}

	/**
	 * A template and where it came from, for error messages.
	 *
	 * @param source where the template was read from
	 * @param content the template
	 */
	record Template(String source, String content) {
	}

	/**
	 * @param specs the project's {@code .github/specs} directory, which need not exist
	 * @param defaults whether the default templates apply, which they do to a project
	 * that is managed by the workflow generator
	 * @param defaultSpecs the {@code .github/specs} directory of the default templates,
	 * or {@code null} if the project has none
	 * @return the default templates, if they apply, replaced or added to by the project's
	 */
	static Map<String, Template> load(Path specs, boolean defaults, Path defaultSpecs) {
		Map<String, Template> templates = new TreeMap<>();
		if (defaults) {
			if (defaultSpecs == null) {
				throw new GradleException("This project says it uses the default workflows, with a"
						+ " workflow-generator.yml, but not where their templates are. Run updateWorkflows with"
						+ " --templates-version latest to use those of the latest release of "
						+ TemplateSource.DEFAULT_REPOSITORY
						+ ", which is then recorded in .github/specs/versions.json");
			}
			templates.putAll(project(defaultSpecs));
		}
		templates.putAll(project(specs));
		return templates;
	}

	/**
	 * @param specs the project's {@code .github/specs} directory, which need not exist
	 * @return only the project's own templates
	 */
	static Map<String, Template> project(Path specs) {
		Map<String, Template> templates = new TreeMap<>();
		try {
			for (Path file : list(specs.resolve(WORKFLOWS), Files::isRegularFile)) {
				String name = file.getFileName().toString();
				if (name.endsWith(WorkflowVerifier.SPEC_SUFFIX)) {
					templates.put(key(WORKFLOWS + "/" + name), template(file));
				}
			}
			for (Path directory : list(specs.resolve(ACTIONS), Files::isDirectory)) {
				Path file = directory.resolve(ACTION_TEMPLATE);
				if (Files.isRegularFile(file)) {
					templates.put(key(ACTIONS + "/" + directory.getFileName() + "/" + ACTION_TEMPLATE), template(file));
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return templates;
	}

	private static Template template(Path file) throws IOException {
		return new Template(file.toString(), Files.readString(file));
	}

	private static List<Path> list(Path directory, Predicate<Path> filter) throws IOException {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(filter).toList();
		}
	}

	private static String key(String template) {
		return template.substring(0, template.length() - WorkflowVerifier.SPEC_SUFFIX.length()) + ".yml";
	}

}
