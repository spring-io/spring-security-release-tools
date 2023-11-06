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

package com.github.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Steve Riesenberg
 */
// @formatter:off
public record Release(
		@JsonProperty("tag_name") String tag,
		@JsonProperty("target_commitish") String commit,
		@JsonProperty("name") String name,
		@JsonProperty("body") String body,
		@JsonProperty("draft") boolean draft,
		@JsonProperty("prerelease") boolean preRelease,
		@JsonProperty("generate_release_notes") boolean generateReleaseNotes) {
// @formatter:on

	public static Builder tag(String tag) {
		return new Builder().tag(tag);
	}

	public static Builder commit(String commit) {
		return new Builder().commit(commit);
	}

	public static final class Builder {

		private String tag;

		private String commit;

		private String name;

		private String body;

		private boolean draft;

		private boolean preRelease;

		private boolean generateReleaseNotes;

		private Builder() {
		}

		public Builder tag(String tag) {
			this.tag = tag;
			return this;
		}

		public Builder commit(String commit) {
			this.commit = commit;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder body(String body) {
			this.body = body;
			return this;
		}

		public Builder draft(boolean draft) {
			this.draft = draft;
			return this;
		}

		public Builder preRelease(boolean preRelease) {
			this.preRelease = preRelease;
			return this;
		}

		public Builder generateReleaseNotes(boolean generateReleaseNotes) {
			this.generateReleaseNotes = generateReleaseNotes;
			return this;
		}

		public Release build() {
			return new Release(this.tag, this.commit, this.name, this.body, this.draft, this.preRelease,
					this.generateReleaseNotes);
		}

	}
}
