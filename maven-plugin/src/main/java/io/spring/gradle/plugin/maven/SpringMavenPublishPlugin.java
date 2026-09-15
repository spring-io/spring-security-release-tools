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
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.SigningPlugin;

/**
 * @author Steve Riesenberg
 */
public class SpringMavenPublishPlugin implements Plugin<Project> {

	private static final String MAVEN_JAVA_PUBLICATION_NAME = "mavenJava";

	private static final String DEFAULT_OSS_REPO_OWNER = "spring-projects";

	private static final String ORGANIZATION_NAME = "VMware, Inc.";

	private static final String ORGANIZATION_URL = "https://spring.io";

	private static final String DEVELOPER_NAME = "Spring";

	private static final String DEVELOPER_EMAIL = "ask@spring.io";

	private static final String DEVELOPER_ORGANIZATION_URL = "https://www.spring.io";

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
		publishing.getPublications().create(MAVEN_JAVA_PUBLICATION_NAME, MavenPublication.class, (maven) -> {
			// @formatter:off
			project.getPlugins().withType(JavaPlugin.class, (plugin) ->
				maven.from(project.getComponents().getByName("java")));
			project.getPlugins().withType(JavaPlatformPlugin.class, (plugin) ->
				maven.from(project.getComponents().getByName("javaPlatform")));
			// @formatter:on
			configurePom(project, maven.getPom());
		});

		configureCheckPomLicenseTask(project);
	}

	/**
	 * Configures the POM with the conventions shared by Spring's security projects,
	 * derived from the {@code ossRepoOwner} (default {@code "spring-projects"}) and
	 * {@code ossRepoName} (default {@link Project#getName() the root project's name})
	 * properties. Any of these values can be overridden by configuring the
	 * {@value #MAVEN_JAVA_PUBLICATION_NAME} publication's {@code pom} in the usual way,
	 * since that configuration always runs after a project applies this plugin.
	 */
	private static void configurePom(Project project, MavenPom pom) {
		String ossRepoOwner = ossRepoOwner(project);
		String ossRepoName = ossRepoName(project);

		pom.getName().set(project.provider(project::getName));
		pom.getDescription().set(project.provider(project::getDescription));
		pom.getUrl().set("https://spring.io/projects/" + ossRepoName);
		pom.organization((organization) -> {
			organization.getName().set(ORGANIZATION_NAME);
			organization.getUrl().set(ORGANIZATION_URL);
		});
		pom.licenses((licenses) -> licenses.license((license) -> {
			license.getName().set(ApacheLicense.NAME);
			license.getUrl().set(ApacheLicense.URL);
		}));
		pom.developers((developers) -> developers.developer((developer) -> {
			developer.getName().set(DEVELOPER_NAME);
			developer.getEmail().set(DEVELOPER_EMAIL);
			developer.getOrganization().set(ORGANIZATION_NAME);
			developer.getOrganizationUrl().set(DEVELOPER_ORGANIZATION_URL);
		}));
		pom.scm((scm) -> {
			scm.getConnection().set("scm:git:git://github.com/" + ossRepoOwner + "/" + ossRepoName + ".git");
			scm.getDeveloperConnection()
				.set("scm:git:ssh://git@github.com/" + ossRepoOwner + "/" + ossRepoName + ".git");
			scm.getUrl().set("https://github.com/" + ossRepoOwner + "/" + ossRepoName);
		});
		pom.issueManagement((issueManagement) -> {
			issueManagement.getSystem().set("GitHub");
			issueManagement.getUrl().set("https://github.com/" + ossRepoOwner + "/" + ossRepoName + "/issues");
		});
	}

	private static String ossRepoOwner(Project project) {
		Object ossRepoOwner = project.findProperty("ossRepoOwner");
		return (ossRepoOwner != null) ? ossRepoOwner.toString() : DEFAULT_OSS_REPO_OWNER;
	}

	private static String ossRepoName(Project project) {
		Object ossRepoName = project.findProperty("ossRepoName");
		return (ossRepoName != null) ? ossRepoName.toString() : project.getRootProject().getName();
	}

	private static void configureCheckPomLicenseTask(Project project) {
		String generatePomTaskName = "generatePomFileFor" + capitalize(MAVEN_JAVA_PUBLICATION_NAME) + "Publication";
		TaskProvider<GenerateMavenPom> generatePomFileTask = project.getTasks()
			.named(generatePomTaskName, GenerateMavenPom.class);

		TaskProvider<CheckMavenPomLicenseTask> checkPomLicenseTask = project.getTasks()
			.register("checkMavenPomLicense", CheckMavenPomLicenseTask.class, (task) -> {
				task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
				task.setDescription("Verifies the generated POM for the '" + MAVEN_JAVA_PUBLICATION_NAME
						+ "' publication agrees with the project's LICENSE.txt when it is Apache License, Version 2.0");
				task.getPublicationName().set(MAVEN_JAVA_PUBLICATION_NAME);
				task.getLicenseFile()
					.set(project.getRootProject().getLayout().getProjectDirectory().file("LICENSE.txt"));
				task.getPomFile()
					.set(project.getLayout().file(generatePomFileTask.map(GenerateMavenPom::getDestination)));
				task.dependsOn(generatePomFileTask);
			});

		project.getPlugins()
			.withType(LifecycleBasePlugin.class,
					(plugin) -> project.getTasks()
						.named(LifecycleBasePlugin.CHECK_TASK_NAME)
						.configure((checkTask) -> checkTask.dependsOn(checkPomLicenseTask)));
	}

	private static String capitalize(String value) {
		return value.substring(0, 1).toUpperCase() + value.substring(1);
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
		signing.setRequired(
				(Callable<Boolean>) () -> signingKeyId != null || signingKey != null || signingPassword != null);
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
