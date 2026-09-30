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
import java.util.function.Function;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

/**
 * Adds the Maven repositories needed to resolve Spring dependencies, in this order:
 * <ol>
 * <li>{@code mavenLocal}, when {@code forceMavenRepositories} contains {@code local}</li>
 * <li>Maven Central</li>
 * <li>the release train repository, when {@code RELEASE_TRAIN_MAVEN_REPOSITORY_URL} is
 * set</li>
 * <li>the Spring snapshot, milestone and release repositories</li>
 * <li>the repositories for a non-OSS {@code releaseChannel}</li>
 * </ol>
 * Repositories are added after the project is evaluated.
 *
 * @author Steve Riesenberg
 * @author Josh Cummings
 */
public abstract class SpringRepositoryPlugin implements Plugin<Project> {

	private static final String FORCE_MAVEN_REPOSITORIES = "forceMavenRepositories";

	private static final String OSS_URL = "https://repo.spring.io";

	private static final String OSS_SNAPSHOT_REPOSITORY = "snapshot";

	private static final String OSS_MILESTONE_REPOSITORY = "milestone";

	private static final String OSS_RELEASE_REPOSITORY = "release";

	private static final String ARTIFACTORY_USERNAME = "artifactoryUsername";

	private static final String ARTIFACTORY_PASSWORD = "artifactoryPassword";

	private final DeploymentRepositoryConventions deployment = new DeploymentRepositoryConventions();

	private final InternalRepositoryConventions internal = new InternalRepositoryConventions();

	@Override
	public void apply(Project project) {
		project.afterEvaluate((p) -> addRepositories(p, System::getenv));
	}

	void addRepositories(Project project, Function<String, String> env) {
		String ossSnapshotUrl = "%s/%s".formatted(OSS_URL, OSS_SNAPSHOT_REPOSITORY);
		String ossMilestoneUrl = "%s/%s".formatted(OSS_URL, OSS_MILESTONE_REPOSITORY);
		String ossReleaseUrl = "%s/%s".formatted(OSS_URL, OSS_RELEASE_REPOSITORY);
		RepositorySpec spec = getRepositorySpec(project);

		List<String> forceMavenRepositories = Collections.emptyList();
		if (project.hasProperty(FORCE_MAVEN_REPOSITORIES)) {
			forceMavenRepositories = List
				.of(Objects.requireNonNull(project.findProperty(FORCE_MAVEN_REPOSITORIES)).toString().split(","));
		}

		String version = project.getVersion().toString();
		boolean isSnapshot = Versions.isSnapshot(version) && forceMavenRepositories.isEmpty()
				|| forceMavenRepositories.contains("snapshot");
		boolean isMilestone = Versions.isMilestone(version) && forceMavenRepositories.isEmpty()
				|| forceMavenRepositories.contains("milestone");

		RepositoryHandler repositories = project.getRepositories();
		if (forceMavenRepositories.contains("local")) {
			repositories.mavenLocal();
		}
		repositories.mavenCentral();
		this.deployment.apply(project, env);
		if (isSnapshot) {
			repositories.maven(spec.repository("spring-oss-snapshot", ossSnapshotUrl));
		}
		if (isSnapshot || isMilestone) {
			repositories.maven(spec.repository("spring-oss-milestone", ossMilestoneUrl));
		}
		repositories.maven(spec.repository("spring-oss-release", ossReleaseUrl));
		this.internal.apply(project);
	}

	private static RepositorySpec getRepositorySpec(Project project) {
		String username = (String) project.findProperty(ARTIFACTORY_USERNAME);
		String password = (String) project.findProperty(ARTIFACTORY_PASSWORD);
		return new RepositorySpec(username, password);
	}

}
