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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;

/**
 * Renders a workflow spec into a workflow: expands the spec as a
 * <a href="https://mustache.github.io/mustache.5.html">Mustache</a> template against the
 * project's configuration, then pins every {@code uses:} to a commit SHA.
 * <p>
 * The delimiters are {@code &lt;&lt;} and {@code &gt;&gt;} so that GitHub's own
 * expressions, which start with {@code ${{}, need no escaping. For example, {@code
 * &lt;&lt; a.b &gt;&gt;} is replaced by the value at {@code a.b}, and {@code
 * &lt;&lt;#a.b&gt;&gt;...&lt;&lt;/a.b&gt;&gt;} keeps its content only if {@code a.b} has
 * a value that isn't {@code false}. A value that is missing is an error, except as a
 * section, where it counts as {@code false}. Values are not escaped.
 * <p>
 * Pinning follows three rules:
 * <ol>
 * <li>no version: use the version in the manifest</li>
 * <li>a tag: use the SHA the manifest has for that tag</li>
 * <li>a SHA: leave it alone</li>
 * </ol>
 * Nothing is resolved over the network, so a ref the manifest doesn't know is an error.
 *
 * @author Josh Cummings
 */
final class WorkflowRenderer {

	/**
	 * Matches a {@code uses:} line. Groups: prefix, quote, reference, trailing comment.
	 */
	static final Pattern USES = Pattern.compile("^(\\s*(?:-\\s+)?uses:\\s*)([\"']?)([^\\s\"']+)\\2(\\s*#.*)?\\s*$");

	private static final Mustache.Compiler COMPILER = Mustache.compiler().withDelims("<< >>").escapeHTML(false);

	static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");

	private final WorkflowVersions versions;

	private final Map<String, Object> config;

	WorkflowRenderer(WorkflowVersions versions, Map<String, Object> config) {
		this.versions = versions;
		this.config = config;
	}

	String render(String spec) {
		return pin(substitute(spec));
	}

	String substitute(String spec) {
		try {
			return COMPILER.compile(spec).execute(this.config);
		}
		catch (MustacheException ex) {
			throw new IllegalStateException(ex.getMessage(), ex);
		}
	}

	String pin(String text) {
		List<String> problems = new ArrayList<>();
		String[] lines = text.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			Matcher uses = USES.matcher(lines[i]);
			if (!uses.matches()) {
				continue;
			}
			try {
				lines[i] = pinLine(lines[i], uses);
			}
			catch (IllegalStateException ex) {
				problems.add("line " + (i + 1) + ": " + ex.getMessage());
			}
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException(String.join("\n", problems));
		}
		return String.join("\n", lines);
	}

	private String pinLine(String line, Matcher uses) {
		String reference = uses.group(3);
		if (reference.startsWith("./") || reference.startsWith("docker://")) {
			return line;
		}
		int at = reference.lastIndexOf('@');
		String name = (at < 0) ? reference : reference.substring(0, at);
		String version = (at < 0) ? null : reference.substring(at + 1);
		if (version != null && SHA.matcher(version).matches()) {
			return line;
		}
		WorkflowVersions.Action action = this.versions.action(repository(name));
		if (action == null) {
			throw new IllegalStateException("'" + reference + "' is not in the version manifest. Add "
					+ repository(name) + " to .github/specs/versions.json");
		}
		if (version != null && !version.equals(action.ref())) {
			throw new IllegalStateException("'" + reference + "' asks for " + version + " but the version manifest has "
					+ action.ref() + ". Drop the version, or add " + version + " to .github/specs/versions.json");
		}
		return uses.group(1) + name + "@" + action.sha() + " # " + action.ref();
	}

	static String repository(String name) {
		String[] segments = name.split("/");
		return (segments.length < 2) ? name : segments[0] + "/" + segments[1];
	}

}
