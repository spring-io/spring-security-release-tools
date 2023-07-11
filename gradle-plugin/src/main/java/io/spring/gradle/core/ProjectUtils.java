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
package io.spring.gradle.core;

import java.util.Objects;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;

/**
 * @author Steve Riesenberg
 */
public final class ProjectUtils {

	private ProjectUtils() {
	}

	public static Provider<String> getProperty(Project project, String propertyName) {
		var stringProperty = project.getObjects().property(String.class);
		if (project.hasProperty(propertyName)) {
			var propertyValue = Objects.requireNonNull(project.property(propertyName));
			stringProperty.set(propertyValue.toString());
		}
		return stringProperty;
	}

	public static <T extends Task> Provider<String> getProperty(Project project, Class<T> taskType,
			Function<T, RegularFileProperty> function) {
		var task = findTaskByType(project, taskType);
		return function.apply(task).map(RegularFileUtils::readString);
	}

	public static <T extends Task> T findTaskByType(Project project, Class<T> taskType) {
		return project.getTasks().withType(taskType).stream().findFirst().map(taskType::cast)
				.orElseThrow(() -> new UnknownTaskException("Unable to find task of type [%s]".formatted(taskType)));
	}

}
