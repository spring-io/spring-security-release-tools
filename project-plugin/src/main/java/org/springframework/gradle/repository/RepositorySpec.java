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

package org.springframework.gradle.repository;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

record RepositorySpec(String username, String password) {

	Action<MavenArtifactRepository> repository(String name, String url) {
		return (repo) -> {
			repo.setName(name);
			repo.setUrl(url);
			if (this.username != null && this.password != null) {
				repo.credentials((c) -> {
					c.setUsername(this.username);
					c.setPassword(this.password);
				});
			}
		};
	}
}
