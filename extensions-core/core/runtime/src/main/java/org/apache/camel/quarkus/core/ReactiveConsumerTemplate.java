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

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * Reactive wrapper for {@link ConsumerTemplate} that provides non-blocking methods returning {@link Uni}.
 * <p>
 * Since Camel's {@link ConsumerTemplate} does not have async methods like {@link org.apache.camel.ProducerTemplate},
 * this wrapper executes blocking receive operations on a worker thread pool to prevent blocking the event loop.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;Inject
 * ReactiveConsumerTemplate template;
 *
 * &#64;GET
 * &#64;Path("/poll")
 * public Uni&lt;String&gt; poll() {
 *     return template.receiveBody("seda:incoming", 5000L, String.class);
 * }
 * </pre>
 *
 * <b>Note:</b> While this template makes consuming reactive, the underlying operations are still blocking.
 * For truly reactive consumption, consider using Camel's reactive-streams component with
 * {@link org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService}.
 */
public interface ReactiveConsumerTemplate {

    /**
     * Receives an exchange from an endpoint with a timeout, executing on a worker thread.
     * <p>
     * Returns null if no message is received within the timeout.
     *
     * @param  endpointUri the endpoint URI to receive from
     * @param  timeout     the timeout in milliseconds
     * @return             a {@link Uni} that emits the exchange or null if timeout occurs
     */
    Uni<Exchange> receive(String endpointUri, long timeout);

    /**
     * Receives an exchange from an endpoint with a timeout, executing on a worker thread.
     * <p>
     * Returns null if no message is received within the timeout.
     *
     * @param  endpoint the endpoint to receive from
     * @param  timeout  the timeout in milliseconds
     * @return          a {@link Uni} that emits the exchange or null if timeout occurs
     */
    Uni<Exchange> receive(Endpoint endpoint, long timeout);

    /**
     * Receives an exchange from an endpoint without waiting, executing on a worker thread.
     * <p>
     * Returns null immediately if no message is available.
     *
     * @param  endpointUri the endpoint URI to receive from
     * @return             a {@link Uni} that emits the exchange or null if no message available
     */
    Uni<Exchange> receiveNoWait(String endpointUri);

    /**
     * Receives an exchange from an endpoint without waiting, executing on a worker thread.
     * <p>
     * Returns null immediately if no message is available.
     *
     * @param  endpoint the endpoint to receive from
     * @return          a {@link Uni} that emits the exchange or null if no message available
     */
    Uni<Exchange> receiveNoWait(Endpoint endpoint);

    /**
     * Receives a message body from an endpoint with a timeout, executing on a worker thread.
     * <p>
     * Returns null if no message is received within the timeout.
     *
     * @param  endpointUri the endpoint URI to receive from
     * @param  timeout     the timeout in milliseconds
     * @return             a {@link Uni} that emits the message body or null if timeout occurs
     */
    Uni<Object> receiveBody(String endpointUri, long timeout);

    /**
     * Receives a message body from an endpoint with a timeout, executing on a worker thread.
     * <p>
     * Returns null if no message is received within the timeout.
     *
     * @param  endpoint the endpoint to receive from
     * @param  timeout  the timeout in milliseconds
     * @return          a {@link Uni} that emits the message body or null if timeout occurs
     */
    Uni<Object> receiveBody(Endpoint endpoint, long timeout);

    /**
     * Receives a message body from an endpoint with a timeout and converts to the specified type, executing on a worker
     * thread.
     * <p>
     * Returns null if no message is received within the timeout.
     *
     * @param  endpointUri the endpoint URI to receive from
     * @param  timeout     the timeout in milliseconds
     * @param  type        the expected body type
     * @param  <T>         the body type
     * @return             a {@link Uni} that emits the message body converted to the specified type or null if timeout
     *                     occurs
     */
    <T> Uni<T> receiveBody(String endpointUri, long timeout, Class<T> type);

    /**
     * Receives a message body from an endpoint with a timeout and converts to the specified type, executing on a worker
     * thread.
     * <p>
     * Returns null if no message is received within the timeout.
     *
     * @param  endpoint the endpoint to receive from
     * @param  timeout  the timeout in milliseconds
     * @param  type     the expected body type
     * @param  <T>      the body type
     * @return          a {@link Uni} that emits the message body converted to the specified type or null if timeout occurs
     */
    <T> Uni<T> receiveBody(Endpoint endpoint, long timeout, Class<T> type);

    /**
     * Receives a message body from an endpoint without waiting, executing on a worker thread.
     * <p>
     * Returns null immediately if no message is available.
     *
     * @param  endpointUri the endpoint URI to receive from
     * @return             a {@link Uni} that emits the message body or null if no message available
     */
    Uni<Object> receiveBodyNoWait(String endpointUri);

    /**
     * Receives a message body from an endpoint without waiting, executing on a worker thread.
     * <p>
     * Returns null immediately if no message is available.
     *
     * @param  endpoint the endpoint to receive from
     * @return          a {@link Uni} that emits the message body or null if no message available
     */
    Uni<Object> receiveBodyNoWait(Endpoint endpoint);

    /**
     * Receives a message body from an endpoint without waiting and converts to the specified type, executing on a worker
     * thread.
     * <p>
     * Returns null immediately if no message is available.
     *
     * @param  endpointUri the endpoint URI to receive from
     * @param  type        the expected body type
     * @param  <T>         the body type
     * @return             a {@link Uni} that emits the message body converted to the specified type or null if no message
     *                     available
     */
    <T> Uni<T> receiveBodyNoWait(String endpointUri, Class<T> type);

    /**
     * Receives a message body from an endpoint without waiting and converts to the specified type, executing on a worker
     * thread.
     * <p>
     * Returns null immediately if no message is available.
     *
     * @param  endpoint the endpoint to receive from
     * @param  type     the expected body type
     * @param  <T>      the body type
     * @return          a {@link Uni} that emits the message body converted to the specified type or null if no message
     *                  available
     */
    <T> Uni<T> receiveBodyNoWait(Endpoint endpoint, Class<T> type);

    /**
     * Creates a reactive stream from a reactive-streams stream name.
     * <p>
     * This method expects a stream name that has been registered with the reactive-streams component,
     * typically via a route like: {@code from("seda:events").to("reactive-streams:my-stream")}
     * <p>
     * For convenience when working with arbitrary Camel endpoints, use {@link #streamFromEndpoint(String, Class)}
     * which automatically creates the necessary bridge.
     * <p>
     * Example usage:
     *
     * <pre>
     * // In a route
     * from("seda:events").to("reactive-streams:event-stream");
     *
     * // In your code
     * template.streamFrom("event-stream", String.class)
     *         .onItem().transform(String::toUpperCase)
     *         .subscribe().with(item -&gt; System.out.println(item));
     * </pre>
     *
     * <b>Note:</b> This method requires the camel-quarkus-reactive-streams extension to be present.
     *
     * @param  streamName the reactive-streams stream name (not a Camel endpoint URI)
     * @param  type       the expected item type
     * @param  <T>        the item type
     * @return            a {@link Multi} that emits items from the stream
     */
    <T> Multi<T> streamFrom(String streamName, Class<T> type);

    /**
     * Creates a reactive stream of exchanges from a reactive-streams stream name.
     * <p>
     * This method expects a stream name that has been registered with the reactive-streams component.
     * For convenience when working with arbitrary Camel endpoints, use {@link #streamFromEndpoint(String, Class)}.
     *
     * <b>Note:</b> This method requires the camel-quarkus-reactive-streams extension to be present.
     *
     * @param  streamName the reactive-streams stream name (not a Camel endpoint URI)
     * @return            a {@link Multi} that emits exchanges from the stream
     */
    Multi<Exchange> streamFrom(String streamName);

    /**
     * Creates a reactive stream from any Camel endpoint by automatically creating a reactive-streams bridge.
     * <p>
     * Unlike {@link #streamFrom(String, Class)} which expects a reactive-streams stream name, this method
     * accepts any Camel endpoint URI and automatically creates the necessary bridge route.
     * <p>
     * The bridge routes are cached and reused for the same endpoint URI. They are automatically cleaned up
     * when the stream terminates (completion, cancellation, or failure).
     * <p>
     * Example usage:
     *
     * <pre>
     * template.streamFromEndpoint("seda:events", String.class)
     *         .onItem().transform(String::toUpperCase)
     *         .subscribe().with(item -&gt; System.out.println(item));
     * </pre>
     *
     * <b>Important limitations:</b>
     * <ul>
     * <li>Designed for streaming/reactive scenarios where you control termination</li>
     * <li>If you terminate early (e.g., {@code .select().first(5)}), remaining messages in the source
     * endpoint will be left unprocessed</li>
     * <li>Suitable for infinite streams or on-demand event generation</li>
     * <li>NOT suitable for draining finite queues where all messages must be processed</li>
     * </ul>
     *
     * <b>Note:</b> This method requires the camel-quarkus-reactive-streams extension to be present.
     *
     * @param  endpointUri the Camel endpoint URI to stream from (e.g., "seda:events", "direct:notifications")
     * @param  type        the expected item type
     * @param  <T>         the item type
     * @return             a {@link Multi} that emits items from the endpoint
     */
    <T> Multi<T> streamFromEndpoint(String endpointUri, Class<T> type);

    /**
     * Gets the underlying {@link ConsumerTemplate}.
     *
     * @return the underlying consumer template
     */
    ConsumerTemplate getConsumerTemplate();
}
