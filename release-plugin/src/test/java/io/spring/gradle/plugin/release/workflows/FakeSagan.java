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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

/**
 * Just enough of api.spring.io to serve a project's generations: 6.5.x has ended its OSS
 * support, 7.0.x and 7.1.x are in it, and 7.2.x has not been released.
 */
final class FakeSagan implements AutoCloseable {

	private final HttpServer server;

	final List<String> requests = new ArrayList<>();

	FakeSagan() throws IOException {
		this(null);
	}

	/**
	 * @param extraGeneration the name of one more generation, which is in OSS support
	 */
	FakeSagan(String extraGeneration) throws IOException {
		LocalDate today = LocalDate.now();
		String generations = """
				{"_embedded":{"generations":[
				{"name":"7.2.x","initialReleaseDate":"%s","ossSupportEndDate":"%s","commercialSupportEndDate":"%s"},
				{"name":"6.5.x","initialReleaseDate":"%s","ossSupportEndDate":"%s","commercialSupportEndDate":"%s"},
				{"name":"7.1.x","initialReleaseDate":"%s","ossSupportEndDate":"%s","commercialSupportEndDate":"%s"},
				{"name":"7.0.x","initialReleaseDate":"%s","ossSupportEndDate":"%s","commercialSupportEndDate":"%s"}
				]}}
				""".formatted(today.plusMonths(4), today.plusYears(1), today.plusYears(2), today.minusYears(2),
				today.minusMonths(1), today.plusYears(1), today.minusMonths(1), today.plusYears(1), today.plusYears(2),
				today.minusYears(1), today.plusMonths(6), today.plusYears(1));
		if (extraGeneration != null) {
			generations = generations.replace("\"generations\":[\n",
					"\"generations\":[\n{\"name\":\"" + extraGeneration + "\",\"initialReleaseDate\":\""
							+ today.minusYears(1) + "\",\"ossSupportEndDate\":\"" + today.plusYears(1)
							+ "\",\"commercialSupportEndDate\":\"" + today.plusYears(2) + "\"},\n");
		}
		String body = generations;
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", (exchange) -> {
			synchronized (this.requests) {
				this.requests.add(exchange.getRequestURI().getPath());
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		this.server.start();
	}

	String url() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

}
