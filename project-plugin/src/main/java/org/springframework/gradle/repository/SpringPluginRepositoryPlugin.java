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

import org.gradle.api.Plugin;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;

import org.springframework.gradle.ReleaseChannel;

/**
 * Adds the non-OSS Gradle plugin mirror as the first repository for resolving plugins: in
 * {@code pluginManagement} and in the {@code buildscript} of every project.
 * <p>
 * This can be applied on its own (for example from the {@code settings.gradle} of a
 * {@code buildSrc} build) and is also applied by
 * {@link org.springframework.gradle.SpringSettingsPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringPluginRepositoryPlugin implements Plugin<Settings> {

	private static final String ARTIFACTORY_USERNAME_PROPERTY = "artifactoryUsername";

	private static final String ARTIFACTORY_PASSWORD_PROPERTY = "artifactoryPassword";

	private static final String MIRROR_NAME = "spring-plugins-mirror";

	private static final String PLUGINS_MIRROR_URL = "https://usw1.packages.broadcom.com/artifactory/spring-plugins-gradle-virtual";

	@Override
	public void apply(Settings settings) {
		ReleaseChannel channel = ReleaseChannel.from(settings);
		if (channel == null || channel == ReleaseChannel.OSS) {
			return;
		}
		String username = settings.getProviders().gradleProperty(ARTIFACTORY_USERNAME_PROPERTY).getOrNull();
		String password = settings.getProviders().gradleProperty(ARTIFACTORY_PASSWORD_PROPERTY).getOrNull();
		RepositorySpec spec = new RepositorySpec(username, password);
		addFirst(settings.getPluginManagement().getRepositories(), spec);
		settings.getGradle().beforeProject((project) -> addFirst(project.getBuildscript().getRepositories(), spec));
	}

	private static void addFirst(RepositoryHandler repositories, RepositorySpec spec) {
		if (repositories.findByName(MIRROR_NAME) != null) {
			return;
		}
		MavenArtifactRepository mirror = repositories.maven(spec.repository(MIRROR_NAME, PLUGINS_MIRROR_URL));
		repositories.remove(mirror);
		repositories.addFirst(mirror);
	}

}
