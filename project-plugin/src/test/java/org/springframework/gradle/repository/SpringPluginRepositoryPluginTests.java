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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.initialization.Settings;
import org.gradle.plugin.management.PluginManagementSpec;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SpringPluginRepositoryPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringPluginRepositoryPluginTests {

	private final Settings settings = mock(Settings.class);

	private final ProviderFactory providers = mock(ProviderFactory.class);

	private final RepositoryHandler repositories = mock(RepositoryHandler.class);

	private final MavenArtifactRepository mirror = mock(MavenArtifactRepository.class);

	private final Gradle gradle = mock(Gradle.class);

	@BeforeEach
	public void setUp() {
		PluginManagementSpec pluginManagement = mock(PluginManagementSpec.class);
		when(this.settings.getProviders()).thenReturn(this.providers);
		when(this.settings.getPluginManagement()).thenReturn(pluginManagement);
		when(pluginManagement.getRepositories()).thenReturn(this.repositories);
		when(this.settings.getGradle()).thenReturn(this.gradle);
		when(this.repositories.maven(any(Action.class))).thenReturn(this.mirror);
		givenProperty("releaseChannel", null);
		givenProperty("artifactoryUsername", null);
		givenProperty("artifactoryPassword", null);
	}

	@Test
	public void applyWhenReleaseChannelUnspecifiedThenNoMirror() {
		new SpringPluginRepositoryPlugin().apply(this.settings);

		verify(this.repositories, never()).maven(any(Action.class));
	}

	@Test
	public void applyWhenReleaseChannelIsOssThenNoMirror() {
		givenProperty("releaseChannel", "oss");

		new SpringPluginRepositoryPlugin().apply(this.settings);

		verify(this.repositories, never()).maven(any(Action.class));
	}

	@Test
	public void applyWhenReleaseChannelIsNotOssThenMirrorMovedToFirst() {
		givenProperty("releaseChannel", "lts");

		new SpringPluginRepositoryPlugin().apply(this.settings);

		InOrder order = inOrder(this.repositories);
		order.verify(this.repositories).maven(any(Action.class));
		order.verify(this.repositories).remove(this.mirror);
		order.verify(this.repositories).addFirst(this.mirror);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void applyWhenReleaseChannelIsNotOssThenMirrorMovedToFirstInBuildscriptOfEachProject() {
		givenProperty("releaseChannel", "lts");
		RepositoryHandler buildscriptRepositories = mock(RepositoryHandler.class);
		MavenArtifactRepository buildscriptMirror = mock(MavenArtifactRepository.class);
		when(buildscriptRepositories.maven(any(Action.class))).thenReturn(buildscriptMirror);
		Project project = mock(Project.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
		when(project.getBuildscript().getRepositories()).thenReturn(buildscriptRepositories);

		new SpringPluginRepositoryPlugin().apply(this.settings);

		ArgumentCaptor<Action<Project>> beforeProject = ArgumentCaptor.forClass(Action.class);
		verify(this.gradle).beforeProject(beforeProject.capture());
		beforeProject.getValue().execute(project);
		InOrder order = inOrder(buildscriptRepositories);
		order.verify(buildscriptRepositories).maven(any(Action.class));
		order.verify(buildscriptRepositories).remove(buildscriptMirror);
		order.verify(buildscriptRepositories).addFirst(buildscriptMirror);
	}

	@Test
	public void applyWhenMirrorAlreadyPresentThenNotAddedAgain() {
		givenProperty("releaseChannel", "lts");
		when(this.repositories.findByName("spring-plugins-mirror")).thenReturn(this.mirror);

		new SpringPluginRepositoryPlugin().apply(this.settings);

		verify(this.repositories, never()).maven(any(Action.class));
	}

	@Test
	public void applyWhenReleaseChannelIsOssThenBuildscriptsNotConfigured() {
		givenProperty("releaseChannel", "oss");

		new SpringPluginRepositoryPlugin().apply(this.settings);

		verify(this.gradle, never()).beforeProject(any(Action.class));
	}

	@Test
	public void applyWhenArtifactoryPropertiesPresentThenMirrorUsesUrlAndCredentials() {
		givenProperty("releaseChannel", "lts");
		givenProperty("artifactoryUsername", "user");
		givenProperty("artifactoryPassword", "password");

		new SpringPluginRepositoryPlugin().apply(this.settings);

		ArgumentCaptor<Action<MavenArtifactRepository>> action = ArgumentCaptor.forClass(Action.class);
		verify(this.repositories).maven(action.capture());
		MavenArtifactRepository repository = mock(MavenArtifactRepository.class);
		PasswordCredentials credentials = mock(PasswordCredentials.class);
		ArgumentCaptor<Action<PasswordCredentials>> credentialsAction = ArgumentCaptor.forClass(Action.class);
		action.getValue().execute(repository);
		verify(repository).setName("spring-plugins-mirror");
		verify(repository).setUrl("https://usw1.packages.broadcom.com/artifactory/spring-plugins-gradle-virtual");
		verify(repository).credentials(credentialsAction.capture());
		credentialsAction.getValue().execute(credentials);
		verify(credentials).setUsername("user");
		verify(credentials).setPassword("password");
	}

	@SuppressWarnings("unchecked")
	private void givenProperty(String name, String value) {
		Provider<String> provider = mock(Provider.class);
		when(provider.getOrNull()).thenReturn(value);
		when(this.providers.gradleProperty(name)).thenReturn(provider);
	}

}
