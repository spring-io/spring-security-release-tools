/*
 * Copyright 2026 the original author or authors.
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

package io.spring.gradle.plugin.release.workflows;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Where the repository that has the templates is read from: GitHub, or a local git
 * checkout of it, to try out a change before it is pushed.
 *
 * @author Josh Cummings
 */
interface TemplateRepository {

	/**
	 * What a tag that is a version looks like.
	 */
	Pattern VERSION = Pattern.compile("v?\\d{1,9}(\\.\\d{1,9})*");

	/**
	 * @param repository {@code owner/repo}
	 * @return the highest tag that is a version
	 */
	String latestVersion(String repository);

	/**
	 * @param repository {@code owner/repo}
	 * @param tag a tag
	 * @return the full SHA of the commit it names
	 */
	String tagCommit(String repository, String tag);

	/**
	 * Write the files of a commit as a zip, with {@code .github/specs} in a directory
	 * that is named for the commit.
	 * @param repository {@code owner/repo}
	 * @param sha the commit
	 * @param target where to write it
	 */
	void downloadArchive(String repository, String sha, Path target);

	/**
	 * @return where this reads from, for messages
	 */
	String origin();

	/**
	 * @param a a version
	 * @param b another
	 * @return which is higher, comparing each of their numbers in turn
	 */
	static int compare(String a, String b) {
		String[] left = a.replaceFirst("^v", "").split("\\.");
		String[] right = b.replaceFirst("^v", "").split("\\.");
		for (int i = 0; i < Math.max(left.length, right.length); i++) {
			int l = (i < left.length) ? Integer.parseInt(left[i]) : 0;
			int r = (i < right.length) ? Integer.parseInt(right[i]) : 0;
			if (l != r) {
				return Integer.compare(l, r);
			}
		}
		return 0;
	}

}
