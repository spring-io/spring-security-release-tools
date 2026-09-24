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

package io.spring.gradle.plugin.maven;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringMavenPublishPlugin}.
 *
 * @author Josh Cummings
 */
public class SpringMavenPublishPluginTests {

	@TempDir
	private File projectDir;

	private Project project;

	@BeforeEach
	public void setUp() {
		this.project = ProjectBuilder.builder().withProjectDir(this.projectDir).withName("mavenJavaTests").build();
		this.project.getPluginManager().apply("java");
		this.project.getPluginManager().apply(SpringMavenPublishPlugin.class);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenPluginAppliedThenExists() {
		var task = this.project.getTasks().findByName("checkMavenPomLicense");
		assertThat(task).isNotNull().isInstanceOf(CheckMavenPomLicenseTask.class);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenPluginAppliedThenLicenseFileIsRootProjectLicense() {
		var task = (CheckMavenPomLicenseTask) this.project.getTasks().getByName("checkMavenPomLicense");
		var expected = this.project.getRootProject().file("LICENSE.txt");
		assertThat(task.getLicenseFile().get().getAsFile()).isEqualTo(expected);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenPluginAppliedThenDependsOnGeneratePomFileTask() {
		Task checkTask = this.project.getTasks().getByName("checkMavenPomLicense");
		Task generatePomTask = this.project.getTasks().getByName("generatePomFileForMavenJavaPublication");
		Set<Task> dependencies = new HashSet<>(checkTask.getTaskDependencies().getDependencies(checkTask));
		assertThat(dependencies).contains(generatePomTask);
	}

	@Test
	public void checkTaskWhenPluginAppliedThenDependsOnCheckMavenPomLicense() {
		Task checkTask = this.project.getTasks().getByName(LifecycleBasePlugin.CHECK_TASK_NAME);
		Task checkPomLicenseTask = this.project.getTasks().getByName("checkMavenPomLicense");
		Set<Task> dependencies = new HashSet<>(checkTask.getTaskDependencies().getDependencies(checkTask));
		assertThat(dependencies).contains(checkPomLicenseTask);
	}

	@Test
	public void checkMavenPomLicenseTaskWhenJavaGradlePluginPluginAppliedThenNotRegistered() {
		Project otherProject = ProjectBuilder.builder().build();
		otherProject.getPluginManager().apply("java-gradle-plugin");
		otherProject.getPluginManager().apply(SpringMavenPublishPlugin.class);

		assertThat(otherProject.getTasks().findByName("checkMavenPomLicense")).isNull();
	}

	@Test
	public void pomWhenPluginAppliedThenNameIsProjectName() {
		var pom = mavenJavaPom(this.project);
		assertThat(pom.getName().get()).isEqualTo(this.project.getName());
	}

	@Test
	public void pomWhenPluginAppliedThenOrganizationIsVmware() {
		var pom = mavenJavaPom(this.project);
		assertThat(pom.getOrganization().getName().get()).isEqualTo("VMware, Inc.");
		assertThat(pom.getOrganization().getUrl().get()).isEqualTo("https://spring.io");
	}

	@Test
	public void pomWhenPluginAppliedThenLicenseIsApache() {
		var pom = mavenJavaPom(this.project);
		assertThat(pom.getLicenses()).hasSize(1);
		var license = pom.getLicenses().iterator().next();
		assertThat(license.getName().get()).isEqualTo(ApacheLicense.NAME);
		assertThat(license.getUrl().get()).isEqualTo(ApacheLicense.URL);
	}

	@Test
	public void pomWhenPluginAppliedThenDeveloperIsSpring() {
		var pom = mavenJavaPom(this.project);
		assertThat(pom.getDevelopers()).hasSize(1);
		var developer = pom.getDevelopers().iterator().next();
		assertThat(developer.getName().get()).isEqualTo("Spring");
		assertThat(developer.getEmail().get()).isEqualTo("ask@spring.io");
		assertThat(developer.getOrganization().get()).isEqualTo("VMware, Inc.");
		assertThat(developer.getOrganizationUrl().get()).isEqualTo("https://www.spring.io");
	}

	@Test
	public void pomWhenNoOssRepoNamePropertyThenUrlAndScmUseRootProjectName() {
		var pom = mavenJavaPom(this.project);
		var rootProjectName = this.project.getRootProject().getName();
		assertThat(pom.getUrl().get()).isEqualTo("https://spring.io/projects/" + rootProjectName);
		assertThat(pom.getScm().getConnection().get())
			.isEqualTo("scm:git:git://github.com/spring-projects/" + rootProjectName + ".git");
		assertThat(pom.getScm().getDeveloperConnection().get())
			.isEqualTo("scm:git:ssh://git@github.com/spring-projects/" + rootProjectName + ".git");
		assertThat(pom.getScm().getUrl().get()).isEqualTo("https://github.com/spring-projects/" + rootProjectName);
		assertThat(pom.getIssueManagement().getSystem().get()).isEqualTo("GitHub");
		assertThat(pom.getIssueManagement().getUrl().get())
			.isEqualTo("https://github.com/spring-projects/" + rootProjectName + "/issues");
	}

	@Test
	public void pomWhenOssRepoNamePropertySetThenUrlAndScmUseIt() {
		Project otherProject = ProjectBuilder.builder()
			.withProjectDir(this.projectDir)
			.withName("spring-session-build")
			.build();
		otherProject.getExtensions().getExtraProperties().set("ossRepoName", "spring-session");
		otherProject.getPluginManager().apply("java");
		otherProject.getPluginManager().apply(SpringMavenPublishPlugin.class);

		var pom = mavenJavaPom(otherProject);
		assertThat(pom.getUrl().get()).isEqualTo("https://spring.io/projects/spring-session");
		assertThat(pom.getScm().getUrl().get()).isEqualTo("https://github.com/spring-projects/spring-session");
	}

	@Test
	public void pomWhenOssRepoOwnerPropertySetThenScmUsesIt() {
		Project otherProject = ProjectBuilder.builder().build();
		otherProject.getExtensions().getExtraProperties().set("ossRepoOwner", "spring-io");
		otherProject.getPluginManager().apply("java");
		otherProject.getPluginManager().apply(SpringMavenPublishPlugin.class);

		var pom = mavenJavaPom(otherProject);
		var rootProjectName = otherProject.getRootProject().getName();
		assertThat(pom.getScm().getUrl().get()).isEqualTo("https://github.com/spring-io/" + rootProjectName);
	}

	private static MavenPomInternal mavenJavaPom(Project project) {
		var publishing = project.getExtensions().getByType(PublishingExtension.class);
		var publication = (MavenPublication) publishing.getPublications().getByName("mavenJava");
		return (MavenPomInternal) publication.getPom();
	}

}
