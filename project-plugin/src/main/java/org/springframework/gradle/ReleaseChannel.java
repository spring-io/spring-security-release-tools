/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.Locale;

import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

public enum ReleaseChannel {

	OSS, INTERNAL, HOTFIX, LTS;

	private static final String PROPERTY = "releaseChannel";

	public static ReleaseChannel from(Project project) {
		if (!project.hasProperty(PROPERTY)) {
			return null;
		}
		String channel = (String) project.property(PROPERTY);
		return from(channel);
	}

	public static ReleaseChannel from(Settings settings) {
		String channel = settings.getProviders().gradleProperty(PROPERTY).getOrNull();
		if (channel == null) {
			return null;
		}
		return from(channel);
	}

	public static ReleaseChannel from(String channel) {
		return ReleaseChannel.valueOf(channel.toUpperCase(Locale.US));
	}

}
