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
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * What {@link VerifyWorkflowsTask} and {@link UpdateWorkflowsTask} both need to read from
 * a project.
 *
 * @author Josh Cummings
 */
final class WorkflowSupport {

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
		return Map.of("workflow",
				Map.of("generator", Map.of("workflows", Map.of("release-tools", Map.of("verify", true)))));
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
