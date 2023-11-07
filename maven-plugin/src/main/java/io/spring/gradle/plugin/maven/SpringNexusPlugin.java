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

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import io.github.gradlenexus.publishplugin.NexusPublishExtension;
import io.github.gradlenexus.publishplugin.NexusPublishPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * @author Steve Riesenberg
 */
public class SpringNexusPlugin implements Plugin<Project> {

	private static final String NEXUS_URL_NAME = "OSSRH_URL";

	private static final String SNAPSHOT_REPOSITORY_URL_NAME = "OSSRH_SNAPSHOT_REPOSITORY_URL";

	private static final String DEFAULT_NEXUS_URL = "https://s01.oss.sonatype.org/service/local/";

	private static final String DEFAULT_SNAPSHOT_URL = "https://s01.oss.sonatype.org/content/repositories/snapshots/";

	@Override
	public void apply(Project project) {
		// Apply base plugin
		project.getPluginManager().apply(NexusPublishPlugin.class);

		Map<String, String> env = System.getenv();
		String nexusUrl = env.getOrDefault(NEXUS_URL_NAME, DEFAULT_NEXUS_URL);
		String snapshotRepositoryUrl = env.getOrDefault(SNAPSHOT_REPOSITORY_URL_NAME, DEFAULT_SNAPSHOT_URL);

		// Create ossrh repository
		NexusPublishExtension nexusPublishing = project.getExtensions().getByType(NexusPublishExtension.class);
		nexusPublishing.getRepositories().create("ossrh", (nexusRepository) -> {
			nexusRepository.getNexusUrl().set(URI.create(nexusUrl));
			nexusRepository.getSnapshotRepositoryUrl().set(URI.create(snapshotRepositoryUrl));
		});

		// Configure timeouts
		nexusPublishing.getConnectTimeout().set(Duration.ofMinutes(3));
		nexusPublishing.getClientTimeout().set(Duration.ofMinutes(3));

		// Publish releases to Maven Central
		project.getTasks().named("publishArtifacts", (publishArtifacts) -> {
			if (ProjectUtils.isRelease(project)) {
				publishArtifacts.dependsOn("publishToOssrh");
			}
		});

		// Ensure release build automatically closes and releases staging repository
		project.getTasks().named("finalizeDeployArtifacts", (finalizeDeployArtifacts) -> {
			if (ProjectUtils.isRelease(project) && project.hasProperty("ossrhUsername")) {
				finalizeDeployArtifacts.dependsOn("closeAndReleaseOssrhStagingRepository");
			}
		});
	}

}
