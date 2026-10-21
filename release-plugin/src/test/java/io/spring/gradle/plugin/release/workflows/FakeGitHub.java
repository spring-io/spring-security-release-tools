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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Just enough of GitHub to serve a release and its asset, the way GitHub does: the asset
 * is a redirect to storage elsewhere.
 */
final class FakeGitHub implements AutoCloseable {

	private final HttpServer server;

	private final byte[] asset;

	final List<String> requests = new ArrayList<>();

	final List<String> storageAuthorization = new ArrayList<>();

	FakeGitHub(byte[] asset) throws IOException {
		this.asset = asset;
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", this::handle);
		this.server.start();
	}

	String url() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		synchronized (this.requests) {
			this.requests.add(path);
		}
		if (path.endsWith("/missing")) {
			respond(exchange, 404, "application/json", "{}");
		}
		else if (path.contains("/releases/tags/")) {
			respond(exchange, 200, "application/json",
					"{\"assets\":[{\"id\":42,\"name\":\"github-actions-workflow-generator-0.0.7.jar\"}]}");
		}
		else if (path.endsWith("/releases/assets/42")) {
			exchange.getResponseHeaders().add("Location", url() + "/storage/jar");
			exchange.sendResponseHeaders(302, -1);
		}
		else if (path.equals("/storage/jar")) {
			synchronized (this.requests) {
				this.storageAuthorization.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
			}
			exchange.sendResponseHeaders(200, this.asset.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(this.asset);
			}
		}
		else {
			respond(exchange, 404, "application/json", "{}");
		}
	}

	private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", type);
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

}
