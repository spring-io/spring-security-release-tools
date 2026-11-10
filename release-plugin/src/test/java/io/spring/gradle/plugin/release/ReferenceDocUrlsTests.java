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

package io.spring.gradle.plugin.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link ReferenceDocUrls}.
 *
 * @author Josh Cummings
 */
class ReferenceDocUrlsTests {

	private static final String URL = "https://docs.example.com/{version}/reference/index.html";

	@ParameterizedTest
	@CsvSource({ "1.2.3,1.2", "1.2.0,1.2", "1.2.0-SNAPSHOT,1.2-SNAPSHOT", "1.2.0-RC1,1.2", "10.20.30,10.20" })
	void resolveWhenReplacingThenUsesMajorMinorVersion(String version, String expected) {
		assertThat(ReferenceDocUrls.resolve(URL, version, true, null))
			.isEqualTo("https://docs.example.com/%s/reference/index.html".formatted(expected));
	}

	@Test
	void resolveWhenNotReplacingThenLeavesPlaceholder() {
		assertThat(ReferenceDocUrls.resolve(URL, "1.2.3", false, null)).isEqualTo(URL);
	}

	@Test
	void resolveWhenNotReplacingThenDoesNotValidateVersion() {
		assertThat(ReferenceDocUrls.resolve(URL, "not-a-version", false, null)).isEqualTo(URL);
	}

	@Test
	void resolveWhenDeprecatedFlagSetThenTakesPrecedence() {
		assertThat(ReferenceDocUrls.resolve(URL, "1.2.3", false, true)).contains("/1.2/");
		assertThat(ReferenceDocUrls.resolve(URL, "1.2.3", true, false)).isEqualTo(URL);
	}

	@Test
	void resolveWhenNoPlaceholderThenUnchanged() {
		var url = "https://docs.example.com/reference/index.html";
		assertThat(ReferenceDocUrls.resolve(url, "1.2.3", true, null)).isEqualTo(url);
	}

	@Test
	void resolveWhenPlaceholderRepeatedThenReplacesAll() {
		assertThat(ReferenceDocUrls.resolve("https://x/{version}/y-{version}", "1.2.3", true, null))
			.isEqualTo("https://x/1.2/y-1.2");
	}

	@Test
	void resolveWhenInvalidVersionThenThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> ReferenceDocUrls.resolve(URL, "nope", true, null));
	}

}
