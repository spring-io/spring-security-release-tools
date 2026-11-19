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

package org.springframework.gradle.docs;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

/**
 * @author Steve Riesenberg
 */
public class SpringJavadocOptionsPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.getTasks().withType(Javadoc.class).configureEach((javadoc) -> {
			StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
			options.addStringOption("Xdoclint:none", "-quiet");
		});
		// Resolving the Javadoc tool finalizes the toolchain, so wait until the build
		// script has had a chance to configure it
		project.afterEvaluate((evaluated) -> evaluated.getTasks().withType(Javadoc.class).configureEach((javadoc) -> {
			if (supportsNoFonts(javadoc)) {
				// do not ship 4MB of web fonts for single modules
				StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
				options.addBooleanOption("-no-fonts", true);
			}
		}));
	}

	/**
	 * The {@code --no-fonts} option was added in JDK 23, so only use it when the JDK that
	 * runs Javadoc is at least that version.
	 */
	private static boolean supportsNoFonts(Javadoc javadoc) {
		if (javadoc.getJavadocTool().isPresent()) {
			return javadoc.getJavadocTool().get().getMetadata().getLanguageVersion().asInt() >= 23;
		}
		return JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_23);
	}

}
