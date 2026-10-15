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

package org.springframework.gradle.nohttp;

import io.spring.nohttp.gradle.NoHttpExtension;
import io.spring.nohttp.gradle.NoHttpPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * @author Steve Riesenberg
 */
public class SpringNoHttpPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		// Apply nohttp plugin
		project.getPluginManager().apply(NoHttpPlugin.class);

		// Configure nohttp
		NoHttpExtension nohttp = project.getExtensions().getByType(NoHttpExtension.class);
		nohttp.getSource().exclude("buildSrc/build/**");
		ignoreNodeBuildFiles(nohttp, ".github/actions/generate-dependabot-yml");
		ignoreNodeBuildFiles(nohttp, ".github/actions/auto-merge-forward");
		ignoreNodeBuildFiles(nohttp, "auto-merge-forward");	
		ignoreNodeBuildFiles(nohttp, "update-bot");
	}

	private void ignoreNodeBuildFiles(NoHttpExtension nohttp, String name) {
		nohttp.getSource().exclude(name + "/node_modules/**");
		nohttp.getSource().exclude(name + "/dist/**");
		nohttp.getSource().exclude(name + "/coverage/**");
	}
}
