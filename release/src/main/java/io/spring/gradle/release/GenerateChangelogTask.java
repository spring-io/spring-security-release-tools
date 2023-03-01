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
package io.spring.gradle.release;

import java.io.File;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputFile;

import org.springframework.util.Assert;

/**
 * @author Steve Riesenberg
 */
public abstract class GenerateChangelogTask extends JavaExec {
	@Input
	public abstract Property<String> getVersion();

	@OutputFile
	public abstract RegularFileProperty getReleaseNotes();

	@Override
	public void exec() {
		String version = getVersion().get();
		File outputFile = getReleaseNotes().getAsFile().get();
		File parent = outputFile.getParentFile();
		if (!parent.exists()) {
			Assert.isTrue(parent.mkdirs(), "Unable to create " + outputFile);
		}

		args("--spring.config.location=scripts/release/release-notes-sections.yml", version, outputFile.toString());
		super.exec();
	}
}
