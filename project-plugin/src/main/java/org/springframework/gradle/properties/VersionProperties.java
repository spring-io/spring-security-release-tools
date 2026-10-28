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

package org.springframework.gradle.properties;

import java.util.regex.Pattern;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

/**
 * Reads tool version properties, rejecting values that are not plain version strings so
 * that a property cannot alter the dependency coordinates it is interpolated into.
 *
 * @author Josh Cummings
 * @since 1.1.3
 */
public final class VersionProperties {

	private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._-]*");

	private VersionProperties() {
	}

	/**
	 * Returns the trimmed value of the given project property, or the default if the
	 * property is unset or blank.
	 * @param project the project to read the property from
	 * @param name the property name
	 * @param defaultVersion the version to use when the property is not set
	 * @return the version
	 * @throws GradleException if the property is not a valid version string
	 */
	public static String get(Project project, String name, String defaultVersion) {
		Object value = project.findProperty(name);
		if (value == null || value.toString().isBlank()) {
			return defaultVersion;
		}
		String version = value.toString().trim();
		if (!VERSION.matcher(version).matches()) {
			throw new GradleException(
					"Invalid value '%s' for %s. Expected a version such as 1.2.3".formatted(version, name));
		}
		return version;
	}

}
