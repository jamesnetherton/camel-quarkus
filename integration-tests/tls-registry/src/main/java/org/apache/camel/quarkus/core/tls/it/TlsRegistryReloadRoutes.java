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
package org.apache.camel.quarkus.core.tls.it;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.vertx.http.VertxHttpComponent;
import org.apache.camel.model.RouteDefinition;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Routes calling the {@link MutualTlsServer}, added only by the test profiles that set
 * {@code tls-registry.reload-routes},
 * since they reference TLS configurations that other profiles do not define.
 *
 * Each entry is {@code <route id>=<component>:<bean>}, or {@code <route id>=none} for a route without TLS. Each of
 * these
 * routes consumes from {@code direct:<route id>}. The component is one of:
 *
 * - {@code https} or {@code vertx-http};
 * - {@code vertx-http-intercepted}, a camel-vertx-http endpoint behind {@code interceptSendToEndpoint};
 * - {@code vertx-http-aliased}, an endpoint of a camel-vertx-http component registered under another name.
 *
 * {@code <route id>=netty-consumer:<bean>} is a camel-netty TLS consumer requiring a client certificate, replying
 * {@code ok} to each line.
 */
@ApplicationScoped
public class TlsRegistryReloadRoutes extends RouteBuilder {
    private static final String SERVER = "localhost:{{tls-registry.mtls.port:8543}}/";
    private static final String INTERCEPTED_PATH = "intercepted";
    private static final String ALIASED_COMPONENT = "aliased-vertx-http";

    @ConfigProperty(name = "tls-registry.reload-routes")
    Optional<List<String>> routes;

    @Override
    public void configure() {
        List<String> routes = this.routes.orElse(List.of());
        if (routes.stream().anyMatch(route -> route.contains("=vertx-http-aliased:"))) {
            getContext().addComponent(ALIASED_COMPONENT, new VertxHttpComponent());
        }
        interceptSendToEndpoint(".*/" + INTERCEPTED_PATH + ".*").setHeader("intercepted", constant("true"));

        for (String route : routes) {
            String routeId = route.substring(0, route.indexOf('='));
            String target = route.substring(route.indexOf('=') + 1);

            if (target.startsWith("netty-consumer:")) {
                from("netty:tcp://localhost:{{tls-registry.consumer.port:8643}}?textline=true&ssl=true&needClientAuth=true"
                        + "&sslContextParameters=#" + target.substring(target.indexOf(':') + 1))
                        .routeId(routeId)
                        .setBody(constant("ok"));
                continue;
            }

            RouteDefinition definition = from("direct:" + routeId).routeId(routeId);
            if ("none".equals(target)) {
                definition.setBody(constant("no-tls"));
                continue;
            }

            String component = target.substring(0, target.indexOf(':'));
            String bean = target.substring(target.indexOf(':') + 1);
            switch (component) {
            case "https" -> definition.to("https://" + SERVER + "?sslContextParameters=#" + bean);
            case "vertx-http" -> definition.to("vertx-http:https://" + SERVER + "?sslContextParameters=#" + bean);
            case "vertx-http-intercepted" -> definition
                    .to("vertx-http:https://" + SERVER + INTERCEPTED_PATH + "?sslContextParameters=#" + bean);
            case "vertx-http-aliased" -> definition
                    .to(ALIASED_COMPONENT + ":https://" + SERVER + "?sslContextParameters=#" + bean);
            default -> throw new IllegalArgumentException("Unknown component " + component);
            }
        }
    }
}
