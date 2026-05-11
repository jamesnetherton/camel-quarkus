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

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

/**
 * Routes supporting reactive template tests.
 */
@ApplicationScoped
public class ReactiveTemplatesRoutes extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:toUpper")
                .routeId("toUpper")
                .process(exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    exchange.getMessage().setBody(body.toUpperCase());
                });

        from("direct:withHeader")
                .routeId("withHeader")
                .process(exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    String header = exchange.getMessage().getHeader("customHeader", String.class);
                    exchange.getMessage().setBody(body + " - " + header);
                });

        from("direct:withHeaders")
                .routeId("withHeaders")
                .process(exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    String header1 = exchange.getMessage().getHeader("header1", String.class);
                    String header2 = exchange.getMessage().getHeader("header2", String.class);
                    exchange.getMessage().setBody(body + " - " + header1 + " - " + header2);
                });

        from("direct:prefix")
                .routeId("prefix")
                .process(exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    exchange.getMessage().setBody("PREFIX:" + body);
                });

        from("direct:error")
                .routeId("error")
                .throwException(new RuntimeException("Simulated error"));
    }
}
