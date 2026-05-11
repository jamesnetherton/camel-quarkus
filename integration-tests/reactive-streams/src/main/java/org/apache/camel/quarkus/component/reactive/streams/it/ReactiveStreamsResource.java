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
package org.apache.camel.quarkus.component.reactive.streams.it;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.camel.CamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.reactive.streams.ReactiveStreamsComponent;
import org.apache.camel.component.reactive.streams.ReactiveStreamsEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsServiceFactory;
import org.apache.camel.quarkus.component.reactive.streams.it.model.TestEntity;
import org.apache.camel.quarkus.component.reactive.streams.it.support.TestSubscriber;
import org.apache.camel.quarkus.core.ReactiveConsumerTemplate;
import org.apache.camel.quarkus.core.ReactiveProducerTemplate;

@Path("/reactive-streams")
@ApplicationScoped
public class ReactiveStreamsResource {
    @Inject
    CamelContext camelContext;
    @Inject
    FluentProducerTemplate producerTemplate;
    @Inject
    ProducerTemplate syncProducerTemplate;
    @Inject
    CamelReactiveStreamsService reactiveStreamsService;
    @Inject
    CamelReactiveStreamsServiceFactory reactiveStreamsServiceFactory;
    @Inject
    ReactiveProducerTemplate reactiveProducerTemplate;
    @Inject
    ReactiveConsumerTemplate reactiveConsumerTemplate;
    @Inject
    EntityManager entityManager;

    @Path("/inspect")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject get() {
        ReactiveStreamsComponent component = camelContext.getComponent("reactive-streams", ReactiveStreamsComponent.class);
        ReactiveStreamsEndpoint endpoint = camelContext.getEndpointRegistry().values().stream()
                .filter(ReactiveStreamsEndpoint.class::isInstance)
                .map(ReactiveStreamsEndpoint.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unable to find and endpoint of type ReactiveStreamsEndpoint"));

        return Json.createObjectBuilder()
                .add("reactive-streams-component-type", component.getClass().getName())
                .add("reactive-streams-component-backpressure-strategy", component.getBackpressureStrategy().toString())
                .add("reactive-streams-endpoint-backpressure-strategy", endpoint.getBackpressureStrategy().toString())
                .add("reactive-streams-service-type", reactiveStreamsService.getClass().getName())
                .add("reactive-streams-service-factory-type", reactiveStreamsServiceFactory.getClass().getName())
                .build();
    }

    @Path("/to-upper")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String toUpper(String payload) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();

        TestSubscriber<String> subscriber = TestSubscriber.onNext(data -> {
            result.set(data);
            latch.countDown();
        });

        subscriber.setInitiallyRequested(1);
        reactiveStreamsService.fromStream("toUpper", String.class).subscribe(subscriber);

        producerTemplate.to("direct:toUpper").withBody(payload).send();

        latch.await(5, TimeUnit.SECONDS);

        return result.get();
    }

    @Path("/template/stream-to")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> templateStreamTo() {
        Multi<String> stream = Multi.createFrom().items("a", "b", "c");
        return reactiveProducerTemplate.streamTo("seda:streamQueue", stream)
                .map(v -> "streamed");
    }

    @Path("/template/stream-from")
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> templateStreamFrom() {
        try {
            // Start the timer route to generate events
            camelContext.getRouteController().startRoute("streamEvents");
            System.out.println("Started streamEvents route");
        } catch (Exception e) {
            throw new RuntimeException("Failed to start streamEvents route", e);
        }

        System.out.println("Starting to stream from SEDA queue using auto-bridge...");
        return reactiveConsumerTemplate.streamFromEndpoint("seda:streamQueue", String.class)
                .onItem().invoke(item -> System.out.println("Streaming: " + item))
                .select().first(5);
    }

    @Path("/template/stream-from-name")
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> templateStreamFromName() {
        try {
            // Start the timer route to generate events to the named stream
            camelContext.getRouteController().startRoute("namedStreamEvents");
            System.out.println("Started namedStreamEvents route");
        } catch (Exception e) {
            throw new RuntimeException("Failed to start namedStreamEvents route", e);
        }

        // Use streamFrom() with a stream name (not endpoint URI)
        // This requires a route that sends to reactive-streams:streamName
        return reactiveConsumerTemplate.streamFrom("namedStream", String.class)
                .onItem().invoke(item -> System.out.println("Streaming from namedStream: " + item))
                .select().first(3);
    }

    @Path("/template/stream-from-exchange")
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> templateStreamFromExchange() {
        try {
            // Start the timer route to generate events to the exchange stream
            camelContext.getRouteController().startRoute("exchangeStreamEvents");
            System.out.println("Started exchangeStreamEvents route");
        } catch (Exception e) {
            throw new RuntimeException("Failed to start exchangeStreamEvents route", e);
        }

        // Use streamFrom() with Exchange variant
        return reactiveConsumerTemplate.streamFrom("exchangeStream")
                .onItem().invoke(exchange -> System.out
                        .println("Streaming exchange from exchangeStream: " + exchange.getIn().getBody(String.class)))
                .map(exchange -> exchange.getIn().getBody(String.class))
                .select().first(3);
    }

    // JMS Tests
    @Path("/jms/send/{count}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String jmsSendMessages(int count) {
        for (int i = 1; i <= count; i++) {
            syncProducerTemplate.sendBody("jms:queue:test", "jms-message-" + i);
        }
        return "sent-" + count;
    }

    @Path("/jms/stream-first/{count}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject jmsStreamFirst(int count) {
        AtomicInteger received = new AtomicInteger(0);
        AtomicInteger processed = new AtomicInteger(0);

        try {
            // Stream from JMS but only take 'count' messages
            reactiveConsumerTemplate.streamFromEndpoint("jms:queue:test?transacted=true", String.class)
                    .onItem().invoke(msg -> {
                        received.incrementAndGet();
                        System.out.println("JMS Received: " + msg);
                    })
                    .select().first(count)
                    .onItem().invoke(msg -> {
                        processed.incrementAndGet();
                        System.out.println("JMS Processed: " + msg);
                    })
                    .collect().asList()
                    .await().atMost(java.time.Duration.ofSeconds(10));
        } catch (Exception e) {
            System.err.println("JMS streaming error: " + e.getMessage());
        }

        // Give Camel time to complete any inflight exchanges
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check how many messages remain in the queue
        long remaining = countJmsMessages();

        return Json.createObjectBuilder()
                .add("received", received.get())
                .add("processed", processed.get())
                .add("remaining", remaining)
                .build();
    }

    @Path("/jms/count")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public long jmsCount() {
        return countJmsMessages();
    }

    private long countJmsMessages() {
        // Use Camel JMS component to browse queue depth
        try {
            String result = syncProducerTemplate.requestBody(
                    "jms:queue:test?disableReplyTo=true&receiveTimeout=100",
                    (Object) null,
                    String.class);
            if (result != null) {
                int count = 1;
                // Keep consuming until null
                while (syncProducerTemplate.requestBody(
                        "jms:queue:test?disableReplyTo=true&receiveTimeout=100",
                        (Object) null,
                        String.class) != null) {
                    count++;
                    if (count > 100)
                        break; // safety
                }
                return count;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // JPA Tests
    @Path("/jpa/seed/{count}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public String jpaSeedEntities(int count) {
        for (int i = 1; i <= count; i++) {
            entityManager.persist(new TestEntity("entity-" + i));
        }
        entityManager.flush();
        return "seeded-" + count;
    }

    @Path("/jpa/stream-first/{count}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject jpaStreamFirst(int count) {
        AtomicInteger received = new AtomicInteger(0);
        AtomicInteger processed = new AtomicInteger(0);

        try {
            // Stream from JPA but only take 'count' entities
            reactiveConsumerTemplate
                    .streamFromEndpoint(
                            "jpa:org.apache.camel.quarkus.component.reactive.streams.it.model.TestEntity"
                                    + "?namedQuery=findUnprocessed&consumeDelete=false&delay=100",
                            TestEntity.class)
                    .onItem().invoke(entity -> {
                        received.incrementAndGet();
                        System.out.println("JPA Received: " + entity.getMessage());
                    })
                    .select().first(count)
                    .onItem().invoke(entity -> {
                        processed.incrementAndGet();
                        System.out.println("JPA Processed: " + entity.getMessage());
                    })
                    .collect().asList()
                    .await().atMost(java.time.Duration.ofSeconds(10));
        } catch (Exception e) {
            System.err.println("JPA streaming error: " + e.getMessage());
        }

        // Give Camel time to complete any inflight exchanges
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check how many unprocessed entities remain
        long remaining = countUnprocessedEntities();

        return Json.createObjectBuilder()
                .add("received", received.get())
                .add("processed", processed.get())
                .add("remaining", remaining)
                .build();
    }

    @Path("/jpa/count")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public long jpaCount() {
        return countUnprocessedEntities();
    }

    @Transactional
    long countUnprocessedEntities() {
        return entityManager
                .createQuery("SELECT COUNT(e) FROM TestEntity e WHERE e.processed = false", Long.class)
                .getSingleResult();
    }

}
