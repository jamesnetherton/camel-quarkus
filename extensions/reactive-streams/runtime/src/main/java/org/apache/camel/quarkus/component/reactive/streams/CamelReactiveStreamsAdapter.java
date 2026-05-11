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

import java.util.concurrent.ConcurrentHashMap;
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

    // Track auto-created bridge routes: endpointUri -> BridgeInfo
    private final ConcurrentHashMap<String, BridgeInfo> endpointBridges = new ConcurrentHashMap<>();

    private static class BridgeInfo {
        final String streamName;
        final String routeId;
        final AtomicInteger refCount;
        volatile boolean cleanupPending = false;

        BridgeInfo(String streamName, String routeId) {
            this.streamName = streamName;
            this.routeId = routeId;
            this.refCount = new AtomicInteger(0);
        }
    }

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
        BridgeInfo bridge = endpointBridges.computeIfAbsent(endpointUri, uri -> {
            try {
                return createBridge(uri);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create reactive-streams bridge for endpoint: " + uri, e);
            }
        });

        // Resume the route if it was previously suspended
        try {
            if (!camelContext.getRouteController().getRouteStatus(bridge.routeId).isStarted()) {
                camelContext.getRouteController().resumeRoute(bridge.routeId);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resume bridge route: " + bridge.routeId, e);
        }

        // Increment reference count
        bridge.refCount.incrementAndGet();

        // Create the stream and add cleanup on any termination (completion, cancellation, or failure)
        // NOTE: If the stream terminates early (e.g., .first(N)), any remaining messages in the
        // source endpoint will be left unprocessed since the bridge route is suspended.
        // This is by design for reactive streaming scenarios but means this method is NOT suitable
        // for draining finite queues where all messages must be processed.
        //
        // We mark cleanup as pending rather than cleaning up immediately to avoid suspending the route
        // while the last exchange is still in-flight. The route's onCompletion() handler will
        // perform the actual cleanup after the final exchange completes.
        //
        // The route is suspended (not removed) so it can be reused if streamFromEndpoint() is called
        // again for the same endpoint URI.
        return streamFrom(bridge.streamName, type)
                .onCompletion().invoke(() -> markForCleanup(endpointUri, bridge))
                .onCancellation().invoke(() -> markForCleanup(endpointUri, bridge))
                .onFailure().invoke(failure -> markForCleanup(endpointUri, bridge));
    }

    private BridgeInfo createBridge(String endpointUri) throws Exception {
        // Generate a unique stream name based on the endpoint URI
        String streamName = "auto-bridge-" + Math.abs(endpointUri.hashCode());
        String routeId = "auto-bridge-route-" + streamName;

        BridgeInfo bridge = new BridgeInfo(streamName, routeId);

        // Create a route that bridges the endpoint to reactive-streams
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpointUri)
                        .routeId(routeId)
                        .shutdownRoute(ShutdownRoute.Defer) // Shutdown last, after other routes
                        .shutdownRunningTask(ShutdownRunningTask.CompleteCurrentTaskOnly) // Don't wait for all tasks
                        .to("reactive-streams:" + streamName)
                        .onCompletion()
                            // After each exchange completes, check if cleanup is pending
                            .process(exchange -> {
                                if (bridge.cleanupPending) {
                                    performCleanup(endpointUri, bridge);
                                }
                            })
                        .end();
            }
        });

        return bridge;
    }

    private void markForCleanup(String endpointUri, BridgeInfo bridge) {
        int count = bridge.refCount.decrementAndGet();
        if (count <= 0) {
            // Mark that cleanup should happen after the next exchange completes
            // The route's onCompletion() handler will perform the actual cleanup
            bridge.cleanupPending = true;
        }
    }

    private void performCleanup(String endpointUri, BridgeInfo bridge) {
        // Only cleanup if still marked as pending (avoid duplicate cleanup)
        if (bridge.cleanupPending) {
            bridge.cleanupPending = false;
            // Keep the bridge in the map for potential reuse - just suspend the route
            try {
                // Suspend the route (don't remove it) so it can be reused
                // The route is configured with ShutdownRunningTask.CompleteCurrentTaskOnly
                camelContext.getRouteController().suspendRoute(bridge.routeId, 1, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // Log but don't fail - route cleanup is best-effort
                System.err.println("Failed to suspend bridge route " + bridge.routeId + ": " + e.getMessage());
            }
        }
    }
}
