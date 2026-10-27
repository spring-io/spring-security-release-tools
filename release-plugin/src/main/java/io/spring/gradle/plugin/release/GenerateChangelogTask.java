/*
 * Copyright 2002-2023 the original author or authors.
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

package io.spring.gradle.plugin.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.spring.gradle.plugin.core.ProjectUtils;
import io.spring.gradle.plugin.core.RegularFileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.work.DisableCachingByDefault;

/**
 * @author Steve Riesenberg
 */
@DisableCachingByDefault(because = "Output depends on live GitHub issue state that is not captured by the task inputs")
public abstract class GenerateChangelogTask extends JavaExec {

	public static final String TASK_NAME = "generateChangelog";

	private static final String GENERATE_CHANGELOG_CONFIGURATION = "changelogGenerator";

	private static final String GENERATE_CHANGELOG_PATH = "changelog/release-notes.md";

	private static final String GENERATE_CHANGELOG_DEPENDENCY = "spring-io:github-changelog-generator:0.0.12";

	// the checksum of the jar of GENERATE_CHANGELOG_DEPENDENCY, which is downloaded from
	// GitHub and then run with the access token, so change the two together
	private static final String GENERATE_CHANGELOG_SHA_256 = "f42a5b90e3b3cc02c74d289b36af36c317c71d4be67d86f575cc3a853d9e3c7e";

	private static final String GENERATE_CHANGELOG_JAR = "github-changelog-generator.jar";

	private static final String GENERATE_CHANGELOG_REPO_LAYOUT = "[organization]/[artifact]/releases/download/v[revision]/[artifact].[ext]";

	private static final String GENERATE_CHANGELOG_REPO_URL = "https://github.com/";

	private static final String GENERATE_CHANGELOG_GROUP = "spring-io";

	@Input
	public abstract Property<String> getVersion();

	@Input
	@Optional
	public abstract Property<String> getGitHubAccessToken();

	/**
	 * The SHA-256 the changelog generator's jar is expected to have. Set this along with
	 * the {@code changelogGenerator} configuration to use a different version.
	 */
	@Input
	public abstract Property<String> getChangelogGeneratorSha256();

	@OutputFile
	public abstract RegularFileProperty getReleaseNotesFile();

	@Override
	public void exec() {
		var version = getVersion().get();
		var gitHubAccessToken = getGitHubAccessToken().getOrNull();
		var outputFile = getReleaseNotesFile().getAsFile().get();
		var parent = outputFile.getParentFile();
		if (!parent.exists() && !parent.mkdirs()) {
			throw new IllegalStateException("Unable to create " + outputFile);
		}

		verifyGenerator();
		args("--spring.config.location=scripts/release/release-notes-sections.yml");
		if (gitHubAccessToken != null) {
			// not an argument, which anyone on the machine could read in the process list
			environment("GITHUB_TOKEN", gitHubAccessToken);
		}
		args(version, outputFile.toString());
		super.exec();
	}

	private void verifyGenerator() {
		var expected = getChangelogGeneratorSha256().get();
		for (var file : getClasspath().getFiles()) {
			if (file.getName().equals(GENERATE_CHANGELOG_JAR)) {
				var actual = sha256(file.toPath());
				if (!expected.equals(actual)) {
					throw new GradleException("The changelog generator " + file + " has checksum " + actual + " but "
							+ expected + " was expected");
				}
			}
		}
	}

	private static String sha256(Path file) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
		}
		catch (NoSuchAlgorithmException | IOException ex) {
			throw new GradleException("Unable to compute the checksum of " + file, ex);
		}
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

			// @formatter:off
			var versionProvider = ProjectUtils.getProperty(project, SpringReleasePlugin.NEXT_VERSION_PROPERTY)
					.orElse(ProjectUtils.findTaskByType(project, GetNextReleaseMilestoneTask.class)
							.getNextReleaseMilestoneFile()
							.map(RegularFileUtils::readString));
			// @formatter:on

			task.getVersion().set(versionProvider);
			task.getChangelogGeneratorSha256().convention(GENERATE_CHANGELOG_SHA_256);
			task.getGitHubAccessToken()
				.set(ProjectUtils.getProperty(project, SpringReleasePlugin.GITHUB_ACCESS_TOKEN_PROPERTY));
			task.getReleaseNotesFile().set(project.getLayout().getBuildDirectory().file(GENERATE_CHANGELOG_PATH));
		});
	}

	private static void createGenerateChangelogConfiguration(Project project) {
		// @formatter:off
		project.getConfigurations().create(GENERATE_CHANGELOG_CONFIGURATION, (configuration) ->
				configuration.defaultDependencies((dependencies) ->
						dependencies.add(project.getDependencies().create(GENERATE_CHANGELOG_DEPENDENCY))));
		// @formatter:on
	}

	private static void createGenerateChangelogRepository(Project project) {
		var repository = project.getRepositories().ivy((repo) -> {
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
