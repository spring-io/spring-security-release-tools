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

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

public enum ReleaseChannel {

	OSS, INTERNAL, HOTFIX, LTS;

	private static final String PROPERTY = "releaseChannel";

	public static ReleaseChannel from(Project project) {
		Object channel = project.findProperty(PROPERTY);
		return (channel != null) ? from(channel.toString()) : null;
	}

	public static ReleaseChannel from(Settings settings) {
		String channel = settings.getProviders().gradleProperty(PROPERTY).getOrNull();
		return (channel != null) ? from(channel) : null;
	}

	public static ReleaseChannel from(String channel) {
		try {
			return ReleaseChannel.valueOf(channel.trim().toUpperCase(Locale.US));
		}
		catch (IllegalArgumentException ex) {
			String allowed = Arrays.stream(values())
				.map((value) -> value.name().toLowerCase(Locale.US))
				.collect(Collectors.joining(", "));
			throw new GradleException(
					"Invalid value '%s' for %s. Expected one of: %s".formatted(channel, PROPERTY, allowed), ex);
		}
	}

}
