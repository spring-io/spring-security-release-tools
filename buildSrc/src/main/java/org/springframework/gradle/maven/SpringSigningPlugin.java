/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.springframework.gradle.maven;

import java.util.List;
import java.util.concurrent.Callable;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.SigningPlugin;

/**
 * @author Steve Riesenberg
 */
public class SpringSigningPlugin implements Plugin<Project> {
	private static final List<String> JAVA_PUBLICATIONS = List.of("mavenJava", "pluginMaven");

	@Override
	public void apply(Project project) {
		project.getPlugins().withType(MavenPublishPlugin.class, (mavenPublish) -> {
			project.getPluginManager().apply(SigningPlugin.class);
			project.getPlugins().withType(SigningPlugin.class, (signingPlugin) -> {
				boolean hasSigningKey = project.hasProperty("signing.keyId") || project.hasProperty("signingKey");
				if (hasSigningKey) {
					sign(project);
				}
			});
		});
	}

	private void sign(Project project) {
		String signingKeyId = (String) project.findProperty("signingKeyId");
		String signingKey = (String) project.findProperty("signingKey");
		String signingPassword = (String) project.findProperty("signingPassword");

		SigningExtension signing = project.getExtensions().getByType(SigningExtension.class);
		signing.setRequired((Callable<Boolean>) () -> signingKeyId != null || signingKey != null || signingPassword != null);

		if (signingKeyId != null) {
			signing.useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword);
		} else {
			signing.useInMemoryPgpKeys(signingKey, signingPassword);
		}
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		publishing.getPublications().stream()
				.filter((publication) -> JAVA_PUBLICATIONS.contains(publication.getName()))
				.forEach(signing::sign);
	}
}
