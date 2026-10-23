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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.spring.gradle.plugin.core.ProjectUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.work.DisableCachingByDefault;

/**
 * Brings the project's workflows and actions up to date: renders every template, the
 * plugin's defaults, for a project with a {@code workflow-generator.yml}, and any in
 * {@code .github/specs/workflows} or {@code .github/specs/actions} (which replace a
 * default of the same name), into {@code .github/workflows} and {@code .github/actions},
 * then runs the workflow generator, which writes the release train workflows. A template
 * that renders to nothing removes its workflow. Run it locally, after changing a spec or
 * updating this plugin, and commit the result.
 * <p>
 * The generator is an internal project. It is downloaded on first use, so a GitHub token
 * with access to it is needed ({@code -PgitHubAccessToken} or {@code GITHUB_TOKEN}), and
 * it runs on its own Java version regardless of the one running Gradle. Use
 * {@code --skip-generator} to render specs only.
 *
 * @author Josh Cummings
 */
@DisableCachingByDefault(because = "Writes into the .github directory and may call GitHub")
public abstract class UpdateWorkflowsTask extends DefaultTask {

	public static final String TASK_NAME = "updateWorkflows";

	@Internal
	public abstract DirectoryProperty getProjectDirectory();

	@Internal
	public abstract DirectoryProperty getGitHubDirectory();

	@Internal
	public abstract DirectoryProperty getWorkflowsDirectory();

	@Internal
	public abstract DirectoryProperty getActionsDirectory();

	@Internal
	public abstract DirectoryProperty getSpecsDirectory();

	@Internal
	public abstract RegularFileProperty getConfigFile();

	@Internal
	public abstract RegularFileProperty getLocalVersionsFile();

	@Internal
	public abstract DirectoryProperty getGeneratorCacheDirectory();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getGitHubApiUrl();

	@Input
	public abstract Property<Boolean> getSkipGenerator();

	@Option(option = "skip-generator", description = "Render the specs only; do not run the workflow generator")
	public void skipGenerator(boolean skip) {
		getSkipGenerator().set(skip);
	}

	@Inject
	protected abstract JavaToolchainService getJavaToolchainService();

	@TaskAction
	public void update() throws IOException {
		WorkflowVersions versions = WorkflowSupport.versions(getLocalVersionsFile().get().getAsFile().toPath());
		GitHubReleases github = new GitHubReleases(getGitHubApiUrl().get(), getGitHubAccessToken().getOrNull());
		WorkflowVersions.Generator generator = (getSkipGenerator().get()) ? null : versions.generator();
		// find the generator's Java first, so that a missing one fails before anything
		// has been rendered
		String java = (generator != null) ? generatorJava(generator) : null;
		renderSpecs(versions);
		if (generator != null) {
			runGenerator(generator, java, github);
		}
		else if (!getSkipGenerator().get()) {
			getLogger().lifecycle("No workflow generator in the version manifest; skipping");
		}
		List<String> problems = WorkflowVerifier.verifyPins(getWorkflowsDirectory().get().getAsFile().toPath(),
				versions);
		problems.addAll(WorkflowVerifier.verifyPins(getActionsDirectory().get().getAsFile().toPath(), versions));
		problems.forEach((problem) -> getLogger().warn("Not pinned: {}", problem));
	}

	private void renderSpecs(WorkflowVersions versions) throws IOException {
		Path specs = getSpecsDirectory().get().getAsFile().toPath();
		Path github = getGitHubDirectory().get().getAsFile().toPath();
		Path config = getConfigFile().get().getAsFile().toPath();
		WorkflowRenderer renderer = new WorkflowRenderer(versions, WorkflowSupport.config(config));
		// render everything before writing anything, so a spec that can't be rendered
		// leaves the workflows as they were
		Map<Path, String> rendered = new LinkedHashMap<>();
		for (Map.Entry<String, WorkflowTemplates.Template> template : WorkflowTemplates
			.load(specs, Files.exists(config))
			.entrySet()) {
			try {
				rendered.put(github.resolve(template.getKey()), renderer.render(template.getValue().content()));
			}
			catch (IllegalStateException ex) {
				throw new GradleException(template.getValue().source() + ": " + ex.getMessage(), ex);
			}
		}
		Map<Path, String> changed = new LinkedHashMap<>();
		List<Path> removed = new ArrayList<>();
		for (Map.Entry<Path, String> entry : rendered.entrySet()) {
			Path workflow = entry.getKey();
			boolean exists = Files.exists(workflow);
			if (entry.getValue().isBlank()) {
				if (exists) {
					removed.add(workflow);
				}
			}
			else if (!exists || !entry.getValue().equals(Files.readString(workflow))) {
				changed.put(workflow, entry.getValue());
			}
		}
		write(changed);
		for (Path workflow : removed) {
			Files.delete(workflow);
			getLogger().lifecycle("Removed {}", workflow);
		}
	}

	/**
	 * Write every workflow or action beside where it belongs first, and only then move
	 * them into place, so that a failure to write, such as a full disk, leaves the
	 * workflows as they were. Moving is not atomic across files, but is as unlikely to
	 * fail as it gets.
	 */
	private void write(Map<Path, String> changed) throws IOException {
		for (Path workflow : changed.keySet()) {
			Files.createDirectories(workflow.getParent());
		}
		Map<Path, Path> staged = new LinkedHashMap<>();
		try {
			for (Map.Entry<Path, String> entry : changed.entrySet()) {
				Path temporary = Files.createTempFile(entry.getKey().getParent(), ".updateWorkflows-", ".tmp");
				staged.put(entry.getKey(), temporary);
				Files.writeString(temporary, entry.getValue());
			}
			for (Map.Entry<Path, Path> entry : staged.entrySet()) {
				Files.move(entry.getValue(), entry.getKey(), StandardCopyOption.REPLACE_EXISTING);
				getLogger().lifecycle("Updated {}", entry.getKey());
			}
		}
		finally {
			for (Path temporary : staged.values()) {
				Files.deleteIfExists(temporary);
			}
		}
	}

	private String generatorJava(WorkflowVersions.Generator generator) {
		int version = generator.javaVersion();
		try {
			return getJavaToolchainService()
				.launcherFor((spec) -> spec.getLanguageVersion().set(JavaLanguageVersion.of(version)))
				.get()
				.getExecutablePath()
				.getAsFile()
				.getAbsolutePath();
		}
		catch (RuntimeException ex) {
			throw new GradleException("The workflow generator " + generator.version() + " runs on Java " + version
					+ ", and Gradle could not find or provision a JDK " + version + " to run it with ("
					+ rootMessage(ex) + "). Install a JDK " + version + ", or let Gradle download one by adding a"
					+ " toolchain resolver plugin to settings.gradle. If you need a different Java, set \"java\""
					+ " alongside \"version\" and \"sha256\" under \"generator\" in .github/specs/versions.json."
					+ " To render only the specs, without the release train workflows, run with --skip-generator.", ex);
		}
	}

	private static String rootMessage(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return (root.getMessage() != null) ? root.getMessage().lines().findFirst().get() : root.toString();
	}

	private void runGenerator(WorkflowVersions.Generator generator, String java, GitHubReleases github)
			throws IOException {
		Path jar = WorkflowGeneratorJar.resolve(generator, getGeneratorCacheDirectory().get().getAsFile().toPath(),
				github);
		getLogger().lifecycle("Running workflow generator {}", generator.version());
		try {
			Process process = new ProcessBuilder(java, "-jar", jar.toString())
				.directory(getProjectDirectory().get().getAsFile())
				.inheritIO()
				.start();
			int exit = process.waitFor();
			if (exit != 0) {
				throw new GradleException("The workflow generator exited with " + exit);
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new GradleException("Interrupted while running the workflow generator", ex);
		}
	}

	public static void register(Project project) {
		project.getTasks().register(TASK_NAME, UpdateWorkflowsTask.class, (task) -> {
			task.setDescription("Renders workflow specs and runs the workflow generator. Run locally, not in CI");
			task.doNotTrackState("Writes into the .github directory and may call GitHub");

			var github = project.getLayout().getProjectDirectory().dir(".github");
			task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
			task.getGitHubDirectory().set(github);
			task.getWorkflowsDirectory().set(github.dir("workflows"));
			task.getActionsDirectory().set(github.dir("actions"));
			task.getSpecsDirectory().set(github.dir("specs"));
			task.getConfigFile().set(github.file("workflow-generator.yml"));
			task.getLocalVersionsFile().set(github.file("specs/versions.json"));
			task.getGeneratorCacheDirectory()
				.set(project.getGradle()
					.getGradleUserHomeDir()
					.toPath()
					.resolve("spring-security-release-tools/workflow-generator")
					.toFile());
			task.getGitHubApiUrl().convention("https://api.github.com");
			task.getSkipGenerator().convention(false);
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, "gitHubAccessToken")
					.orElse(project.getProviders().environmentVariable("GITHUB_TOKEN"))
					.orElse(project.getProviders().environmentVariable("GH_TOKEN")));
		});
	}

}
