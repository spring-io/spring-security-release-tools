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

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

import org.springframework.gradle.ReleaseChannel;

/**
 * Adds the non-OSS Maven repositories used by {@link SpringRepositoryPlugin}.
 *
 * @author Josh Cummings
 * @since 1.0.20
 */
final class InternalRepositoryConventions {

	private static final String ARTIFACTORY_USERNAME_PROPERTY = "artifactoryUsername";

	private static final String ARTIFACTORY_PASSWORD_PROPERTY = "artifactoryPassword";

	private static final String INTERNAL_RELEASE_URL = "https://repo.spring.io/artifactory/spring-commercial-release-remote";

	private static final String LTS_RELEASE_URL = "https://usw1.packages.broadcom.com/spring-enterprise-maven-prod-local";

	private static final String LTS_SNAPSHOT_URL = "https://usw1.packages.broadcom.com/spring-enterprise-maven-dev-local";

	void apply(Project project) {
		ReleaseChannel channel = ReleaseChannel.from(project);
		if (channel == null || channel == ReleaseChannel.OSS) {
			return;
		}
		String username = (String) project.findProperty(ARTIFACTORY_USERNAME_PROPERTY);
		String password = (String) project.findProperty(ARTIFACTORY_PASSWORD_PROPERTY);
		RepositorySpec spec = new RepositorySpec(username, password);
		RepositoryHandler repositories = project.getRepositories();
		repositories.maven(spec.repository("spring-internal-release", INTERNAL_RELEASE_URL));
		repositories.maven(spec.repository("spring-lts-release", LTS_RELEASE_URL));
		if (Versions.isSnapshot(String.valueOf(project.getVersion()))) {
			repositories.maven(spec.repository("spring-lts-snapshot", LTS_SNAPSHOT_URL));
		}
	}

}
