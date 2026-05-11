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

import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.sse.SseEventSource;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ReactiveStreamsTest {

    @Inject
    @RestClient
    ReactiveStreamsClient reactiveStreamsClient;

    @Test
    public void reactiveStreamsService() {
        JsonPath result = RestAssured.get("/reactive-streams/inspect")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        assertThat(result.getString("reactive-streams-component-type")).isEqualTo(
                "org.apache.camel.quarkus.component.reactive.streams.ReactiveStreamsRecorder$QuarkusReactiveStreamsComponent");
        assertThat(result.getString("reactive-streams-component-backpressure-strategy")).isEqualTo(
                "LATEST");
        assertThat(result.getString("reactive-streams-endpoint-backpressure-strategy")).isEqualTo(
                "BUFFER");
        assertThat(result.getString("reactive-streams-service-type")).isEqualTo(
                "org.apache.camel.component.reactive.streams.engine.DefaultCamelReactiveStreamsService");
        assertThat(result.getString("reactive-streams-service-factory-type")).isEqualTo(
                "org.apache.camel.component.reactive.streams.engine.DefaultCamelReactiveStreamsServiceFactory");
    }

    @Test
    public void subscriber() {
        final String payload = "test";

        RestAssured.given()
                .body(payload)
                .post("/reactive-streams/to-upper")
                .then()
                .statusCode(200)
                .body(is(payload.toUpperCase()));
    }

    @Test
    void reactiveTemplateStreamTo() {
        RestAssured.given()
                .post("/reactive-streams/template/stream-to")
                .then()
                .statusCode(200)
                .body(is("streamed"));
    }

    @TestHTTPResource("/reactive-streams/template/stream-from")
    URI streamFromUri;

    @Test
    void reactiveTemplateStreamFrom() throws InterruptedException {
        var resultList = new CopyOnWriteArrayList<String>();

        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(streamFromUri);
        SseEventSource eventSource = SseEventSource.target(target).build();

        eventSource.register(event -> {
            String data = event.readData();
            System.out.println("Received: " + data);
            resultList.add(data);
        });

        eventSource.open();

        // Timer sends 5 events (event-1 through event-5)
        // Wait for them to arrive
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(resultList).hasSizeGreaterThanOrEqualTo(5));

        eventSource.close();
        client.close();

        // Verify we got the expected events
        assertThat(resultList)
                .contains("event-1", "event-2", "event-3", "event-4", "event-5");
    }

    @TestHTTPResource("/reactive-streams/template/stream-from-name")
    URI streamFromNameUri;

    @Test
    void reactiveTemplateStreamFromName() throws InterruptedException {
        var resultList = new CopyOnWriteArrayList<String>();

        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(streamFromNameUri);
        SseEventSource eventSource = SseEventSource.target(target).build();

        eventSource.register(event -> {
            String data = event.readData();
            System.out.println("Received from named stream: " + data);
            resultList.add(data);
        });

        eventSource.open();

        // Timer sends 3 events (named-1 through named-3)
        // Wait for them to arrive
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(resultList).hasSizeGreaterThanOrEqualTo(3));

        eventSource.close();
        client.close();

        // Verify we got the expected events
        assertThat(resultList)
                .contains("named-1", "named-2", "named-3");
    }

    @TestHTTPResource("/reactive-streams/template/stream-from-exchange")
    URI streamFromExchangeUri;

    @Test
    void reactiveTemplateStreamFromExchange() throws InterruptedException {
        var resultList = new CopyOnWriteArrayList<String>();

        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(streamFromExchangeUri);
        SseEventSource eventSource = SseEventSource.target(target).build();

        eventSource.register(event -> {
            String data = event.readData();
            System.out.println("Received exchange from exchange stream: " + data);
            resultList.add(data);
        });

        eventSource.open();

        // Timer sends 3 events (exchange-1 through exchange-3)
        // Wait for them to arrive
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(resultList).hasSizeGreaterThanOrEqualTo(3));

        eventSource.close();
        client.close();

        // Verify we got the expected events
        assertThat(resultList)
                .contains("exchange-1", "exchange-2", "exchange-3");
    }

    /**
     * Tests streamFromEndpoint() with JMS transactional component.
     *
     * This test verifies what happens when we terminate the stream early (.first(3))
     * while there are more messages in the queue. Key concerns:
     *
     * 1. Are messages ACKed before the subscriber processes them?
     * 2. Do remaining messages get lost or stay in the queue?
     * 3. Does early termination cause transaction issues?
     */
    @Test
    void jmsStreamWithEarlyTermination() {
        // Send 10 messages to JMS queue
        RestAssured.given()
                .post("/reactive-streams/jms/send/10")
                .then()
                .statusCode(200)
                .body(is("sent-10"));

        // Stream from JMS but only take the first 3
        JsonPath result = RestAssured.given()
                .get("/reactive-streams/jms/stream-first/3")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        int received = result.getInt("received");
        int processed = result.getInt("processed");
        int remaining = result.getInt("remaining");

        System.out.println("JMS Test Results:");
        System.out.println("  Received: " + received);
        System.out.println("  Processed: " + processed);
        System.out.println("  Remaining in queue: " + remaining);

        // Assertions - document actual behavior
        assertThat(processed).isEqualTo(3); // We asked for 3

        // CRITICAL FINDING: With transactional JMS, messages are ACKed BEFORE the reactive
        // subscriber processes them. This means early termination (.first(3)) causes data loss.
        // The bridge route commits the transaction as soon as it sends to reactive-streams,
        // not when the subscriber confirms processing.
        //
        // Result: All 10 messages were consumed from the queue (remaining = 0), but only 3
        // were processed by the subscriber. The other 7 are LOST.
        assertThat(remaining).isEqualTo(0); // All messages consumed and ACKed, even though we only processed 3!
    }

    /**
     * Tests streamFromEndpoint() with JPA component.
     *
     * This test verifies what happens with database polling when we terminate early.
     * Key concerns:
     *
     * 1. Are entities marked as processed before the subscriber sees them?
     * 2. Do remaining entities stay unprocessed in the database?
     * 3. Does the route cleanup release database connections/cursors properly?
     */
    @Test
    void jpaStreamWithEarlyTermination() {
        // Seed 10 entities in database
        RestAssured.given()
                .post("/reactive-streams/jpa/seed/10")
                .then()
                .statusCode(200)
                .body(is("seeded-10"));

        // Stream from JPA but only take the first 3
        JsonPath result = RestAssured.given()
                .get("/reactive-streams/jpa/stream-first/3")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        int received = result.getInt("received");
        int processed = result.getInt("processed");
        int remaining = result.getInt("remaining");

        System.out.println("JPA Test Results:");
        System.out.println("  Received: " + received);
        System.out.println("  Processed: " + processed);
        System.out.println("  Remaining unprocessed: " + remaining);

        // Assertions - document actual behavior
        assertThat(processed).isEqualTo(3); // We asked for 3

        // JPA behavior depends on configuration:
        // - consumeDelete=false (used here): Entities stay in DB even after being read
        // - consumeDelete=true (default): Entities deleted in transaction before subscriber processes
        //
        // OBSERVED: JPA polled more than 3 entities (logs show 2 in-flight when stopped), but
        // we used .first(3) to take only 3. The route was forcefully stopped with pending exchanges.
        //
        // With consumeDelete=false: Data is safe (10 entities remain in DB)
        // With consumeDelete=true: Those extra polled entities would be DELETED but never processed
        //
        // This demonstrates the same transaction boundary issue as JMS, but with a safety net
        // if you use consumeDelete=false. Still not safe for processing finite queues.
        assertThat(remaining).isEqualTo(10); // All entities remain with consumeDelete=false
    }
}
