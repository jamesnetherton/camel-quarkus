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
import org.apache.camel.Exchange;

/**
 * Adapter interface for bridging between Mutiny reactive types and Camel reactive-streams.
 * <p>
 * This interface is implemented by the camel-quarkus-reactive-streams extension to provide streaming support.
 * A default no-op implementation is available when the extension is not present.
 */
public interface ReactiveStreamsAdapter {

    /**
     * Creates a reactive stream from a Camel endpoint.
     *
     * @param  endpointUri the endpoint URI to stream from
     * @param  type        the expected item type
     * @param  <T>         the item type
     * @return             a {@link Multi} that emits items from the endpoint
     */
    <T> Multi<T> streamFrom(String endpointUri, Class<T> type);

    /**
     * Creates a reactive stream of exchanges from a Camel endpoint.
     *
     * @param  endpointUri the endpoint URI to stream from
     * @return             a {@link Multi} that emits exchanges from the endpoint
     */
    Multi<Exchange> streamFromExchange(String endpointUri);

    /**
     * Sends a reactive stream of items to a Camel endpoint.
     *
     * @param endpointUri the endpoint URI to send to
     * @param stream      the Multi stream of items to send
     * @param <T>         the item type
     */
    <T> void streamTo(String endpointUri, Multi<T> stream);

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
     * <b>Important:</b> This method is designed for streaming/reactive scenarios where you control termination.
     * If you terminate the stream early (e.g., {@code .select().first(5)}), any remaining messages in the source
     * endpoint will be left unprocessed. This is suitable for infinite streams or on-demand event generation,
     * but NOT for draining finite queues where all messages must be processed.
     *
     * @param  endpointUri the Camel endpoint URI to stream from (e.g., "seda:events", "direct:notifications")
     * @param  type        the expected item type
     * @param  <T>         the item type
     * @return             a {@link Multi} that emits items from the endpoint
     */
    <T> Multi<T> streamFromEndpoint(String endpointUri, Class<T> type);
}
