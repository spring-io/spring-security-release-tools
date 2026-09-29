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

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.springframework.gradle.ReleaseChannel;

/**
 * Applies Develocity build scan conventions, for the "oss" release channel.
 * <p>
 * The release channel is read from the {@code releaseChannel} project property.
 *
 * @author Josh Cummings
 * @since 1.0.20
 */
public class DevelocityPlugin implements Plugin<Settings> {

	private static final String DEVELOCITY_PLUGIN_ID = "com.gradle.develocity";

	private static final String DEVELOCITY_CONVENTIONS_PLUGIN_ID = "io.spring.ge.conventions";

	@Override
	public void apply(Settings settings) {
		ReleaseChannel channel = ReleaseChannel.from(settings);
		if (channel != ReleaseChannel.OSS) {
			return;
		}
		settings.getPluginManager().apply(DEVELOCITY_PLUGIN_ID);
		settings.getPluginManager().apply(DEVELOCITY_CONVENTIONS_PLUGIN_ID);
	}

}
