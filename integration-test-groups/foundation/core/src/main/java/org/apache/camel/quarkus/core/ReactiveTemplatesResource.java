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

@Path("/reactive-templates")
@ApplicationScoped
public class ReactiveTemplatesResource {

    @Inject
    ReactiveProducerTemplate reactiveProducerTemplate;

    @Inject
    ReactiveConsumerTemplate reactiveConsumerTemplate;

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
}
