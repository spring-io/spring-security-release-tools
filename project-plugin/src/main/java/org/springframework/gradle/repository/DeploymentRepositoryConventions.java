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

package org.springframework.gradle.repository;

import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

final class DeploymentRepositoryConventions {

	static final String DEPLOYMENT_REPOSITORY_NAME = "spring-staged-deployment";

	static final String RELEASE_TRAIN_MAVEN_REPOSITORY_URL = "RELEASE_TRAIN_MAVEN_REPOSITORY_URL";

	static final String RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME = "RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME";

	static final String RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD = "RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD";

	void apply(Project project) {
		apply(project, System::getenv);
	}

	void apply(Project project, Function<String, String> env) {
		RepositoryHandler repositories = project.getRepositories();
		String url = env.apply(RELEASE_TRAIN_MAVEN_REPOSITORY_URL);
		if (url == null) {
			return;
		}
		String username = env.apply(RELEASE_TRAIN_MAVEN_REPOSITORY_USERNAME);
		String password = env.apply(RELEASE_TRAIN_MAVEN_REPOSITORY_PASSWORD);
		RepositorySpec spec = new RepositorySpec(username, password);
		repositories.maven(spec.repository(DEPLOYMENT_REPOSITORY_NAME, url));
	}

}
