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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link WorkflowRenderer}.
 */
class WorkflowRendererTests {

	private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

	private static final String OTHER_SHA = "fedcba9876543210fedcba9876543210fedcba98";

	private final WorkflowVersions versions = new WorkflowVersions(null,
			Map.of("actions/checkout", new WorkflowVersions.Action("v7.0.1", SHA)));

	private final WorkflowRenderer renderer = new WorkflowRenderer(this.versions,
			Map.of("java", Map.of("version", 25)));

	@Test
	void renderWhenConditionTrueThenBlockKept() {
		WorkflowRenderer renderer = new WorkflowRenderer(this.versions, Map.of("ci", Map.of("schema", true)));
		assertThat(renderer.render("a\n<<#ci.schema>>\nb\n<</ci.schema>>\nc")).isEqualTo("a\nb\nc");
	}

	@Test
	void renderWhenConditionFalseThenBlockDropped() {
		WorkflowRenderer renderer = new WorkflowRenderer(this.versions, Map.of("ci", Map.of("schema", false)));
		assertThat(renderer.render("a\n<<#ci.schema>>\nb\n<</ci.schema>>\nc")).isEqualTo("a\nc");
	}

	@Test
	void renderWhenConditionMissingThenBlockDropped() {
		assertThat(this.renderer.render("a\n<<#ci.schema>>\nb\n<</ci.schema>>\nc")).isEqualTo("a\nc");
	}

	@Test
	void renderWhenNestedConditionsThenBothMustHold() {
		WorkflowRenderer renderer = new WorkflowRenderer(this.versions, Map.of("x", true, "y", false));
		assertThat(renderer.render("<<#x>>\n1\n<<#y>>\n2\n<</y>>\n3\n<</x>>\n")).isEqualTo("1\n3\n");
	}

	@Test
	void renderWhenConditionUnbalancedThenFails() {
		assertThatIllegalStateException().isThrownBy(() -> this.renderer.render("<<#x>>\nb"));
		assertThatIllegalStateException().isThrownBy(() -> this.renderer.render("b\n<</x>>"));
	}

	@Test
	void renderWhenShippedCiTemplateThenDeploySchemaFollowsGeneratorConfig() {
		String ci = WorkflowTemplates.shipped().get("workflows/ci.yml").content();
		Map<String, Object> project = Map.of("java", Map.of("versions", Map.of("primary", 17)));
		WorkflowRenderer without = new WorkflowRenderer(WorkflowVersions.shipped(),
				Map.of("workflow", Map.of("generator", Map.of("project", project))));
		WorkflowRenderer with = new WorkflowRenderer(WorkflowVersions.shipped(), Map.of("workflow", Map.of("generator",
				Map.of("project", project, "workflows", Map.of("release-tools", Map.of("deploy-schema", true))))));
		assertThat(without.render(ci)).doesNotContain("deploy-schema");
		assertThat(with.render(ci)).contains("  deploy-schema:").contains("ci-deploy-schema.yml");
	}

	@Test
	void renderWhenNoVersionThenManifestVersionUsed() {
		assertThat(this.renderer.render("      - uses: actions/checkout"))
			.isEqualTo("      - uses: actions/checkout@" + SHA + " # v7.0.1");
	}

	@Test
	void renderWhenNoVersionOnSubpathThenRepositoryVersionUsed() {
		assertThat(this.renderer.render("    uses: actions/checkout/sub/path"))
			.isEqualTo("    uses: actions/checkout/sub/path@" + SHA + " # v7.0.1");
	}

	@Test
	void renderWhenTagThenShaFromManifestUsed() {
		assertThat(this.renderer.render("uses: actions/checkout@v7.0.1"))
			.isEqualTo("uses: actions/checkout@" + SHA + " # v7.0.1");
	}

	@Test
	void renderWhenQuotedThenQuotesAndStaleCommentDropped() {
		assertThat(this.renderer.render("uses: \"actions/checkout\" # whatever"))
			.isEqualTo("uses: actions/checkout@" + SHA + " # v7.0.1");
	}

	@Test
	void renderWhenShaThenLeftAlone() {
		String line = "uses: actions/checkout@" + OTHER_SHA + " # my fork";
		assertThat(this.renderer.render(line)).isEqualTo(line);
	}

	@Test
	void renderWhenLocalThenLeftAlone() {
		String line = "uses: ./.github/actions/release-train-build";
		assertThat(this.renderer.render(line)).isEqualTo(line);
	}

	@Test
	void renderWhenTagDiffersFromManifestThenFails() {
		assertThatIllegalStateException().isThrownBy(() -> this.renderer.render("uses: actions/checkout@v6"))
			.withMessageContaining("line 1")
			.withMessageContaining("v7.0.1");
	}

	@Test
	void renderWhenRepositoryUnknownThenFailsWithEveryProblem() {
		assertThatIllegalStateException().isThrownBy(() -> this.renderer.render("uses: a/b\nuses: c/d@v1"))
			.withMessageContaining("line 1")
			.withMessageContaining("line 2");
	}

	@Test
	void renderWhenPlaceholderThenSubstitutedAndExpressionsKept() {
		String rendered = this.renderer.render("java: '<< java.version >>'\nref: ${{ github.ref_name }}\n");
		assertThat(rendered).isEqualTo("java: '25'\nref: ${{ github.ref_name }}\n");
	}

	@Test
	void renderWhenPlaceholderMissingThenFails() {
		assertThatIllegalStateException().isThrownBy(() -> this.renderer.render("<< java.vendor >>"))
			.withMessageContaining("java.vendor");
	}

}
