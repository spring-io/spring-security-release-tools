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

import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DevelocityConventionsPlugin}.
 *
 * @author Josh Cummings
 */
public class DevelocityConventionsPluginTests {

	private final Settings settings = mock(Settings.class);

	private final ProviderFactory providers = mock(ProviderFactory.class);

	private final PluginManager pluginManager = mock(PluginManager.class);

	private final DevelocityConventionsPlugin plugin = new DevelocityConventionsPlugin();

	@BeforeEach
	public void setUp() {
		when(this.settings.getProviders()).thenReturn(this.providers);
		when(this.settings.getPluginManager()).thenReturn(this.pluginManager);
	}

	@Test
	public void applyWhenReleaseChannelUnspecifiedThenDoesNotApplyDevelocityPlugins() {
		givenReleaseChannel(null);

		this.plugin.apply(this.settings);

		verifyNoInteractions(this.pluginManager);
	}

	@Test
	public void applyWhenReleaseChannelIsOssThenAppliesDevelocityPlugins() {
		givenReleaseChannel("oss");

		this.plugin.apply(this.settings);

		verify(this.pluginManager).apply("com.gradle.develocity");
		verify(this.pluginManager).apply("io.spring.ge.conventions");
	}

	@ParameterizedTest
	@ValueSource(strings = { "lts", "internal", "hotfix" })
	public void applyWhenReleaseChannelIsNotOssThenDoesNotApplyDevelocityPlugins(String releaseChannel) {
		givenReleaseChannel(releaseChannel);

		this.plugin.apply(this.settings);

		verifyNoInteractions(this.pluginManager);
	}

	@SuppressWarnings("unchecked")
	private void givenReleaseChannel(String releaseChannel) {
		Provider<String> provider = mock(Provider.class);
		when(provider.getOrNull()).thenReturn(releaseChannel);
		when(this.providers.gradleProperty("releaseChannel")).thenReturn(provider);
	}

}
