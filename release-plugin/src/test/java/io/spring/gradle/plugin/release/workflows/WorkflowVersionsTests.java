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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link WorkflowVersions}.
 */
class WorkflowVersionsTests {

	private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

	private static final String SHA_256 = "0123456789abcdef".repeat(4);

	@Test
	void shippedWhenLoadedThenEveryActionIsPinnedToATagAndSha() {
		WorkflowVersions shipped = WorkflowVersions.shipped();
		assertThat(shipped.actions()).isNotEmpty();
		// a branch moves, so what it resolves to would change under a fixed plugin
		// version
		shipped.actions()
			.forEach((repository, action) -> assertThat(action.ref()).as(repository)
				.matches("v?\\d+(\\.\\d+)*(-SNAPSHOT)?"));
	}

	@Test
	void shippedWhenLoadedThenGeneratorHasVersionAndChecksum() {
		assertThat(WorkflowVersions.shipped().generator().version()).startsWith("v");
	}

	@Test
	void mergeWhenLocalOverridesThenLocalWins() throws IOException {
		WorkflowVersions shipped = load("{\"actions\":{\"a/b\":{\"ref\":\"v1\",\"sha\":\"" + SHA + "\"}}}");
		WorkflowVersions local = load("{\"actions\":{\"a/b\":{\"ref\":\"v2\",\"sha\":\"" + SHA + "\"},"
				+ "\"c/d\":{\"ref\":\"v3\",\"sha\":\"" + SHA + "\"}}}");
		WorkflowVersions merged = shipped.merge(local);
		assertThat(merged.action("a/b").ref()).isEqualTo("v2");
		assertThat(merged.action("c/d").ref()).isEqualTo("v3");
	}

	@Test
	void shippedWhenLoadedThenThisPluginsOwnEntryHasBeenFilledIn() {
		WorkflowVersions.Action self = WorkflowVersions.shipped().action("spring-io/spring-security-release-tools");
		assertThat(self.ref()).startsWith("v").doesNotContain("$");
		assertThat(self.sha()).matches("[0-9a-f]{40}");
	}

	@ParameterizedTest
	@ValueSource(strings = { "v1.2.3", "v0.0.7", "v1.2.3-RC1", "v1.2.3-SNAPSHOT" })
	void generatorWhenVersionIsAReleaseTagThenAccepted(String version) {
		assertThat(new WorkflowVersions.Generator(version, SHA_256, null).version()).isEqualTo(version);
	}

	@ParameterizedTest
	@ValueSource(strings = { "../../x", "v1.2.3/../../x", "1.2.3", "v1.2", "v1.2.3 ", "main", "v1.2.3\n", "" })
	void generatorWhenVersionIsNotAReleaseTagThenRejected(String version) {
		assertThatIllegalArgumentException().isThrownBy(() -> new WorkflowVersions.Generator(version, SHA_256, null))
			.withMessageContaining("release tag");
	}

	@Test
	void loadWhenActionHasNoShaThenRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new WorkflowVersions.Action("v1", null));
	}

	@Test
	void loadWhenShaIsNotFullLengthThenRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new WorkflowVersions.Action("v1", "abc123"));
	}

	private static WorkflowVersions load(String json) throws IOException {
		return WorkflowVersions.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

}
