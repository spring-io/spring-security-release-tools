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

package io.spring.gradle.plugin.maven;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;

/**
 * @author Steve Riesenberg
 */
public class SpringDeploymentRepositoryPublishPlugin implements Plugin<Project> {

	private static final String DEPLOYMENT_REPOSITORY_PROPERTY = "deploymentRepository";

	@Override
	public void apply(Project project) {
		if (!project.hasProperty(DEPLOYMENT_REPOSITORY_PROPERTY)) {
			return;
		}

		project.getPluginManager().withPlugin("maven-publish", (plugin) -> {
			PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
			publishing.getRepositories().maven((repository) -> {
				repository.setName("deployment");
				repository.setUrl(project.property(DEPLOYMENT_REPOSITORY_PROPERTY));
			});
		});
	}

}
