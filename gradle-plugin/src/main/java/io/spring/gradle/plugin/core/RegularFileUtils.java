/*
 * Copyright 2020-2023 the original author or authors.
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
package io.spring.gradle.plugin.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;

/**
 * @author Steve Riesenberg
 */
public final class RegularFileUtils {

	private RegularFileUtils() {
	}

	public static String readString(RegularFile regularFile) {
		Path path = regularFile.getAsFile().toPath();
		try {
			var string = Files.readString(path);
			return (string != null && !string.isEmpty()) ? string : null;
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static Path writeString(RegularFile file, String value) {
		Path path = file.getAsFile().toPath();
		try {
			return Files.writeString(path, value);
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static Transformer<Path, RegularFile> writeString(String value) {
		return (regularFile) -> writeString(regularFile, value);
	}

	public static void mkdirs(DirectoryProperty property) {
		Directory directory = property.get();
		if (!directory.getAsFile().mkdirs()) {
			throw new IllegalArgumentException("Unable to create directories for " + directory);
		}
	}

}
