/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.core;

import java.util.Map;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;

/**
 * Reactive wrapper for {@link ProducerTemplate} that provides non-blocking methods returning {@link Uni}.
 * <p>
 * This template leverages Camel's existing async methods and wraps them with Mutiny's {@link Uni} type,
 * making it natural for Quarkus reactive applications.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;Inject
 * ReactiveProducerTemplate template;
 *
 * &#64;GET
 * &#64;Path("/process")
 * public Uni&lt;String&gt; process(String data) {
 *     return template.requestBody("direct:process", data, String.class);
 * }
 * </pre>
 */
public interface ReactiveProducerTemplate {

    /**
     * Sends an asynchronous body to an endpoint and returns a {@link Uni} with the result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  body        the request body
     * @return             a {@link Uni} that emits the response body
     */
    Uni<Object> requestBody(String endpointUri, Object body);

    /**
     * Sends an asynchronous body to an endpoint with a specific type and returns a {@link Uni} with the result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  body        the request body
     * @param  type        the expected response type
     * @param  <T>         the response type
     * @return             a {@link Uni} that emits the response body converted to the specified type
     */
    <T> Uni<T> requestBody(String endpointUri, Object body, Class<T> type);

    /**
     * Sends an asynchronous body to an endpoint and returns a {@link Uni} with the result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpoint the endpoint to send to
     * @param  body     the request body
     * @return          a {@link Uni} that emits the response body
     */
    Uni<Object> requestBody(Endpoint endpoint, Object body);

    /**
     * Sends an asynchronous body to an endpoint with a specific type and returns a {@link Uni} with the result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpoint the endpoint to send to
     * @param  body     the request body
     * @param  type     the expected response type
     * @param  <T>      the response type
     * @return          a {@link Uni} that emits the response body converted to the specified type
     */
    <T> Uni<T> requestBody(Endpoint endpoint, Object body, Class<T> type);

    /**
     * Sends an asynchronous body with a header to an endpoint and returns a {@link Uni} with the result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  body        the request body
     * @param  header      the header name
     * @param  headerValue the header value
     * @return             a {@link Uni} that emits the response body
     */
    Uni<Object> requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue);

    /**
     * Sends an asynchronous body with a header to an endpoint with a specific type and returns a {@link Uni} with the
     * result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  body        the request body
     * @param  header      the header name
     * @param  headerValue the header value
     * @param  type        the expected response type
     * @param  <T>         the response type
     * @return             a {@link Uni} that emits the response body converted to the specified type
     */
    <T> Uni<T> requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue, Class<T> type);

    /**
     * Sends an asynchronous body with headers to an endpoint and returns a {@link Uni} with the result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  body        the request body
     * @param  headers     the headers
     * @return             a {@link Uni} that emits the response body
     */
    Uni<Object> requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers);

    /**
     * Sends an asynchronous body with headers to an endpoint with a specific type and returns a {@link Uni} with the
     * result.
     * <p>
     * Uses the InOut message exchange pattern (request-reply).
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  body        the request body
     * @param  headers     the headers
     * @param  type        the expected response type
     * @param  <T>         the response type
     * @return             a {@link Uni} that emits the response body converted to the specified type
     */
    <T> Uni<T> requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type);

    /**
     * Sends a body asynchronously to an endpoint.
     * <p>
     * Uses the default message exchange pattern (typically InOnly for fire-and-forget).
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  body        the message body
     * @return             a {@link Uni} that emits the sent body or null for InOnly
     */
    Uni<Object> sendBody(String endpointUri, Object body);

    /**
     * Sends a body asynchronously to an endpoint.
     * <p>
     * Uses the default message exchange pattern (typically InOnly for fire-and-forget).
     *
     * @param  endpoint the endpoint to send to
     * @param  body     the message body
     * @return          a {@link Uni} that emits the sent body or null for InOnly
     */
    Uni<Object> sendBody(Endpoint endpoint, Object body);

    /**
     * Sends an exchange asynchronously to an endpoint using a processor.
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  processor   the processor to prepare the exchange
     * @return             a {@link Uni} that emits the processed exchange
     */
    Uni<Exchange> send(String endpointUri, Processor processor);

    /**
     * Sends an exchange asynchronously to an endpoint using a processor.
     *
     * @param  endpoint  the endpoint to send to
     * @param  processor the processor to prepare the exchange
     * @return           a {@link Uni} that emits the processed exchange
     */
    Uni<Exchange> send(Endpoint endpoint, Processor processor);

    /**
     * Sends an exchange asynchronously to an endpoint.
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  exchange    the exchange to send
     * @return             a {@link Uni} that emits the sent exchange
     */
    Uni<Exchange> send(String endpointUri, Exchange exchange);

    /**
     * Sends an exchange asynchronously to an endpoint.
     *
     * @param  endpoint the endpoint to send to
     * @param  exchange the exchange to send
     * @return          a {@link Uni} that emits the sent exchange
     */
    Uni<Exchange> send(Endpoint endpoint, Exchange exchange);

    /**
     * Sends a reactive stream of items to a Camel endpoint using Camel's reactive-streams support.
     * <p>
     * This method provides true reactive streaming production by bridging Mutiny's {@link Multi} to Camel endpoints.
     * Items from the Multi stream are sent to the endpoint as they are emitted.
     * <p>
     * Example usage:
     *
     * <pre>
     * Multi&lt;String&gt; stream = Multi.createFrom().items("a", "b", "c");
     * return template.streamTo("seda:output", stream);
     * </pre>
     *
     * <b>Note:</b> This method requires the camel-quarkus-reactive-streams extension to be present.
     *
     * @param  endpointUri the endpoint URI to send to
     * @param  stream      the Multi stream of items to send
     * @param  <T>         the item type
     * @return             a {@link Uni} that completes when the stream has been fully sent
     */
    <T> Uni<Void> streamTo(String endpointUri, Multi<T> stream);

    /**
     * Gets the underlying {@link ProducerTemplate}.
     *
     * @return the underlying producer template
     */
    ProducerTemplate getProducerTemplate();
}
