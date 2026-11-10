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

package io.spring.gradle.plugin.release;

import io.spring.release.SpringReleases;

/**
 * Resolves the {@code {version}} placeholder in a reference documentation URL.
 *
 * @author Josh Cummings
 */
final class ReferenceDocUrls {

	private static final String VERSION_PLACEHOLDER = "{version}";

	private ReferenceDocUrls() {
	}

	/**
	 * Replace {@code {version}} in the given URL with the major.minor version (keeping
	 * the {@code -SNAPSHOT} suffix, if any) when replacement is enabled.
	 * @param referenceDocUrl the URL, possibly containing {@code {version}}
	 * @param version the release version
	 * @param replaceVersion whether to replace the placeholder
	 * @param replaceSnapshotVersion the deprecated flag that, when not {@code null},
	 * takes precedence over {@code replaceVersion}
	 * @return the resolved URL
	 */
	static String resolve(String referenceDocUrl, String version, boolean replaceVersion,
			Boolean replaceSnapshotVersion) {
		if (replaceSnapshotVersion != null) {
			replaceVersion = replaceSnapshotVersion;
		}
		if (!replaceVersion) {
			return referenceDocUrl;
		}
		var versionMatcher = SpringReleases.versionMatcher(version);
		var majorVersion = versionMatcher.group(1);
		var minorVersion = versionMatcher.group(2);
		var majorMinorVersion = "%s.%s".formatted(majorVersion, minorVersion);
		if (version.endsWith("-SNAPSHOT")) {
			majorMinorVersion += "-SNAPSHOT";
		}
		return referenceDocUrl.replace(VERSION_PLACEHOLDER, majorMinorVersion);
	}

}
