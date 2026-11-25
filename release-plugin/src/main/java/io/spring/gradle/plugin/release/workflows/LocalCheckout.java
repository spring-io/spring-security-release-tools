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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.gradle.api.GradleException;

/**
 * A git checkout of the repository that has the templates, which is read with {@code git}
 * so that a change can be tried before it is pushed. The commit has to be committed, and
 * either tagged or the one that is checked out.
 *
 * @author Josh Cummings
 */
final class LocalCheckout implements TemplateRepository {

	private static final long TIMEOUT_SECONDS = 60;

	private final Path directory;

	LocalCheckout(Path directory) {
		this.directory = directory.toAbsolutePath();
		if (!Files.isDirectory(this.directory)) {
			throw new GradleException(this.directory + " is not a directory");
		}
		try {
			git("rev-parse", "--git-dir");
		}
		catch (IllegalStateException ex) {
			throw new GradleException(
					this.directory + " is not a git checkout (" + WorkflowSupport.rootMessage(ex) + ")", ex);
		}
	}

	@Override
	public String latestVersion(String repository) {
		String highest = null;
		for (String tag : git("tag", "--list").split("\\R")) {
			if (VERSION.matcher(tag).matches() && (highest == null || TemplateRepository.compare(tag, highest) > 0)) {
				highest = tag;
			}
		}
		if (highest == null) {
			throw new IllegalStateException(this.directory + " has no tag that is a version");
		}
		return highest;
	}

	@Override
	public String tagCommit(String repository, String tag) {
		if (TemplateSource.LOCAL.equals(tag)) {
			return git("rev-parse", "--verify", "--quiet", "HEAD^{commit}");
		}
		try {
			return git("rev-parse", "--verify", "--quiet", "refs/tags/" + tag + "^{commit}");
		}
		catch (IllegalStateException ex) {
			throw new IllegalStateException(this.directory + " has no tag " + tag + ". Create it, with git tag " + tag
					+ ", on the commit with the templates", ex);
		}
	}

	@Override
	public void downloadArchive(String repository, String sha, Path target) {
		git("archive", "--format=zip", "--prefix=checkout/", "--output=" + target, sha, ".github/specs");
	}

	@Override
	public String origin() {
		return "the checkout at " + this.directory;
	}

	private String git(String... arguments) {
		String[] command = new String[arguments.length + 3];
		command[0] = "git";
		command[1] = "-C";
		command[2] = this.directory.toString();
		System.arraycopy(arguments, 0, command, 3, arguments.length);
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
		// never wait for someone to answer a prompt that nobody can see
		builder.environment().put("GIT_TERMINAL_PROMPT", "0");
		Process process = null;
		try {
			process = builder.start();
			// read as it runs, as reading is what would otherwise wait on a git that
			// does not finish
			CompletableFuture<String> output = read(process.getInputStream());
			if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException(
						"git " + arguments[0] + " did not finish in " + TIMEOUT_SECONDS + " seconds");
			}
			String text = output.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).trim();
			if (process.exitValue() != 0) {
				throw new IllegalStateException(
						text.isEmpty() ? "git " + arguments[0] + " failed" : text.lines().findFirst().get());
			}
			return text;
		}
		catch (IOException ex) {
			throw new IllegalStateException("could not run git: " + ex.getMessage(), ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while running git", ex);
		}
		catch (ExecutionException | TimeoutException ex) {
			throw new IllegalStateException("could not read what git said: " + ex.getMessage(), ex);
		}
		finally {
			if (process != null) {
				process.descendants().forEach(ProcessHandle::destroyForcibly);
				process.destroyForcibly();
			}
		}
	}

	private static CompletableFuture<String> read(InputStream in) {
		return CompletableFuture.supplyAsync(() -> {
			try (in) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		});
	}

}
