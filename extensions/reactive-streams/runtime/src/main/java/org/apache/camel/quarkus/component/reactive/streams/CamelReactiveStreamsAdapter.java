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
package org.apache.camel.quarkus.component.reactive.streams;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.smallrye.mutiny.Multi;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.quarkus.core.ReactiveStreamsAdapter;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Implementation of {@link ReactiveStreamsAdapter} that bridges Mutiny and Camel reactive-streams.
 * <p>
 * This adapter uses {@link CamelReactiveStreamsService} to convert between Mutiny's {@link Multi} and
 * Reactive Streams Publisher/Subscriber.
 */
public class CamelReactiveStreamsAdapter implements ReactiveStreamsAdapter {

    private final CamelContext camelContext;
    private volatile CamelReactiveStreamsService reactiveStreamsService;

    // Counter for generating unique stream names
    private final AtomicInteger bridgeCounter = new AtomicInteger(0);

    public CamelReactiveStreamsAdapter(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    private CamelReactiveStreamsService getService() {
        if (reactiveStreamsService == null) {
            synchronized (this) {
                if (reactiveStreamsService == null) {
                    try {
                        reactiveStreamsService = CamelReactiveStreams.get(camelContext);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to get CamelReactiveStreamsService", e);
                    }
                }
            }
        }
        return reactiveStreamsService;
    }

    @Override
    public <T> Multi<T> streamFrom(String endpointUri, Class<T> type) {
        Publisher<T> publisher = getService().fromStream(endpointUri, type);
        return Multi.createFrom().publisher(FlowAdapters.toFlowPublisher(publisher));
    }

    @Override
    public Multi<Exchange> streamFromExchange(String endpointUri) {
        Publisher<Exchange> publisher = getService().fromStream(endpointUri, Exchange.class);
        return Multi.createFrom().publisher(FlowAdapters.toFlowPublisher(publisher));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void streamTo(String endpointUri, Multi<T> stream) {
        Subscriber<Object> subscriber = getService().streamSubscriber(endpointUri, Object.class);
        ((Multi<Object>) stream).subscribe().withSubscriber(FlowAdapters.toFlowSubscriber(subscriber));
    }

    @Override
    public <T> Multi<T> streamFromEndpoint(String endpointUri, Class<T> type) {
        // Generate unique stream name and route ID for this stream
        int bridgeId = bridgeCounter.incrementAndGet();
        String streamName = "auto-bridge-" + bridgeId;
        String routeId = "auto-bridge-route-" + bridgeId;

        try {
            createBridge(endpointUri, streamName, routeId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create reactive-streams bridge for endpoint: " + endpointUri, e);
        }

        // Create the stream and add cleanup on any termination (completion, cancellation, or failure)
        // NOTE: If the stream terminates early (e.g., .first(N)), any remaining messages in the
        // source endpoint will be left unprocessed since the bridge route is removed.
        // This is by design for reactive streaming scenarios but means this method is NOT suitable
        // for draining finite queues where all messages must be processed.
        //
        // The route is fully removed (not just stopped) to ensure all resources are properly released.
        // This is important for components that manage resources like database connections, JMS sessions, etc.
        Multi<T> stream = streamFrom(streamName, type);

        // Add cleanup handlers for all termination scenarios
        return stream
                .onCompletion().invoke(() -> removeRoute(routeId))
                .onCancellation().invoke(() -> removeRoute(routeId))
                .onFailure().invoke(failure -> removeRoute(routeId));
    }

    private void createBridge(String endpointUri, String streamName, String routeId) throws Exception {
        // Create a route that bridges the endpoint to reactive-streams
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpointUri)
                        .routeId(routeId)
                        .shutdownRoute(ShutdownRoute.Defer) // Shutdown last, after other routes
                        .shutdownRunningTask(ShutdownRunningTask.CompleteCurrentTaskOnly) // Don't wait for all tasks
                        // Use BUFFER backpressure strategy to ensure all messages from the source endpoint
                        // are available to the stream. This prevents message loss when the subscriber is slow.
                        // Alternative strategies (LATEST/OLDEST) drop messages which would be surprising when
                        // bridging from queues or other endpoints where users expect all messages to be consumable.
                        .to("reactive-streams:" + streamName + "?backpressureStrategy=BUFFER");
            }
        });
    }

    private void removeRoute(String routeId) {
        try {
            // Stop and remove the route to ensure all resources are properly released
            camelContext.getRouteController().stopRoute(routeId, 1, TimeUnit.MILLISECONDS);
            camelContext.removeRoute(routeId);
        } catch (Exception e) {
            // Log but don't fail - route cleanup is best-effort
            System.err.println("Failed to remove bridge route " + routeId + ": " + e.getMessage());
        }
    }
}
