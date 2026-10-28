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

package org.springframework.gradle.develocity;

import com.gradle.develocity.agent.gradle.DevelocityConfiguration;
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration;
import com.gradle.develocity.agent.gradle.scan.BuildScanPublishingConfiguration;
import com.gradle.develocity.agent.gradle.scan.BuildScanPublishingConfiguration.PublishingContext;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.api.invocation.Gradle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DevelocityPlugin}.
 *
 * @author Josh Cummings
 */
public class DevelocityPluginTests {

	private final Settings settings = mock(Settings.class);

	private final ProviderFactory providers = mock(ProviderFactory.class);

	private final PluginManager pluginManager = mock(PluginManager.class);

	private final StartParameter startParameter = mock(StartParameter.class);

	private final ExtensionContainer extensions = mock(ExtensionContainer.class);

	private final DevelocityConfiguration develocity = mock(DevelocityConfiguration.class);

	private final BuildScanConfiguration buildScan = mock(BuildScanConfiguration.class);

	private final Gradle gradle = mock(Gradle.class);

	private final BuildCacheConfiguration buildCache = mock(BuildCacheConfiguration.class);

	private final DevelocityPlugin plugin = new DevelocityPlugin();

	@BeforeEach
	public void setUp() {
		when(this.settings.getProviders()).thenReturn(this.providers);
		when(this.settings.getPluginManager()).thenReturn(this.pluginManager);
		when(this.settings.getStartParameter()).thenReturn(this.startParameter);
		when(this.settings.getExtensions()).thenReturn(this.extensions);
		when(this.settings.getGradle()).thenReturn(this.gradle);
		when(this.settings.getBuildCache()).thenReturn(this.buildCache);
		when(this.extensions.getByType(DevelocityConfiguration.class)).thenReturn(this.develocity);
		when(this.develocity.getBuildScan()).thenReturn(this.buildScan);
	}

	@Test
	public void applyWhenReleaseChannelUnspecifiedThenDisablesBuildScans() {
		givenReleaseChannel(null);

		this.plugin.apply(this.settings);

		verify(this.pluginManager).apply("com.gradle.develocity");
		verify(this.pluginManager, never()).apply("io.spring.ge.conventions");
		verifyBuildScansDisabled();
	}

	@Test
	public void applyWhenReleaseChannelIsOssThenAppliesDevelocityConventions() {
		givenReleaseChannel("oss");

		this.plugin.apply(this.settings);

		verify(this.pluginManager).apply("com.gradle.develocity");
		verify(this.pluginManager).apply("io.spring.ge.conventions");
		verifyNoInteractions(this.startParameter, this.develocity);
	}

	@ParameterizedTest
	@ValueSource(strings = { "lts", "internal", "hotfix" })
	public void applyWhenReleaseChannelIsNotOssThenDisablesBuildScans(String releaseChannel) {
		givenReleaseChannel(releaseChannel);

		this.plugin.apply(this.settings);

		verify(this.pluginManager).apply("com.gradle.develocity");
		verify(this.pluginManager, never()).apply("io.spring.ge.conventions");
		verifyBuildScansDisabled();
	}

	@ParameterizedTest
	@ValueSource(strings = { "lts", "internal", "hotfix" })
	public void applyWhenReleaseChannelIsNotOssThenDisablesRemoteBuildCacheAfterSettingsEvaluated(
			String releaseChannel) {
		givenReleaseChannel(releaseChannel);
		BuildCache remote = mock(BuildCache.class);
		when(this.buildCache.getRemote()).thenReturn(remote);

		this.plugin.apply(this.settings);

		verifyNoInteractions(remote);
		settingsEvaluated();
		verify(remote).setEnabled(false);
		verify(remote).setPush(false);
	}

	@Test
	public void applyWhenNoRemoteBuildCacheConfiguredThenSettingsEvaluatedDoesNotFail() {
		givenReleaseChannel("lts");
		when(this.buildCache.getRemote()).thenReturn(null);

		this.plugin.apply(this.settings);

		settingsEvaluated();
	}

	@Test
	public void applyWhenReleaseChannelIsOssThenDoesNotTouchBuildCache() {
		givenReleaseChannel("oss");

		this.plugin.apply(this.settings);

		verifyNoInteractions(this.gradle, this.buildCache);
	}

	@Test
	public void applyWhenReleaseChannelInvalidThenFailsListingAllowedValues() {
		givenReleaseChannel("prod");

		assertThatThrownBy(() -> this.plugin.apply(this.settings)).isInstanceOf(GradleException.class)
			.hasMessageContaining("Invalid value 'prod' for releaseChannel")
			.hasMessageContaining("oss, internal, hotfix, lts");
	}

	@SuppressWarnings("unchecked")
	private void settingsEvaluated() {
		ArgumentCaptor<Action<Settings>> action = ArgumentCaptor.forClass(Action.class);
		verify(this.gradle).settingsEvaluated(action.capture());
		action.getValue().execute(this.settings);
	}

	@SuppressWarnings("unchecked")
	private void verifyBuildScansDisabled() {
		verify(this.startParameter).setBuildScan(false);
		verify(this.startParameter).setNoBuildScan(true);
		ArgumentCaptor<Action<BuildScanPublishingConfiguration>> action = ArgumentCaptor.forClass(Action.class);
		verify(this.buildScan).publishing(action.capture());
		BuildScanPublishingConfiguration publishing = mock(BuildScanPublishingConfiguration.class);
		action.getValue().execute(publishing);
		ArgumentCaptor<Spec<PublishingContext>> spec = ArgumentCaptor.forClass(Spec.class);
		verify(publishing).onlyIf(spec.capture());
		assertThat(spec.getValue().isSatisfiedBy(mock(PublishingContext.class))).isFalse();
	}

	@SuppressWarnings("unchecked")
	private void givenReleaseChannel(String releaseChannel) {
		Provider<String> provider = mock(Provider.class);
		when(provider.getOrNull()).thenReturn(releaseChannel);
		when(this.providers.gradleProperty("releaseChannel")).thenReturn(provider);
	}

}
