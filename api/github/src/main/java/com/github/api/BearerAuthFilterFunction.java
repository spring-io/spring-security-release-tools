/*
 * Copyright 2020-2023 the original author or authors.
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
package com.github.api;

import reactor.core.publisher.Mono;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

/**
 * @author Steve Riesenberg
 */
final class BearerAuthFilterFunction implements ExchangeFilterFunction {
	private final String accessToken;

	/**
	 * @param accessToken Optional access token used to add an Authorization header to requests (if not-null)
	 */
	public BearerAuthFilterFunction(String accessToken) {
		this.accessToken = accessToken;
	}

	@Override
	public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
		if (this.accessToken == null) {
			return next.exchange(request);
		}

		ClientRequest newRequest = ClientRequest.from(request)
				.headers((headers) -> headers.setBearerAuth(this.accessToken))
				.build();
		return next.exchange(newRequest);
	}
}
