/*
 * Copyright 2019-2023 the original author or authors.
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

package io.spring.gradle.release;

import io.spring.api.Release;
import io.spring.api.SaganApi;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/**
 * @author Rob Winch
 * @author Steve Riesenberg
 */
public abstract class CreateSaganReleaseTask extends DefaultTask {
	@Input
	public abstract Property<String> getUsername();

	@Input
	public abstract Property<String> getGitHubAccessToken();

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<String> getApiDocUrl();

	@Input
	public abstract Property<String> getReferenceDocUrl();

	@Input
	public abstract Property<String> getProjectName();

	@TaskAction
	public void createRelease() {
		String username = getUsername().get();
		String gitHubAccessToken = getGitHubAccessToken().get();
		String version = getVersion().get();
		String referenceDocUrl = getReferenceDocUrl().get();
		String apiDocUrl = getApiDocUrl().get();

		SaganApi sagan = new SaganApi(username, gitHubAccessToken);
		Release release = new Release(version, referenceDocUrl, apiDocUrl, null, false);
		sagan.createRelease(getProjectName().get(), release);
	}
}
