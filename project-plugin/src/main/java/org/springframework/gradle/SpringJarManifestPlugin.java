/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * @author Steve Riesenberg
 */
public class SpringJarManifestPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.getTasks().withType(Jar.class, (jar) -> jar.manifest((manifest) -> {
			Map<String, Object> attributes = new HashMap<>();
			attributes.put("Build-Jdk-Spec", buildJdkSpec(project));
			attributes.put("Implementation-Title", project.getName());
			attributes.put("Implementation-Version", project.getVersion().toString());
			attributes.put("Automatic-Module-Name", project.getName().replace("-", "."));
			manifest.attributes(attributes);
		}));
	}

	/**
	 * Reports the toolchain the project builds with so that the value does not depend on
	 * which JDK happens to run Gradle, falling back to the running JDK when no toolchain
	 * is configured.
	 */
	private static Provider<String> buildJdkSpec(Project project) {
		return project.provider(() -> {
			JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
			JavaLanguageVersion toolchain = (java != null) ? java.getToolchain().getLanguageVersion().getOrNull()
					: null;
			return (toolchain != null) ? toolchain.toString() : System.getProperty("java.specification.version");
		});
	}

}
