/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.gradle.repository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.internal.credentials.DefaultPasswordCredentials;

/**
 * @author Steve Riesenberg
 */
public abstract class SpringRepositoryPlugin implements Plugin<Project> {

	private static final String FORCE_MAVEN_REPOSITORIES = "forceMavenRepositories";

	private static final String ARTIFACTORY_URL = "https://repo.spring.io";

	private static final String ARTIFACTORY_SNAPSHOT_REPOSITORY = "snapshot";

	private static final String ARTIFACTORY_MILESTONE_REPOSITORY = "milestone";

	private static final String ARTIFACTORY_RELEASE_REPOSITORY = "release";

	private static final String ARTIFACTORY_USERNAME = "artifactoryUsername";

	private static final String ARTIFACTORY_PASSWORD = "artifactoryPassword";

	@Override
	public void apply(Project project) {
		project.afterEvaluate((p) -> {
			String artifactorySnapshotUrl = "%s/%s".formatted(ARTIFACTORY_URL, ARTIFACTORY_SNAPSHOT_REPOSITORY);
			String artifactoryMilestoneUrl = "%s/%s".formatted(ARTIFACTORY_URL, ARTIFACTORY_MILESTONE_REPOSITORY);
			String artifactoryReleaseUrl = "%s/%s".formatted(ARTIFACTORY_URL, ARTIFACTORY_RELEASE_REPOSITORY);
			PasswordCredentials credentials = getArtifactoryCredentials(project);

			List<String> forceMavenRepositories = Collections.emptyList();
			if (project.hasProperty(FORCE_MAVEN_REPOSITORIES)) {
				forceMavenRepositories = List
					.of(Objects.requireNonNull(project.findProperty(FORCE_MAVEN_REPOSITORIES)).toString().split(","));
			}

			String version = project.getVersion().toString();
			boolean isSnapshot = version.endsWith("-SNAPSHOT") && forceMavenRepositories.isEmpty()
					|| forceMavenRepositories.contains("snapshot");
			boolean isMilestone = (version.contains("-RC") || version.contains("-M"))
					&& forceMavenRepositories.isEmpty() || forceMavenRepositories.contains("milestone");

			RepositoryHandler repositories = project.getRepositories();
			if (forceMavenRepositories.contains("local")) {
				repositories.mavenLocal();
			}
			repositories.mavenCentral();
			if (isSnapshot) {
				repositories.maven(repository("artifactory-snapshot", artifactorySnapshotUrl, credentials));
			}
			if (isMilestone) {
				repositories.maven(repository("artifactory-milestone", artifactoryMilestoneUrl, credentials));
			}
			repositories.maven(repository("artifactory-release", artifactoryReleaseUrl, credentials));
		});
	}

	private Action<MavenArtifactRepository> repository(String name, String url,
			PasswordCredentials artifactoryCredentials) {
		return (repo) -> {
			repo.setName(name);
			repo.setUrl(url);
			repo.credentials((credentials) -> {
				credentials.setUsername(artifactoryCredentials.getUsername());
				credentials.setPassword(artifactoryCredentials.getPassword());
			});
		};
	}

	private PasswordCredentials getArtifactoryCredentials(Project project) {
		if (project.hasProperty(ARTIFACTORY_USERNAME) && project.hasProperty(ARTIFACTORY_PASSWORD)) {
			String artifactoryUsername = Objects.requireNonNull(project.property(ARTIFACTORY_USERNAME)).toString();
			String artifactoryPassword = Objects.requireNonNull(project.property(ARTIFACTORY_PASSWORD)).toString();
			return new DefaultPasswordCredentials(artifactoryUsername, artifactoryPassword);
		}
		return new DefaultPasswordCredentials();
	}

}
