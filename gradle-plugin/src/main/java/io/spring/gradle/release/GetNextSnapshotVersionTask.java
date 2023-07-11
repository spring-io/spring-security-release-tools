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

import io.spring.gradle.core.RegularFileUtils;
import io.spring.release.SpringReleases;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import static io.spring.gradle.core.ProjectUtils.getProperty;
import static io.spring.gradle.release.SpringReleasePlugin.CURRENT_VERSION_PROPERTY;

/**
 * @author Steve Riesenberg
 */
public abstract class GetNextSnapshotVersionTask extends DefaultTask {

	public static final String TASK_NAME = "getNextSnapshotVersion";

	private static final String OUTPUT_VERSION_PATH = "next-snapshot-version.txt";

	@Input
	public abstract Property<String> getVersion();

	@OutputFile
	public abstract RegularFileProperty getNextSnapshotVersionFile();

	@TaskAction
	public void getNextSnapshotVersion() {
		var version = getVersion().get();
		var nextVersion = SpringReleases.getNextSnapshotVersion(version);
		RegularFileUtils.writeString(getNextSnapshotVersionFile().get(), nextVersion);
		System.out.println(nextVersion);
	}

	public static void register(Project project) {
		project.getTasks().register(TASK_NAME, GetNextSnapshotVersionTask.class, (task) -> {
			task.setGroup(SpringReleasePlugin.TASK_GROUP);
			task.setDescription(
					"Calculates the next snapshot version based on the current version and outputs the version number");
			task.doNotTrackState("API call to GitHub needs to check for new milestones every time");

			var versionProvider = getProperty(project, CURRENT_VERSION_PROPERTY)
					.orElse(project.getRootProject().getVersion().toString());

			task.getVersion().set(versionProvider);
			task.getNextSnapshotVersionFile().set(project.getLayout().getBuildDirectory().file(OUTPUT_VERSION_PATH));
		});
	}

}
