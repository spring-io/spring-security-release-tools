/*
 * Copyright 2002-2023 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public class ReproducibleBuildConventions {

	/**
	 * Ensures that the archive always has the same time and file ordering between builds.
	 * @param project the current project
	 */
	public void apply(Project project) {
		project.getTasks().withType(AbstractArchiveTask.class).configureEach(task -> {
			task.setPreserveFileTimestamps(false);
			task.setReproducibleFileOrder(true);
		});
	}

}
