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

package io.spring.gradle.plugin.maven;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jfrog.gradle.plugin.artifactory.ArtifactoryPlugin;
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention;

/**
 * @author Steve Riesenberg
 */
public class SpringArtifactoryPlugin implements Plugin<Project> {

	private static final String ARTIFACTORY_URL_NAME = "ARTIFACTORY_URL";

	private static final String DEFAULT_ARTIFACTORY_URL = "https://repo.spring.io";

	@Override
	public void apply(Project project) {
		// Apply base plugin
		project.getPluginManager().apply(ArtifactoryPlugin.class);

		// Gather Artifactory repository configuration
		String artifactoryUrl = System.getenv().getOrDefault(ARTIFACTORY_URL_NAME, DEFAULT_ARTIFACTORY_URL);
		String artifactoryUsername = (String) project.findProperty("artifactoryUsername");
		String artifactoryPassword = (String) project.findProperty("artifactoryPassword");

		// @formatter:off
		String repoKey = ProjectUtils.isSnapshot(project) ? "libs-snapshot-local"
				: ProjectUtils.isMilestone(project) ? "libs-milestone-local"
				: "libs-release-local";
		// @formatter:on

		// Apply Artifactory repository configuration
		ArtifactoryPluginConvention artifactoryExtension = project.getExtensions()
			.getByType(ArtifactoryPluginConvention.class);
		artifactoryExtension.publish((publish) -> {
			publish.setContextUrl(artifactoryUrl);
			publish.repository((repository) -> {
				repository.setRepoKey(repoKey);
				if (artifactoryUsername != null && artifactoryPassword != null) {
					repository.setUsername(artifactoryUsername);
					repository.setPassword(artifactoryPassword);
				}
			});

			publish.defaults((defaults) -> defaults.publications("ALL_PUBLICATIONS"));
		});

		// Publish snapshots, milestones, and release candidates to Artifactory
		project.getTasks().named("publishArtifacts", (publishArtifacts) -> {
			if (!ProjectUtils.isRelease(project)) {
				publishArtifacts.dependsOn("artifactoryPublish");
			}
		});
	}

}
