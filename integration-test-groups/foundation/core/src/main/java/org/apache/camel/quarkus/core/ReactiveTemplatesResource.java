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

import java.util.HashMap;
import java.util.Map;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

@Path("/reactive-templates")
@ApplicationScoped
public class ReactiveTemplatesResource {

    @Inject
    ReactiveProducerTemplate reactiveProducerTemplate;

    @Inject
    ReactiveConsumerTemplate reactiveConsumerTemplate;

    @Inject
    CamelContext camelContext;

    @Path("/producer/request-body")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> requestBody(String body) {
        return reactiveProducerTemplate.requestBody("direct:toUpper", body, String.class);
    }

    @Path("/producer/request-body-and-header")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> requestBodyAndHeader(String body) {
        return reactiveProducerTemplate.requestBodyAndHeader("direct:withHeader", body, "customHeader", "headerValue",
                String.class);
    }

    @Path("/producer/request-body-and-headers")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> requestBodyAndHeaders(String body) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        return reactiveProducerTemplate.requestBodyAndHeaders("direct:withHeaders", body, headers, String.class);
    }

    @Path("/producer/send-body")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> sendBody(String body) {
        return reactiveProducerTemplate.sendBody("seda:queue", body)
                .map(result -> "sent");
    }

    @Path("/consumer/receive/{timeout}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receive(@PathParam("timeout") long timeout) {
        return reactiveConsumerTemplate.receiveBody("seda:queue", timeout, String.class)
                .map(body -> body != null ? body : "null");
    }

    @Path("/consumer/receive-no-wait")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiveNoWait() {
        return reactiveConsumerTemplate.receiveBodyNoWait("seda:queue", String.class)
                .map(body -> body != null ? body : "null");
    }

    @Path("/error/producer")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> producerError() {
        return reactiveProducerTemplate.requestBody("direct:error", "test", String.class)
                .onFailure().recoverWithItem("error-handled");
    }

    @Path("/chain")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> chain(String body) {
        return reactiveProducerTemplate.requestBody("direct:toUpper", body, String.class)
                .chain(upper -> reactiveProducerTemplate.requestBody("direct:prefix", upper, String.class));
    }

    // Endpoint-based variants
    @Path("/producer/request-body-endpoint")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> requestBodyEndpoint(String body) {
        Endpoint endpoint = camelContext.getEndpoint("direct:toUpper");
        return reactiveProducerTemplate.requestBody(endpoint, body, String.class);
    }

    @Path("/producer/send-body-endpoint")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> sendBodyEndpoint(String body) {
        Endpoint endpoint = camelContext.getEndpoint("seda:queue");
        return reactiveProducerTemplate.sendBody(endpoint, body)
                .map(result -> "sent");
    }

    @Path("/consumer/receive-endpoint/{timeout}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiveEndpoint(@PathParam("timeout") long timeout) {
        Endpoint endpoint = camelContext.getEndpoint("seda:queue");
        return reactiveConsumerTemplate.receiveBody(endpoint, timeout, String.class)
                .map(body -> body != null ? body : "null");
    }

    @Path("/consumer/receive-no-wait-endpoint")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiveNoWaitEndpoint() {
        Endpoint endpoint = camelContext.getEndpoint("seda:queue");
        return reactiveConsumerTemplate.receiveBodyNoWait(endpoint, String.class)
                .map(body -> body != null ? body : "null");
    }

    // Untyped variants
    @Path("/producer/request-body-untyped")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> requestBodyUntyped(String body) {
        return reactiveProducerTemplate.requestBody("direct:toUpper", body)
                .map(result -> result != null ? result.toString() : "null");
    }

    @Path("/producer/request-body-and-header-untyped")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> requestBodyAndHeaderUntyped(String body) {
        return reactiveProducerTemplate.requestBodyAndHeader("direct:withHeader", body, "customHeader", "headerValue")
                .map(result -> result != null ? result.toString() : "null");
    }

    @Path("/producer/request-body-and-headers-untyped")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> requestBodyAndHeadersUntyped(String body) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        return reactiveProducerTemplate.requestBodyAndHeaders("direct:withHeaders", body, headers)
                .map(result -> result != null ? result.toString() : "null");
    }

    @Path("/consumer/receive-body-untyped/{timeout}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiveBodyUntyped(@PathParam("timeout") long timeout) {
        return reactiveConsumerTemplate.receiveBody("seda:queue", timeout)
                .map(body -> body != null ? body.toString() : "null");
    }

    @Path("/consumer/receive-body-no-wait-untyped")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiveBodyNoWaitUntyped() {
        return reactiveConsumerTemplate.receiveBodyNoWait("seda:queue")
                .map(body -> body != null ? body.toString() : "null");
    }

    // Exchange-based methods
    @Path("/producer/send-exchange")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> sendExchange(String body) {
        Exchange exchange = camelContext.getEndpoint("direct:toUpper").createExchange();
        exchange.getIn().setBody(body);
        return reactiveProducerTemplate.send("direct:toUpper", exchange)
                .map(result -> result.getIn().getBody(String.class));
    }

    @Path("/producer/send-processor")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> sendProcessor(String body) {
        return reactiveProducerTemplate.send("direct:toUpper", exchange -> {
            exchange.getIn().setBody(body);
        }).map(result -> result.getIn().getBody(String.class));
    }

    @Path("/consumer/receive-exchange/{timeout}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiveExchange(@PathParam("timeout") long timeout) {
        return reactiveConsumerTemplate.receive("seda:queue", timeout)
                .map(exchange -> exchange != null ? exchange.getIn().getBody(String.class) : "null");
    }

    @Path("/consumer/receive-exchange-no-wait")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiveExchangeNoWait() {
        return reactiveConsumerTemplate.receiveNoWait("seda:queue")
                .map(exchange -> exchange != null ? exchange.getIn().getBody(String.class) : "null");
    }
}
