/*
 * Copyright 2020-2023 the original author or authors.
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
package io.spring.gradle.release;

import java.io.File;

import io.spring.gradle.core.RegularFileUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import org.springframework.util.Assert;

import static io.spring.gradle.core.ProjectUtils.findTaskByType;
import static io.spring.gradle.core.ProjectUtils.getProperty;
import static io.spring.gradle.release.SpringReleasePlugin.CURRENT_VERSION_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY;
import static io.spring.gradle.release.SpringReleasePlugin.GITHUB_USER_NAME_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class GenerateChangelogTask extends JavaExec {
	public static final String TASK_NAME = "generateChangelog";

	private static final String GENERATE_CHANGELOG_CONFIGURATION = "changelogGenerator";
	private static final String GENERATE_CHANGELOG_PATH = "changelog/release-notes.md";
	private static final String GENERATE_CHANGELOG_DEPENDENCY = "spring-io:github-changelog-generator:0.0.8";
	private static final String GENERATE_CHANGELOG_REPO_LAYOUT = "[organization]/[artifact]/releases/download/v[revision]/[artifact].[ext]";
	private static final String GENERATE_CHANGELOG_REPO_URL = "https://github.com/";
	private static final String GENERATE_CHANGELOG_GROUP = "spring-io";

	@Input
	public abstract Property<String> getCurrentVersion();

	@Input
	@Optional
	public abstract Property<String> getUsername();

	@Input
	@Optional
	public abstract Property<String> getPassword();

	@OutputFile
	public abstract RegularFileProperty getReleaseNotesFile();

	@Override
	public void exec() {
		String currentVersion = getCurrentVersion().get();
		String username = getUsername().getOrNull();
		String password = getPassword().getOrNull();
		File outputFile = getReleaseNotesFile().getAsFile().get();
		File parent = outputFile.getParentFile();
		if (!parent.exists()) {
			Assert.isTrue(parent.mkdirs(), "Unable to create " + outputFile);
		}

		args("--spring.config.location=scripts/release/release-notes-sections.yml");
		if (username != null && password != null) {
			args("--github.username=" + username, "--github.password=" + password);
		}
		args(currentVersion, outputFile.toString());
		super.exec();
	}

	public static void register(Project project) {
		createGenerateChangelogConfiguration(project);
		createGenerateChangelogRepository(project);
		project.getTasks().register(TASK_NAME, GenerateChangelogTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription("Generate the release notes (changelog) for a milestone.");
			task.doNotTrackState("API call to GitHub needs to check for open issues every time");
			task.setWorkingDir(project.getRootDir());
			task.classpath(project.getConfigurations().getAt(GENERATE_CHANGELOG_CONFIGURATION));

			var versionProvider = getProperty(project, CURRENT_VERSION_PROPERTY)
					.orElse(findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));

			task.getCurrentVersion().set(versionProvider);
			if (project.hasProperty(GITHUB_ACCESS_TOKEN_PROPERTY)) {
				var usernameProvider = getProperty(project, GITHUB_USER_NAME_PROPERTY)
						.orElse(findTaskByType(project, GetGitHubUserNameTask.class)
								.getUsernameFile()
								.map(RegularFileUtils::readString));

				task.getUsername().set(usernameProvider);
				task.getPassword().set(getProperty(project, GITHUB_ACCESS_TOKEN_PROPERTY));
			}
			task.getReleaseNotesFile().set(project.getLayout().getBuildDirectory().file(GENERATE_CHANGELOG_PATH));
		});
	}

	private static void createGenerateChangelogConfiguration(Project project) {
		project.getConfigurations().create(GENERATE_CHANGELOG_CONFIGURATION, (configuration) ->
				configuration.defaultDependencies((dependencies) ->
						dependencies.add(project.getDependencies().create(GENERATE_CHANGELOG_DEPENDENCY))));
	}

	private static void createGenerateChangelogRepository(Project project) {
		IvyArtifactRepository repository = project.getRepositories().ivy((repo) -> {
			repo.setUrl(GENERATE_CHANGELOG_REPO_URL);
			repo.patternLayout((layout) -> layout.artifact(GENERATE_CHANGELOG_REPO_LAYOUT));
			repo.getMetadataSources().artifact();
		});
		project.getRepositories().exclusiveContent((exclusiveContentRepository) -> {
			exclusiveContentRepository.forRepositories(repository);
			exclusiveContentRepository.filter((descriptor) -> descriptor.includeGroup(GENERATE_CHANGELOG_GROUP));
		});
	}
}
