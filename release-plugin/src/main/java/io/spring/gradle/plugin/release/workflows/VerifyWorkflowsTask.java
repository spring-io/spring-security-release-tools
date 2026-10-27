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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.gradle.work.DisableCachingByDefault;

/**
 * Verifies that the project's workflows and actions are pinned to commit SHAs, that pins
 * the version manifest knows about match it, and that workflows rendered from specs are
 * up to date. The workflows the workflow generator writes are not checked; they are
 * verified by its rules. It sees what {@code updateWorkflows} sees, including the branch,
 * so a workflow that belongs only on {@code main} is expected there and an error
 * elsewhere. It does not change anything, so it is safe to run in CI. On {@code main} it
 * asks api.spring.io which branches are supported, and if it can't, doesn't verify the
 * one workflow that depends on them. To bring workflows up to date, run
 * {@code updateWorkflows} locally.
 *
 * @author Josh Cummings
 */
@DisableCachingByDefault(because = "Declares no outputs and reads the whole .github directory")
public abstract class VerifyWorkflowsTask extends DefaultTask {

	public static final String TASK_NAME = "verifyWorkflows";

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

	/**
	 * The branch being verified, which decides which workflows should be there. Defaults
	 * to the one checked out. If there is none, the workflows that depend on it are
	 * verified if they are checked in, as if on {@code main}.
	 */
	@Input
	@Optional
	public abstract Property<String> getBranch();

	@Input
	public abstract Property<String> getProjectSlug();

	@Input
	public abstract Property<String> getSaganApiUrl();

	@TaskAction
	public void verify() throws IOException {
		Path workflows = getWorkflowsDirectory().get().getAsFile().toPath();
		Path actions = getActionsDirectory().get().getAsFile().toPath();
		Path specs = getSpecsDirectory().get().getAsFile().toPath();

		Path config = getConfigFile().get().getAsFile().toPath();
		WorkflowVersions versions = WorkflowSupport.versions(getLocalVersionsFile().get().getAsFile().toPath());
		List<String> problems = new ArrayList<>();
		problems.addAll(WorkflowVerifier.verifyPins(workflows, versions));
		problems.addAll(WorkflowVerifier.verifyPins(actions, versions));
		Map<String, Object> model = WorkflowSupport.config(config);
		Map<String, WorkflowTemplates.Template> templates = new TreeMap<>(
				WorkflowTemplates.load(specs, Files.exists(config)));
		String branch = getBranch().getOrNull();
		if (branch == null) {
			model = WorkflowSupport.onMain(model);
			templates.keySet().removeAll(WorkflowSupport.GENERATED);
			templates.keySet()
				.removeIf((name) -> WorkflowSupport.MAIN_ONLY.contains(name)
						&& !Files.exists(getGitHubDirectory().get().getAsFile().toPath().resolve(name)));
		}
		else {
			try {
				model = WorkflowSupport.generate(model, branch,
						() -> WorkflowSupport.supportedBranches(getProjectSlug().get(), getSaganApiUrl().get()));
			}
			catch (GradleException ex) {
				getLogger().warn("Not verifying {}: {}", WorkflowSupport.GENERATED, ex.getMessage());
				model = WorkflowSupport.generate(model, branch, List::of);
				templates.keySet().removeAll(WorkflowSupport.GENERATED);
			}
		}
		problems.addAll(WorkflowVerifier.verifyRendered(templates, getGitHubDirectory().get().getAsFile().toPath(),
				new WorkflowRenderer(versions, model)));
		if (!problems.isEmpty()) {
			throw new GradleException("Workflow verification found " + problems.size() + " problem(s):\n  "
					+ String.join("\n  ", problems));
		}
	}

	public static void register(Project project) {
		project.getTasks().register(TASK_NAME, VerifyWorkflowsTask.class, (task) -> {
			task.setDescription("Verifies that workflows and actions are pinned to commit SHAs and match their specs");
			task.doNotTrackState("Reads the whole .github directory");

			var github = project.getLayout().getProjectDirectory().dir(".github");
			task.getGitHubDirectory().set(github);
			task.getWorkflowsDirectory().set(github.dir("workflows"));
			task.getActionsDirectory().set(github.dir("actions"));
			task.getSpecsDirectory().set(github.dir("specs"));
			task.getConfigFile().set(github.file("workflow-generator.yml"));
			task.getLocalVersionsFile().set(github.file("specs/versions.json"));
			WorkflowSupport.conventions(project, task.getBranch(), task.getProjectSlug(), task.getSaganApiUrl());
		});
	}

}
