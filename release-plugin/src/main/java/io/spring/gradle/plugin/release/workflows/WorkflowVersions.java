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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The versions that workflows are pinned to. Every entry carries the commit SHA as well
 * as the ref it came from, so that pinning never needs network access. This plugin's own
 * entry is filled in when the plugin is built, from the version and commit it is built
 * from.
 *
 * @param generator the workflow generator that produces release train workflows, if any
 * @param actions the actions and reusable workflows, keyed by {@code owner/repo}
 * @author Josh Cummings
 */
record WorkflowVersions(Generator generator, Map<String, Action> actions) {

	private static final String SHIPPED = "versions.json";

	private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");

	private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

	private static final Pattern RELEASE = Pattern.compile("v\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.]+)?");

	WorkflowVersions {
		actions = (actions != null) ? Map.copyOf(actions) : Map.of();
	}

	/**
	 * Load the versions that ship with this plugin.
	 * @return the shipped versions
	 */
	static WorkflowVersions shipped() {
		try (InputStream in = WorkflowVersions.class.getResourceAsStream(SHIPPED)) {
			return load(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static WorkflowVersions load(InputStream in) throws IOException {
		return new ObjectMapper().readValue(in, WorkflowVersions.class);
	}

	/**
	 * Overlay a project's own versions on these. Entries in {@code local} win.
	 * @param local the project's versions
	 * @return the combined versions
	 */
	WorkflowVersions merge(WorkflowVersions local) {
		Map<String, Action> merged = new LinkedHashMap<>(this.actions);
		merged.putAll(local.actions);
		return new WorkflowVersions((local.generator != null) ? local.generator : this.generator, merged);
	}

	Action action(String repository) {
		return this.actions.get(repository);
	}

	/**
	 * @param version the generator release tag, e.g. {@code v0.0.7}. It names a directory
	 * and an asset, so it must look like a release
	 * @param sha256 the checksum of the release's jar
	 * @param java the Java version the generator runs on, or {@code null} for the default
	 */
	record Generator(String version, String sha256, Integer java) {

		static final int DEFAULT_JAVA = 25;

		Generator {
			if (version == null || !RELEASE.matcher(version).matches()) {
				throw new IllegalArgumentException(
						"generator version must be a release tag like v1.2.3, not " + version);
			}
			if (sha256 == null || !SHA_256.matcher(sha256).matches()) {
				throw new IllegalArgumentException("generator requires a 64-character sha256");
			}
		}

		int javaVersion() {
			return (this.java != null) ? this.java : DEFAULT_JAVA;
		}

	}

	/**
	 * @param ref the tag the SHA was resolved from, written as the trailing comment
	 * @param sha the full commit SHA of the tag
	 */
	record Action(String ref, String sha) {

		Action {
			if (ref == null || ref.isBlank() || sha == null || !SHA.matcher(sha).matches()) {
				throw new IllegalArgumentException("an action requires a ref and a 40-character lowercase sha");
			}
		}

	}

}
