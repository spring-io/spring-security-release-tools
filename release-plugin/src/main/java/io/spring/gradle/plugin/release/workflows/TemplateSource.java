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
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gradle.api.GradleException;

/**
 * Where the templates and the versions they are pinned to come from:
 * {@code .github/specs} in the commit of the repository that has them, which a project
 * names with {@code templates} in its {@code .github/specs/versions.json} and pins under
 * {@code actions}. That commit is downloaded once and kept, so that rendering and
 * verifying work offline after that. A project that has no pin yet asks for one with
 * {@code --templates-version}, which is then recorded.
 *
 * @author Josh Cummings
 */
final class TemplateSource {

	static final String DEFAULT_REPOSITORY = "spring-io/spring-security-actions";

	private static final String SPECS = ".github/specs/";

	private static final int MAX_FILES = 1000;

	private static final long MAX_BYTES = 20L * 1024 * 1024;

	private static final Pattern TAG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

	private static final ObjectMapper MAPPER = new ObjectMapper();

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
	 * @param localVersions the project's {@code .github/specs/versions.json}
	 * @param explicit a directory of templates to use instead, or {@code null}
	 * @param requestedVersion a tag, or {@code latest}, or {@code null}
	 * @param checkout a git checkout of the repository that has the templates, or
	 * {@code null}
	 * @param cache where commits are kept
	 * @param gitHub where to read the repository from if there is no checkout
	 * @param log where to say what is being done
	 * @return where they are
	 */
	static Resolved resolve(Path project, Path localVersions, Path explicit, String requestedVersion, String checkout,
			Path cache, GitHubReleases gitHub, Consumer<String> log) {
		Path local = (checkout != null) ? project.resolve(checkout) : null;
		return resolve(localVersions, explicit, requestedVersion, cache, repository(explicit, local, gitHub), log);
	}

	/**
	 * Find the templates, downloading them if they are not yet kept.
	 * @param localVersions the project's {@code .github/specs/versions.json}, which need
	 * not exist
	 * @param explicit a directory of templates to use instead, or {@code null}
	 * @param requestedVersion a tag, or {@code latest}, to use instead of what the
	 * project pins, or {@code null}
	 * @param cache where commits are kept
	 * @param repository where to read the repository that has the templates from
	 * @param log where to say what is being done
	 * @return where they are, which is nowhere if the project does not use them
	 */
	static Resolved resolve(Path localVersions, Path explicit, String requestedVersion, Path cache,
			TemplateRepository repository, Consumer<String> log) {
		if (explicit != null) {
			if (requestedVersion != null) {
				throw new GradleException("A directory of templates and a version of the templates cannot both be"
						+ " asked for, as the version is where the directory would come from");
			}
			return new Resolved(explicit, null, null, false);
		}
		WorkflowVersions local = read(localVersions);
		String name = (local.templates() != null) ? local.templates()
				: (requestedVersion != null) ? DEFAULT_REPOSITORY : null;
		if (name == null) {
			return Resolved.NONE;
		}
		WorkflowVersions.Action pin;
		if (requestedVersion != null) {
			pin = choose(name, requestedVersion, repository);
		}
		else {
			pin = local.action(name);
			if (pin == null) {
				throw new GradleException(localVersions + " says the templates are in " + name
						+ ", so it has to pin a version of it under \"actions\", like \"" + name
						+ "\": { \"ref\": \"1.2.3\", \"sha\": \"<the commit of that tag>\" }, or you can run with"
						+ " --templates-version latest to have that done for you");
			}
		}
		Path directory = cache.resolve(name.replace('/', '-')).resolve(pin.sha());
		if (!Files.isDirectory(directory)) {
			log.accept("Downloading the templates in " + name + " " + pin.ref() + " from " + repository.origin());
			download(name, pin, directory, repository);
		}
		log.accept("Using the templates in " + name + " " + pin.ref());
		return new Resolved(directory, name, pin, requestedVersion != null);
	}

	/**
	 * Record a version that was asked for in the project's versions file, which is
	 * created if there is none. Anything else in it is kept. A version that was not asked
	 * for is already there.
	 */
	static void record(Path localVersions, Resolved resolved) throws IOException {
		if (!resolved.chosen()) {
			return;
		}
		ObjectNode root = root(localVersions);
		root.put("templates", resolved.repository());
		ObjectNode actions = (root.get("actions") instanceof ObjectNode existing) ? existing
				: root.putObject("actions");
		ObjectNode entry = actions.putObject(resolved.repository());
		entry.put("ref", resolved.pin().ref());
		entry.put("sha", resolved.pin().sha());
		DefaultPrettyPrinter printer = new Printer();
		Path parent = localVersions.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, ".versions-", ".tmp");
		try {
			Files.writeString(temporary, MAPPER.writer(printer).writeValueAsString(root) + "\n");
			permissions(localVersions, temporary);
			Files.move(temporary, localVersions, StandardCopyOption.REPLACE_EXISTING);
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	/**
	 * @return what the file has, which is nothing if there is no file
	 * @throws GradleException if the file has something other than an object, which is
	 * not for this to overwrite
	 */
	private static ObjectNode root(Path localVersions) throws IOException {
		if (!Files.exists(localVersions)) {
			return MAPPER.createObjectNode();
		}
		JsonNode node = MAPPER.readTree(localVersions.toFile());
		if (node == null || node.isMissingNode()) {
			return MAPPER.createObjectNode();
		}
		if (!(node instanceof ObjectNode object)) {
			throw new GradleException(localVersions + " has to be a JSON object to pin the templates in it, but is "
					+ node.getNodeType().toString().toLowerCase(Locale.ROOT) + ". Fix it, or move it aside");
		}
		return object;
	}

	/**
	 * A temporary file is only readable by its owner. Keep the permissions of the file it
	 * replaces, or else make it readable like any other file in the project.
	 */
	private static void permissions(Path replaced, Path temporary) throws IOException {
		try {
			Set<PosixFilePermission> permissions = (Files.exists(replaced)) ? Files.getPosixFilePermissions(replaced)
					: PosixFilePermissions.fromString("rw-r--r--");
			Files.setPosixFilePermissions(temporary, permissions);
		}
		catch (UnsupportedOperationException ex) {
			// not a POSIX file system, which has no such permissions to get wrong
		}
	}

	private static WorkflowVersions read(Path localVersions) {
		if (!Files.exists(localVersions)) {
			return new WorkflowVersions(null, null, null);
		}
		try (InputStream in = Files.newInputStream(localVersions)) {
			return WorkflowVersions.load(in);
		}
		catch (JsonProcessingException ex) {
			throw new GradleException(localVersions + " could not be read: " + WorkflowSupport.rootMessage(ex), ex);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
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
						+ WorkflowSupport.rootMessage(ex) + "). Check that the pin under \"actions\" in"
						+ " .github/specs/versions.json is a commit of " + repository, ex);
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

	/**
	 * Where the templates are.
	 *
	 * @param directory the {@code .github/specs} of the repository that has them, or
	 * {@code null} if the project does not use them
	 * @param repository the repository, or {@code null}
	 * @param pin the version of it
	 * @param chosen whether the version was asked for, so is not yet recorded
	 */
	record Resolved(Path directory, String repository, WorkflowVersions.Action pin, boolean chosen) {

		static final Resolved NONE = new Resolved(null, null, null, false);

		/**
		 * @return what to lay over the project's versions, which is the version that was
		 * asked for, as it is not yet in them
		 */
		WorkflowVersions overlay() {
			return (this.chosen) ? new WorkflowVersions(null, Map.of(this.repository, this.pin), this.repository)
					: new WorkflowVersions(null, null, null);
		}

	}

	/**
	 * Tabs, and {@code "key": value} rather than the {@code "key" : value} that Jackson
	 * writes.
	 */
	private static final class Printer extends DefaultPrettyPrinter {

		Printer() {
			indentObjectsWith(new DefaultIndenter("\t", "\n"));
			indentArraysWith(new DefaultIndenter("\t", "\n"));
		}

		private Printer(Printer base) {
			super(base);
		}

		@Override
		public DefaultPrettyPrinter createInstance() {
			return new Printer(this);
		}

		@Override
		public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
			generator.writeRaw(": ");
		}

	}

}
