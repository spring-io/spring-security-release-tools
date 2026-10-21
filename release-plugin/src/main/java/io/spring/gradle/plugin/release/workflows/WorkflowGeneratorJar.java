/*
 * Copyright 2026 the original author or authors.
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

package io.spring.gradle.plugin.release.workflows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Finds the workflow generator jar for a version, downloading it once. The generator is
 * an internal project, so it is fetched on demand with the caller's token and kept in a
 * local cache rather than shipped with the plugin. Each use checks the jar against the
 * checksum in the version manifest.
 *
 * @author Josh Cummings
 */
final class WorkflowGeneratorJar {

	static final String REPOSITORY = "spring-io/github-actions-workflow-generator";

	private WorkflowGeneratorJar() {
	}

	/**
	 * @param generator the version to use
	 * @param cache the directory jars are cached in
	 * @param github where to download from if the jar isn't cached
	 * @return the verified jar
	 */
	static Path resolve(WorkflowVersions.Generator generator, Path cache, GitHubReleases github) {
		String name = "github-actions-workflow-generator-" + generator.version().replaceFirst("^v", "") + ".jar";
		Path jar = cache.resolve(generator.version()).resolve(name);
		if (Files.exists(jar) && generator.sha256().equals(sha256(jar))) {
			return jar;
		}
		try {
			Files.createDirectories(jar.getParent());
			Path download = Files.createTempFile(jar.getParent(), name, ".download");
			try {
				Files.deleteIfExists(download);
				github.downloadAsset(REPOSITORY, generator.version(), name, download);
				String actual = sha256(download);
				if (!generator.sha256().equals(actual)) {
					throw new IllegalStateException("workflow generator " + generator.version() + " has checksum "
							+ actual + " but the version manifest expects " + generator.sha256());
				}
				Files.move(download, jar, StandardCopyOption.REPLACE_EXISTING);
				return jar;
			}
			finally {
				Files.deleteIfExists(download);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static String sha256(Path file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
