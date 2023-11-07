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

import java.io.File;
import java.util.concurrent.Callable;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.SigningPlugin;

/**
 * @author Steve Riesenberg
 */
public class SpringMavenPublishPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		// Apply base plugins
		project.getPluginManager().apply(MavenPublishPlugin.class);
		project.getPluginManager().apply(SigningPlugin.class);

		createPublication(project);
		configureSigning(project);
		createLocalRepository(project);
	}

	private static void createPublication(Project project) {
		// Gradle plugins have the pluginMaven publication instead of mavenJava
		if (project.getPlugins().hasPlugin(JavaGradlePluginPlugin.class)) {
			return;
		}

		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		publishing.getPublications().create("mavenJava", MavenPublication.class, (maven) -> {
			// @formatter:off
			project.getPlugins().withType(JavaPlugin.class, (plugin) ->
				maven.from(project.getComponents().getByName("java")));
			project.getPlugins().withType(JavaPlatformPlugin.class, (plugin) ->
				maven.from(project.getComponents().getByName("javaPlatform")));
			// @formatter:on
		});
	}

	private static void configureSigning(Project project) {
		// Configure publication signing only if signing key is available
		if (!project.hasProperty("signingKeyId") && !project.hasProperty("signingKey")) {
			return;
		}

		String signingKeyId = (String) project.findProperty("signingKeyId");
		String signingKey = (String) project.findProperty("signingKey");
		String signingPassword = (String) project.findProperty("signingPassword");

		SigningExtension signing = project.getExtensions().getByType(SigningExtension.class);
		signing.setRequired((Callable<Boolean>) () -> project.getGradle().getTaskGraph().hasTask("publishArtifacts"));
		if (signingKeyId != null) {
			signing.useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword);
		}
		else {
			signing.useInMemoryPgpKeys(signingKey, signingPassword);
		}

		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		// @formatter:off
		publishing.getPublications().stream()
			.filter(MavenPublication.class::isInstance)
			.forEach(signing::sign);
		// @formatter:on
	}

	private static void createLocalRepository(Project project) {
		// @formatter:off
		File outputDir = project.getRootProject()
			.getLayout()
			.getBuildDirectory()
			.dir("publications/repos")
			.get()
			.getAsFile();
		// @formatter:on

		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		publishing.getRepositories().maven((maven) -> {
			maven.setName("local");
			maven.setUrl(outputDir);
		});
	}

}
