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

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

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

	private static final String MAVEN_CENTRAL_URL = "https://repo.maven.apache.org/maven2";

	private static final String SPRING_REPOSITORY_HOST = "repo.spring.io";

	private static final String PLUGIN_PORTAL_URL = "https://plugins.gradle.org/m2";

	private static final String VIRTUAL_REPOSITORY_NAME = "spring-lts-virtual";

	private static final String LTS_VIRTUAL_URL = "https://usw1.packages.broadcom.com/artifactory/spring-maven-virtual";

	/**
	 * Adds the virtual repository so that it is consulted before {@code repo.spring.io},
	 * the Gradle Plugin Portal and Maven Central. The Plugin Portal and Maven Central are
	 * moved to the end, because the Plugin Portal redirects most requests to Maven
	 * Central. Repositories added later (for example by the build script) are placed
	 * before Maven Central too, and the virtual repository stays ahead of
	 * {@code repo.spring.io}.
	 * @param project the project to add the repository to
	 */
	void applyVirtualRepository(Project project) {
		if (!isNonOss(project)) {
			return;
		}
		RepositoryHandler repositories = project.getRepositories();
		MavenArtifactRepository virtual = repositories
			.maven(spec(project).repository(VIRTUAL_REPOSITORY_NAME, LTS_VIRTUAL_URL));
		boolean[] arranging = { false };
		Runnable arrange = () -> {
			if (arranging[0]) {
				return;
			}
			arranging[0] = true;
			try {
				arrange(repositories, virtual);
			}
			finally {
				arranging[0] = false;
			}
		};
		arrange.run();
		repositories.whenObjectAdded((repository) -> arrange.run());
	}

	private static void arrange(RepositoryHandler repositories, ArtifactRepository virtual) {
		List<ArtifactRepository> all = new ArrayList<>(repositories);
		all.remove(virtual);
		List<ArtifactRepository> tail = all.stream()
			.filter(InternalRepositoryConventions::isRedirectingToMavenCentral)
			.toList();
		List<ArtifactRepository> head = new ArrayList<>(all);
		head.removeAll(tail);
		int index = head.size();
		for (int i = 0; i < head.size(); i++) {
			if (isSpringRepository(head.get(i))) {
				index = i;
				break;
			}
		}
		head.add(index, virtual);
		List<ArtifactRepository> ordered = new ArrayList<>(head);
		ordered.addAll(tail);
		if (!ordered.equals(new ArrayList<>(repositories))) {
			new ArrayList<>(repositories).forEach(repositories::remove);
			ordered.forEach(repositories::add);
		}
	}

	private static boolean isSpringRepository(ArtifactRepository repository) {
		return repository instanceof MavenArtifactRepository maven
				&& SPRING_REPOSITORY_HOST.equals(maven.getUrl().getHost());
	}

	/**
	 * Maven Central, and the Gradle Plugin Portal which redirects most of its artifact
	 * requests to Maven Central.
	 */
	private static boolean isRedirectingToMavenCentral(ArtifactRepository repository) {
		if (!(repository instanceof MavenArtifactRepository maven)) {
			return false;
		}
		String url = maven.getUrl().toString().replaceAll("/+$", "");
		return MAVEN_CENTRAL_URL.equals(url) || PLUGIN_PORTAL_URL.equals(url);
	}

	void apply(Project project) {
		if (!isNonOss(project)) {
			return;
		}
		RepositorySpec spec = spec(project);
		RepositoryHandler repositories = project.getRepositories();
		repositories.maven(spec.repository("spring-internal-release", INTERNAL_RELEASE_URL));
		repositories.maven(spec.repository("spring-lts-release", LTS_RELEASE_URL));
		if (Versions.isSnapshot(String.valueOf(project.getVersion()))) {
			repositories.maven(spec.repository("spring-lts-snapshot", LTS_SNAPSHOT_URL));
		}
	}

	private static boolean isNonOss(Project project) {
		ReleaseChannel channel = ReleaseChannel.from(project);
		return channel != null && channel != ReleaseChannel.OSS;
	}

	private static RepositorySpec spec(Project project) {
		String username = (String) project.findProperty(ARTIFACTORY_USERNAME_PROPERTY);
		String password = (String) project.findProperty(ARTIFACTORY_PASSWORD_PROPERTY);
		return new RepositorySpec(username, password);
	}

}
