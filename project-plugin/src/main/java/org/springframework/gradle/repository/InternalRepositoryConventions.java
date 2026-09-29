/*
 * Copyright 2002-2026 the original author or authors.
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

import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

import org.springframework.gradle.ReleaseChannel;

/**
 * Adds the non-OSS Maven repositories used by {@link SpringRepositoryPlugin}.
 * <p>
 * This is not a standalone plugin; {@link SpringRepositoryPlugin} decides where in the
 * repository order each group is added.
 *
 * @author Josh Cummings
 * @since 1.0.20
 */
final class InternalRepositoryConventions {

	static final String DEPLOYMENT_REPOSITORY_NAME = "spring-internal-deployment";

	static final String RELEASE_TRAIN_MAVEN_REPOSITORY_URL = "RELEASE_TRAIN_MAVEN_REPOSITORY_URL";

	static final String RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME = "RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME";

	static final String RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD = "RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD";

	private static final String ARTIFACTORY_USERNAME_PROPERTY = "artifactoryUsername";

	private static final String ARTIFACTORY_PASSWORD_PROPERTY = "artifactoryPassword";

	private static final String INTERNAL_RELEASE_URL = "https://repo.spring.io/artifactory/spring-commercial-release-remote";

	private static final String LTS_RELEASE_URL = "https://usw1.packages.broadcom.com/spring-enterprise-maven-prod-local";

	private static final String LTS_SNAPSHOT_URL = "https://usw1.packages.broadcom.com/spring-enterprise-maven-dev-local";

	private InternalRepositoryConventions() {
	}

	/**
	 * Adds the release train repository for resolution when
	 * {@code RELEASE_TRAIN_MAVEN_REPOSITORY_URL} is set, using the
	 * {@code RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME} and
	 * {@code RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD} credentials when both are set.
	 * @param repositories the repositories to add to
	 * @param env looks up an environment variable by name
	 */
	static void addReleaseTrain(RepositoryHandler repositories, Function<String, String> env) {
		String url = env.apply(RELEASE_TRAIN_MAVEN_REPOSITORY_URL);
		if (url == null) {
			return;
		}
		String username = env.apply(RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME);
		String password = env.apply(RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD);
		RepositorySpec spec = new RepositorySpec(username, password);
		repositories.maven(spec.repository(DEPLOYMENT_REPOSITORY_NAME, url));
	}

	/**
	 * Adds the repositories needed to resolve Spring dependencies for any
	 * {@code releaseChannel} other than {@code oss}, plus the LTS snapshot repository
	 * when the project version is a snapshot.
	 * @param project the project whose {@code releaseChannel} and version are read
	 * @param repositories the repositories to add to
	 */
	static void addReleaseChannel(Project project, RepositoryHandler repositories) {
		ReleaseChannel channel = ReleaseChannel.from(project);
		if (channel == null || channel == ReleaseChannel.OSS) {
			return;
		}
		String username = findProperty(project, ARTIFACTORY_USERNAME_PROPERTY);
		String password = findProperty(project, ARTIFACTORY_PASSWORD_PROPERTY);
		RepositorySpec spec = new RepositorySpec(username, password);
		repositories.maven(spec.repository("spring-internal-release", INTERNAL_RELEASE_URL));
		repositories.maven(spec.repository("spring-lts-release", LTS_RELEASE_URL));
		if (String.valueOf(project.getVersion()).endsWith("-SNAPSHOT")) {
			repositories.maven(spec.repository("spring-lts-snapshot", LTS_SNAPSHOT_URL));
		}
	}

	private static String findProperty(Project project, String propertyName) {
		if (project.hasProperty(propertyName)) {
			return String.valueOf(project.property(propertyName));
		}
		return null;
	}

}
