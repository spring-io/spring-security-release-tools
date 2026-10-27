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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.spring.api.SaganApi;
import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.release.SpringReleases;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

/**
 * What {@link VerifyWorkflowsTask} and {@link UpdateWorkflowsTask} both need to read from
 * a project.
 *
 * @author Josh Cummings
 */
final class WorkflowSupport {

	/**
	 * The templates that render from what {@link #generate} adds to the model, which
	 * changes with the branch and over time, so {@link VerifyWorkflowsTask} can't
	 * reproduce them if it doesn't know the branch, or can't reach the API.
	 */
	static final Set<String> GENERATED = Set.of("workflows/update-antora-ui-spring.yml");

	/**
	 * The templates that only render on a branch that ends in {@code main}, which
	 * {@link VerifyWorkflowsTask} can't tell when it doesn't know the branch, so then it
	 * checks them wherever they are checked in, as if on {@code main}.
	 */
	static final Set<String> MAIN_ONLY = Set.of("workflows/codeql.yml");

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
	 * Set where the branch, the project's name at {@code api.spring.io} and that API are
	 * found, so that {@link UpdateWorkflowsTask} and {@link VerifyWorkflowsTask} see the
	 * same thing. The branch is {@code -Pbranch} or else the one checked out, the project
	 * is {@code -PossRepoName} or else the root project's name without
	 * {@code -commercial}.
	 */
	static void conventions(Project project, Property<String> branch, Property<String> slug,
			Property<String> saganApiUrl) {
		saganApiUrl.convention("https://api.spring.io");
		slug.convention(ProjectUtils.getProperty(project, "ossRepoName")
			.orElse(project.getRootProject().getName().replaceFirst("-commercial$", "")));
		branch.convention(ProjectUtils.getProperty(project, "branch")
			.orElse(project.getProviders()
				.provider(() -> currentBranch(project.getLayout().getProjectDirectory().getAsFile()))));
	}

	static List<String> supportedBranches(String slug, String saganApiUrl) {
		try {
			return new SpringReleases(null, new SaganApi(saganApiUrl, null, null)).getOssSupportedBranches(slug);
		}
		catch (RuntimeException ex) {
			throw new GradleException("Could not find the supported branches of " + slug + " at " + saganApiUrl + " ("
					+ rootMessage(ex) + "). Try again, or turn off workflows.release-tools.update-antora-ui in"
					+ " .github/workflow-generator.yml", ex);
		}
	}

	private static String currentBranch(File directory) {
		try {
			Process process = new ProcessBuilder("git", "branch", "--show-current").directory(directory)
				.redirectErrorStream(true)
				.start();
			String branch = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			return (process.waitFor() == 0 && !branch.isEmpty()) ? branch : null;
		}
		catch (IOException ex) {
			return null;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	static String rootMessage(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return (root.getMessage() != null) ? root.getMessage().lines().findFirst().get() : root.toString();
	}

	/**
	 * @param config the model from {@link #config}
	 * @return the model as it is on a branch that ends in {@code main}
	 */
	static Map<String, Object> onMain(Map<String, Object> config) {
		return merge(config, branch(true));
	}

	private static Map<String, Object> branch(boolean main) {
		return Map.of("workflow", Map.of("generator", Map.of("branch", Map.of("main", main))));
	}

	/**
	 * Add to the model what can only be known when generating. A branch that ends in
	 * {@code main}, which includes the ones that mirror it, is one where
	 * {@code workflow.generator.branch.main} is true, and the workflows that only belong
	 * there are rendered. The Antora UI is kept up to date from such a branch alone,
	 * since a scheduled workflow only runs from the default branch, so on any other
	 * branch {@code update-antora-ui} is turned off. Where it is on,
	 * {@code workflow.generator.project.supported-branches} is the branches that are
	 * supported.
	 * @param model the model from {@link #config}
	 * @param branch the branch being generated on, or {@code null} if unknown
	 * @param supported finds the supported branches, only called if they are needed
	 * @return the model to render from
	 */
	@SuppressWarnings("unchecked")
	static Map<String, Object> generate(Map<String, Object> model, String branch, Supplier<List<String>> supported) {
		boolean main = branch != null && branch.endsWith("main");
		Map<String, Object> config = merge(model, branch(main));
		Map<String, Object> tools = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) config
			.get("workflow")).get("generator")).get("workflows");
		boolean enabled = Boolean.TRUE.equals(((Map<String, Object>) tools.get("release-tools")).get(ANTORA_UI));
		if (!enabled) {
			return config;
		}
		if (!main) {
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
