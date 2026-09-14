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

package io.spring.gradle.plugin.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A {@link org.gradle.api.Task} for checking that a generated Maven POM's license agrees
 * with the project's {@code LICENSE.txt} when it declares the Apache License, Version
 * 2.0.
 *
 * <p>
 * This task only recognizes the Apache License, Version 2.0. When the project's
 * {@code LICENSE.txt} does not exist or does not begin with that license, the task does
 * nothing, since this repository has no basis for asserting what any other license's POM
 * metadata should look like.
 *
 * @author Josh Cummings
 */
public abstract class CheckMavenPomLicenseTask extends DefaultTask {

	static final String APACHE_LICENSE_SIGNATURE_LINE = "Apache License";

	static final String EXPECTED_LICENSE_NAME = "Apache License, Version 2.0";

	static final String EXPECTED_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0";

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getLicenseFile();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getPomFile();

	@Input
	@Optional
	public abstract Property<String> getPublicationName();

	@TaskAction
	public void checkPomLicense() throws IOException, ParserConfigurationException, SAXException {
		if (!isApacheLicensed()) {
			return;
		}

		var pomFile = getPomFile().get().getAsFile();
		var declaredLicenses = readDeclaredLicenses(pomFile);
		var matches = declaredLicenses.stream()
			.anyMatch((license) -> EXPECTED_LICENSE_NAME.equals(license[0]) && EXPECTED_LICENSE_URL.equals(license[1]));
		if (!matches) {
			throw new GradleException(buildFailureMessage(pomFile, declaredLicenses));
		}
	}

	private boolean isApacheLicensed() throws IOException {
		if (!getLicenseFile().isPresent()) {
			return false;
		}

		var licenseFile = getLicenseFile().get().getAsFile();
		if (!licenseFile.isFile()) {
			return false;
		}

		try (var lines = Files.lines(licenseFile.toPath(), StandardCharsets.UTF_8)) {
			return lines.map(String::trim)
				.filter((line) -> !line.isEmpty())
				.findFirst()
				.map(APACHE_LICENSE_SIGNATURE_LINE::equals)
				.orElse(false);
		}
	}

	private List<String[]> readDeclaredLicenses(File pomFile)
			throws ParserConfigurationException, IOException, SAXException {
		if (!pomFile.isFile()) {
			throw new GradleException("Generated POM file does not exist: " + pomFile.getAbsolutePath()
					+ ". Ensure the corresponding generatePomFileFor...Publication task has run first.");
		}

		var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(false);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(pomFile);

		List<String[]> licenses = new ArrayList<>();
		NodeList licenseNodes = document.getElementsByTagName("license");
		for (var i = 0; i < licenseNodes.getLength(); i++) {
			var license = (Element) licenseNodes.item(i);
			licenses.add(new String[] { textContentOf(license, "name"), textContentOf(license, "url") });
		}
		return licenses;
	}

	private String textContentOf(Element parent, String tagName) {
		NodeList nodes = parent.getElementsByTagName(tagName);
		if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
			return null;
		}
		return nodes.item(0).getTextContent().trim();
	}

	private String buildFailureMessage(File pomFile, List<String[]> declaredLicenses) {
		var publication = getPublicationName().getOrElse("<unknown>");
		var message = new StringBuilder();
		message.append(String.format(
				"The generated POM for the '%s' publication does not declare the expected license.%n", publication));
		message.append(String.format("  LICENSE.txt: %s%n", getLicenseFile().get().getAsFile().getAbsolutePath()));
		message.append(String.format("  POM file:    %s%n", pomFile.getAbsolutePath()));
		message
			.append(String.format("  Expected:    name='%s', url='%s'%n", EXPECTED_LICENSE_NAME, EXPECTED_LICENSE_URL));
		if (declaredLicenses.isEmpty()) {
			message.append("  Found:       no <license> elements were declared in the generated POM.");
		}
		else {
			message.append("  Found:").append(String.format("%n"));
			for (String[] license : declaredLicenses) {
				message.append(String.format("    name='%s', url='%s'%n", license[0], license[1]));
			}
		}
		return message.toString();
	}

}
