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

package org.springframework.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.PluginManager;

import org.springframework.gradle.develocity.DevelocityConventionsPlugin;

/**
 * Applies Spring Security's settings-level conventions.
 * <p>
 * This is the single entry point projects should apply from {@code settings.gradle}; it
 * applies the individual settings plugins internally so that consumers don't need to know
 * about or reference them directly.
 *
 * @author Josh Cummings
 */
public class SpringSettingsPlugin implements Plugin<Settings> {

	@Override
	public void apply(Settings settings) {
		PluginManager pluginManager = settings.getPluginManager();
		pluginManager.apply(DevelocityConventionsPlugin.class);
	}

}
