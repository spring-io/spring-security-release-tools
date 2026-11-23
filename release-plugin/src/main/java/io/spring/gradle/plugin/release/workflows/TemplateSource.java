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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.GradleException;

/**
 * Where the templates and the versions they are pinned to come from:
 * {@code .github/specs} in the commit of the repository that has them, which a project
 * names with {@code uses} under {@code workflow.generator.workflows.release-tools} in its
 * {@code .github/workflow-generator.yml}, like a workflow does, with the tag the commit
 * is of in a comment:
 *
 * <pre>
 * uses: spring-io/spring-security-actions@0123456789abcdef0123456789abcdef01234567 # 1.2.3
 * </pre>
 *
 * That commit is downloaded once and kept, so that rendering and verifying work offline
 * after that. A project that has no {@code uses}, or wants another version, asks for one
 * with {@code --templates-version}, which is then recorded.
 *
 * @author Josh Cummings
 */
final class TemplateSource {

	static final String DEFAULT_REPOSITORY = "spring-io/spring-security-actions";

	private static final String SPECS = ".github/specs/";

	private static final int MAX_FILES = 1000;

	private static final long MAX_BYTES = 20L * 1024 * 1024;

	private static final Pattern TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

	private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");

	private static final Pattern USES = Pattern
		.compile("([A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*)@(\\S+)");

	private static final Pattern COMMENT = Pattern.compile("#\\s*(\\S+)");

	private TemplateSource() {
	}

	/**
	 * @param templates a directory of templates to use as they are, or {@code null}
	 * @param checkout a git checkout of the repository that has the templates, or
	 * {@code null}
	 * @param gitHub where to read it from if there is no checkout
	 * @return where to read the repository that has the templates from
	 */
	static TemplateRepository repository(Path templates, Path checkout, GitHubReleases gitHub) {
		if (checkout == null) {
			return gitHub;
		}
		if (templates != null) {
			throw new GradleException("A directory of templates and a checkout of the repository that has them cannot"
					+ " both be given, as the directory is where the templates would come from");
		}
		return new LocalCheckout(checkout);
	}

	/**
	 * Find the templates for a task, from what it was given.
	 * @param project the project's directory, which a relative {@code checkout} is
	 * relative to
	 * @param config the project's {@code .github/workflow-generator.yml}
	 * @param explicit a directory of templates to use instead, or {@code null}
	 * @param requestedVersion a tag, or {@code latest}, or {@code null}
	 * @param checkout a git checkout of the repository that has the templates, or
	 * {@code null}
	 * @param cache where commits are kept
	 * @param gitHub where to read the repository from if there is no checkout
	 * @param log where to say what is being done
	 * @return where they are
	 */
	static Resolved resolve(Path project, Path config, Path explicit, String requestedVersion, String checkout,
			Path cache, GitHubReleases gitHub, Consumer<String> log) {
		Path local = (checkout != null) ? project.resolve(checkout) : null;
		return resolve(config, explicit, requestedVersion, cache, repository(explicit, local, gitHub), log);
	}

	/**
	 * Find the templates, downloading them if they are not yet kept.
	 * @param config the project's {@code .github/workflow-generator.yml}, which need not
	 * exist
	 * @param explicit a directory of templates to use instead, or {@code null}
	 * @param requestedVersion a tag, or {@code latest}, to use instead of what the
	 * project uses, or {@code null}
	 * @param cache where commits are kept
	 * @param repository where to read the repository that has the templates from
	 * @param log where to say what is being done
	 * @return where they are, which is nowhere if the project does not use them
	 */
	static Resolved resolve(Path config, Path explicit, String requestedVersion, Path cache,
			TemplateRepository repository, Consumer<String> log) {
		if (explicit != null) {
			if (requestedVersion != null) {
				throw new GradleException("A directory of templates and a version of the templates cannot both be"
						+ " asked for, as the version is where the directory would come from");
			}
			return new Resolved(explicit, null, null, false);
		}
		Uses uses = uses(config);
		String name = (uses != null) ? uses.repository() : (requestedVersion != null) ? DEFAULT_REPOSITORY : null;
		if (name == null) {
			return Resolved.NONE;
		}
		WorkflowVersions.Action pin;
		boolean changed = requestedVersion != null;
		if (requestedVersion != null) {
			pin = choose(name, requestedVersion, repository);
		}
		else {
			pin = pinned(config, uses);
		}
		Path directory = cache.resolve(name.replace('/', '-')).resolve(pin.sha());
		if (!Files.isDirectory(directory)) {
			log.accept("Downloading the templates in " + name + " " + pin.ref() + " from " + repository.origin());
			download(name, pin, directory, repository);
		}
		log.accept("Using the templates in " + name + " " + pin.ref());
		return new Resolved(directory, name, pin, changed);
	}

	/**
	 * @return the version that {@code uses} says, which has to say the whole commit and
	 * the tag of it
	 */
	private static WorkflowVersions.Action pinned(Path config, Uses uses) {
		String example = uses.repository() + "@0123456789abcdef0123456789abcdef01234567 # 1.2.3";
		if (!SHA.matcher(uses.sha()).matches()) {
			throw new GradleException(
					config + ": uses under release-tools has to name the whole commit, like " + example + ", not "
							+ uses.sha() + ". Run updateWorkflows with --templates-version latest, or with the"
							+ " tag you want, to have it filled in");
		}
		if (uses.ref() == null || !TAG.matcher(uses.ref()).matches()) {
			throw new GradleException(config + ": uses under release-tools has to say which tag the commit is of, in a"
					+ " comment, like " + example);
		}
		return new WorkflowVersions.Action(uses.ref(), uses.sha());
	}

	/**
	 * Say what the project's configuration would be once what was resolved is in it, so
	 * that it can be known before anything is rendered that it can be.
	 * @param config the project's {@code .github/workflow-generator.yml}
	 * @param resolved what was resolved
	 * @return the configuration with {@code uses} as it should be, or {@code null} if it
	 * is already
	 * @throws GradleException if there is nowhere to put it
	 */
	static String recorded(Path config, Resolved resolved) throws IOException {
		if (!resolved.changed()) {
			return null;
		}
		String replacement = "uses: " + resolved.repository() + "@" + resolved.pin().sha() + " # "
				+ resolved.pin().ref();
		if (!Files.exists(config)) {
			throw new GradleException(config + " does not exist, so there is nowhere to record the templates. Create it"
					+ " with workflow.generator.workflows.release-tools." + replacement);
		}
		List<String> lines = new ArrayList<>(List.of(Files.readString(config).split("\n", -1)));
		String value = value(config);
		int uses = (value != null) ? indexOf(lines, value) : -1;
		if (value != null && uses < 0) {
			throw new GradleException(
					config + " has uses under release-tools, but not as a line of its own that this can"
							+ " change. Change it to " + replacement);
		}
		if (uses >= 0) {
			lines.set(uses, indent(lines.get(uses)) + replacement + ending(lines.get(uses)));
			return String.join("\n", lines);
		}
		int tools = indexOfReleaseTools(lines);
		if (tools < 0) {
			throw new GradleException(config + " has no release-tools under workflow.generator.workflows, so there is"
					+ " nowhere to record the templates. Add " + replacement + " to it");
		}
		String parent = indent(lines.get(tools));
		boolean nested = tools + 1 < lines.size() && !lines.get(tools + 1).isBlank()
				&& indent(lines.get(tools + 1)).length() > parent.length();
		String child = (nested) ? indent(lines.get(tools + 1)) : parent + "  ";
		lines.add(tools + 1, child + replacement + ending(lines.get(tools)));
		return String.join("\n", lines);
	}

	/**
	 * @return what {@code uses} says, or {@code null} if it does not
	 */
	private static Uses uses(Path config) {
		String value = value(config);
		if (value == null) {
			return null;
		}
		Matcher matcher = USES.matcher(value);
		if (!matcher.matches()) {
			throw new GradleException(config + ": uses under release-tools has to look like"
					+ " owner/repo@<commit> # <tag>, not " + value);
		}
		String ref = null;
		try {
			List<String> lines = List.of(Files.readString(config).split("\n", -1));
			int line = indexOf(lines, value);
			Matcher comment = (line >= 0) ? COMMENT.matcher(lines.get(line)) : null;
			ref = (comment != null && comment.find()) ? comment.group(1) : null;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return new Uses(matcher.group(1), matcher.group(2), ref);
	}

	@SuppressWarnings("unchecked")
	private static String value(Path config) {
		Map<String, Object> model = WorkflowSupport.config(config);
		Map<String, Object> workflow = (Map<String, Object>) model.get("workflow");
		Map<String, Object> generator = (Map<String, Object>) workflow.get("generator");
		Map<String, Object> workflows = (Map<String, Object>) generator.get("workflows");
		Map<String, Object> tools = (Map<String, Object>) workflows.get("release-tools");
		Object uses = tools.get("uses");
		return (uses != null) ? uses.toString() : null;
	}

	private static int indexOf(List<String> lines, String value) {
		Pattern line = Pattern.compile("\\s*uses:\\s*[\"']?" + Pattern.quote(value) + "[\"']?\\s*(#.*)?");
		for (int i = 0; i < lines.size(); i++) {
			if (line.matcher(lines.get(i)).matches()) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfReleaseTools(List<String> lines) {
		Pattern line = Pattern.compile("\\s*release-tools:\\s*(#.*)?");
		for (int i = 0; i < lines.size(); i++) {
			if (line.matcher(lines.get(i)).matches()) {
				return i;
			}
		}
		return -1;
	}

	private static String indent(String line) {
		int i = 0;
		while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
			i++;
		}
		return line.substring(0, i);
	}

	private static String ending(String line) {
		return line.endsWith("\r") ? "\r" : "";
	}

	private static WorkflowVersions.Action choose(String repository, String requested, TemplateRepository source) {
		if (!requested.equals("latest") && !TAG.matcher(requested).matches()) {
			throw new GradleException("'" + requested + "' is not a tag; give the tag of a release of " + repository
					+ ", like 1.2.3, or latest");
		}
		try {
			String tag = (requested.equals("latest")) ? source.latestVersion(repository) : requested;
			if (!TAG.matcher(tag).matches()) {
				throw new IllegalStateException("unexpected tag name '" + tag + "'");
			}
			return new WorkflowVersions.Action(tag, source.tagCommit(repository, tag));
		}
		catch (RuntimeException ex) {
			throw new GradleException("Could not find " + requested + " of " + repository + " ("
					+ WorkflowSupport.rootMessage(ex) + "). Give the tag of a release of it, or latest", ex);
		}
	}

	private static void download(String repository, WorkflowVersions.Action pin, Path directory,
			TemplateRepository source) {
		Path parent = directory.getParent();
		Path temporary = null;
		Path zip = null;
		try {
			Files.createDirectories(parent);
			zip = Files.createTempFile(parent, ".download-", ".zip");
			temporary = Files.createTempDirectory(parent, ".extract-");
			try {
				source.downloadArchive(repository, pin.sha(), zip);
			}
			catch (RuntimeException ex) {
				throw new GradleException("Could not download " + repository + " at " + pin.ref() + " ("
						+ WorkflowSupport.rootMessage(ex) + "). Check that uses under release-tools in"
						+ " .github/workflow-generator.yml is a commit of " + repository, ex);
			}
			if (extract(zip, temporary) == 0) {
				throw new GradleException(repository + " at " + pin.ref() + " has nothing under .github/specs. Pin a"
						+ " version of it that has the templates");
			}
			try {
				Files.move(temporary, directory, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException ex) {
				// someone else got there first
				if (!Files.isDirectory(directory)) {
					throw ex;
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			delete(zip);
			delete(temporary);
		}
	}

	/**
	 * Write {@code .github/specs} of the archive, whose files are in a directory named
	 * for the commit, into {@code target}.
	 * @return the number of files written
	 */
	private static int extract(Path zip, Path target) throws IOException {
		Path root = target.toAbsolutePath().normalize();
		int files = 0;
		long bytes = 0;
		try (ZipFile archive = new ZipFile(zip.toFile())) {
			Enumeration<? extends ZipEntry> entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				int slash = name.indexOf('/');
				if (entry.isDirectory() || slash < 0 || !name.startsWith(SPECS, slash + 1)) {
					continue;
				}
				Path file = root.resolve(name.substring(slash + 1 + SPECS.length())).normalize();
				if (!file.startsWith(root)) {
					throw new GradleException("The archive has an entry that leaves its directory: " + name);
				}
				if (++files > MAX_FILES) {
					throw new GradleException("The archive has more than " + MAX_FILES + " files under .github/specs,"
							+ " which is not what templates look like");
				}
				Files.createDirectories(file.getParent());
				try (InputStream in = archive.getInputStream(entry); OutputStream out = Files.newOutputStream(file)) {
					bytes += copy(in, out, MAX_BYTES - bytes);
				}
			}
		}
		return files;
	}

	/**
	 * @return how many bytes were copied
	 * @throws GradleException if there are more than {@code limit}
	 */
	private static long copy(InputStream in, OutputStream out, long limit) throws IOException {
		byte[] buffer = new byte[8192];
		long total = 0;
		for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
			total += read;
			if (total > limit) {
				throw new GradleException("The archive has more than " + (MAX_BYTES / 1024 / 1024)
						+ "MB under .github/specs, which is not what templates look like");
			}
			out.write(buffer, 0, read);
		}
		return total;
	}

	private static void delete(Path path) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach((each) -> each.toFile().delete());
		}
		catch (IOException ex) {
			// best effort, it is only a temporary file
		}
	}

	private record Uses(String repository, String sha, String ref) {

	}

	/**
	 * Where the templates are.
	 *
	 * @param directory the {@code .github/specs} of the repository that has them, or
	 * {@code null} if the project does not use them
	 * @param repository the repository, or {@code null}
	 * @param pin the version of it
	 * @param changed whether {@code uses} in the project's configuration is not yet what
	 * {@code pin} is, because a version was asked for
	 */
	record Resolved(Path directory, String repository, WorkflowVersions.Action pin, boolean changed) {

		static final Resolved NONE = new Resolved(null, null, null, false);

		/**
		 * @return what to lay over the project's versions, which is the version of the
		 * templates, as that is what the workflows that link to them are pinned to
		 */
		WorkflowVersions overlay() {
			return (this.repository != null) ? new WorkflowVersions(null, Map.of(this.repository, this.pin))
					: new WorkflowVersions(null, null);
		}

	}

}
