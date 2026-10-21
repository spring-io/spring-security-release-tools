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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks that workflows and actions are pinned and, where they are rendered from a spec,
 * that they match it. Works offline, so it is safe to run in CI.
 * <p>
 * Workflows and actions that the workflow generator writes, which are named
 * {@code release-train-*}, are left alone: they are verified by the generator's rules,
 * not these.
 *
 * @author Josh Cummings
 */
final class WorkflowVerifier {

	static final String SPEC_SUFFIX = ".template";

	/**
	 * What the workflow generator names the workflows and actions it writes.
	 */
	static final String GENERATED_PREFIX = "release-train-";

	private static final Pattern PINNED = Pattern.compile("[^@\\s]+@[0-9a-f]{40}");

	private static final Pattern COMMENT = Pattern.compile("\\s*#\\s*\\S.*");

	private WorkflowVerifier() {
	}

	/**
	 * Report every {@code uses:} under {@code directory} that is not pinned to a SHA with
	 * a trailing comment naming the version, or whose SHA is not the one the manifest has
	 * for the version the comment names. A version the manifest doesn't know can't be
	 * checked offline, so it is only required to be pinned and commented.
	 * @param directory a workflows or actions directory; ignored if absent
	 * @param versions the manifest
	 * @return the problems found, empty if none
	 */
	static List<String> verifyPins(Path directory, WorkflowVersions versions) {
		List<String> problems = new ArrayList<>();
		for (Path file : files(directory)) {
			String[] lines = read(file).split("\n", -1);
			for (int i = 0; i < lines.length; i++) {
				Matcher uses = WorkflowRenderer.USES.matcher(lines[i]);
				if (uses.matches()) {
					String problem = pinProblem(uses.group(3), uses.group(4), versions);
					if (problem != null) {
						problems.add(file + ":" + (i + 1) + ": " + problem);
					}
				}
			}
		}
		return problems;
	}

	private static String pinProblem(String reference, String comment, WorkflowVersions versions) {
		if (reference.startsWith("./") || reference.startsWith("docker://")) {
			return null;
		}
		if (!PINNED.matcher(reference).matches()) {
			return "'" + reference + "' is not pinned to a commit SHA";
		}
		if (comment == null || !COMMENT.matcher(comment).matches()) {
			return "'" + reference + "' has no trailing '# <version>' comment";
		}
		int at = reference.lastIndexOf('@');
		WorkflowVersions.Action action = versions.action(WorkflowRenderer.repository(reference.substring(0, at)));
		String ref = comment.replaceFirst("^\\s*#\\s*", "").split("\\s+")[0];
		if (action != null && action.ref().equals(ref) && !action.sha().equals(reference.substring(at + 1))) {
			return "'" + reference + "' says " + ref + ", which the version manifest has as " + action.sha();
		}
		return null;
	}

	/**
	 * Report every template whose rendering differs from the workflow checked in
	 * alongside it. A template that renders to nothing means there should be no workflow.
	 * @param templates the templates, keyed by the workflow each renders to
	 * @param workflows the directory holding the rendered workflows
	 * @param renderer the renderer to use
	 * @return the problems found, empty if none
	 */
	static List<String> verifyRendered(Map<String, WorkflowTemplates.Template> templates, Path workflows,
			WorkflowRenderer renderer) {
		List<String> problems = new ArrayList<>();
		templates.forEach((name, template) -> {
			Path workflow = workflows.resolve(name);
			try {
				boolean exists = Files.exists(workflow);
				String expected = renderer.render(template.content());
				if (expected.isBlank()) {
					if (exists) {
						problems.add(workflow + " should not exist, as " + template.source()
								+ " renders to nothing; run updateWorkflows");
					}
				}
				else if (!exists) {
					problems.add(workflow + " is missing; it is rendered from " + template.source());
				}
				else if (!expected.equals(read(workflow))) {
					problems.add(workflow + " is out of date with " + template.source() + "; run updateWorkflows");
				}
			}
			catch (IllegalStateException ex) {
				problems.add(template.source() + ": " + ex.getMessage().replace("\n", "\n" + template.source() + ": "));
			}
		});
		return problems;
	}

	private static List<Path> files(Path directory) {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(directory)) {
			return files.filter(Files::isRegularFile)
				.filter((file) -> !generated(directory.relativize(file)))
				.filter((file) -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml"))
				.sorted()
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static boolean generated(Path relative) {
		return relative.getName(0).toString().startsWith(GENERATED_PREFIX);
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
