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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * What {@link VerifyWorkflowsTask} and {@link UpdateWorkflowsTask} both need to read from
 * a project.
 *
 * @author Josh Cummings
 */
final class WorkflowSupport {

	/**
	 * The templates that render from what {@link #generate} adds to the model, which
	 * changes with the branch and over time, so {@link VerifyWorkflowsTask}, which is
	 * offline and has no branch in CI, can't reproduce them. Only
	 * {@link UpdateWorkflowsTask} renders these.
	 */
	static final Set<String> GENERATED = Set.of("workflows/update-antora-ui-spring.yml");

	private static final String ANTORA_UI = "update-antora-ui";

	private static final ObjectMapper YAML = new YAMLMapper();

	private WorkflowSupport() {
	}

	/**
	 * @param local the project's own versions file, which need not exist
	 * @return the versions that ship with the plugin, overlaid with the project's
	 */
	static WorkflowVersions versions(Path local) {
		WorkflowVersions versions = WorkflowVersions.shipped();
		if (!Files.exists(local)) {
			return versions;
		}
		try (var in = Files.newInputStream(local)) {
			return versions.merge(WorkflowVersions.load(in));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * @param file the project's workflow generator configuration, which need not exist
	 * @return the values placeholders are rendered from: the project's, over the defaults
	 * for the options that are on unless a project turns them off, as a template can't
	 * tell a missing value from {@code false}
	 */
	@SuppressWarnings("unchecked")
	static Map<String, Object> config(Path file) {
		Map<String, Object> config = Map.of();
		if (Files.exists(file)) {
			try (var in = Files.newInputStream(file)) {
				Map<String, Object> read = YAML.readValue(in, Map.class);
				config = (read != null) ? read : Map.of();
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return merge(defaults(), config);
	}

	private static Map<String, Object> defaults() {
		return releaseTools(Map.of("verify", true, ANTORA_UI, true));
	}

	private static Map<String, Object> releaseTools(Map<String, Object> values) {
		return Map.of("workflow", Map.of("generator", Map.of("workflows", Map.of("release-tools", values))));
	}

	/**
	 * Add to the model what can only be known when generating. The Antora UI is kept up
	 * to date from the main branch alone, since a scheduled workflow only runs from the
	 * default branch, so on any other branch {@code update-antora-ui} is turned off.
	 * Where it is on, {@code workflow.generator.project.supported-branches} is the
	 * branches that are supported.
	 * @param config the model from {@link #config}
	 * @param branch the branch being generated on, or {@code null} if unknown
	 * @param supported finds the supported branches, only called if they are needed
	 * @return the model to render from
	 */
	@SuppressWarnings("unchecked")
	static Map<String, Object> generate(Map<String, Object> config, String branch, Supplier<List<String>> supported) {
		Map<String, Object> tools = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) config
			.get("workflow")).get("generator")).get("workflows");
		boolean enabled = Boolean.TRUE.equals(((Map<String, Object>) tools.get("release-tools")).get(ANTORA_UI));
		if (!enabled) {
			return config;
		}
		if (branch == null || !branch.endsWith("main")) {
			return merge(config, releaseTools(Map.of(ANTORA_UI, false)));
		}
		return merge(config, Map.of("workflow",
				Map.of("generator", Map.of("project", Map.of("supported-branches", supported.get())))));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> overrides) {
		Map<String, Object> merged = new LinkedHashMap<>(defaults);
		overrides.forEach((key, value) -> {
			Object existing = merged.get(key);
			merged.put(key, (existing instanceof Map && value instanceof Map)
					? merge((Map<String, Object>) existing, (Map<String, Object>) value) : value);
		});
		return merged;
	}

}
