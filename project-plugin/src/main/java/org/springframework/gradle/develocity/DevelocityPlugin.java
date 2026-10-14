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
import org.gradle.StartParameter;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.springframework.gradle.ReleaseChannel;

/**
 * Applies the Develocity plugin so that a {@code develocity {}} block always parses, and
 * applies the Spring Develocity conventions for the "oss" release channel only.
 * <p>
 * For any other release channel, build scans are disabled: {@code --scan} is ignored and
 * publishing is refused, so that nothing is sent to a Develocity server (including
 * scans.gradle.com).
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
		if (channel == ReleaseChannel.OSS) {
			settings.getPluginManager().apply(DEVELOCITY_PLUGIN_ID);
			settings.getPluginManager().apply(DEVELOCITY_CONVENTIONS_PLUGIN_ID);
			return;
		}
		// the start parameters must be changed before the Develocity plugin reads them
		StartParameter startParameter = settings.getStartParameter();
		startParameter.setBuildScan(false);
		startParameter.setNoBuildScan(true);
		settings.getPluginManager().apply(DEVELOCITY_PLUGIN_ID);
		disableBuildScans(settings);
	}

	private void disableBuildScans(Settings settings) {
		DevelocityConfiguration develocity = settings.getExtensions().getByType(DevelocityConfiguration.class);
		develocity.getBuildScan().publishing((publishing) -> publishing.onlyIf((context) -> false));
	}

}
