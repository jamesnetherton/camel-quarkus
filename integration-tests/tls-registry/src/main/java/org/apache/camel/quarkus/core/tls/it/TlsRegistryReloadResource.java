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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import io.quarkus.tls.CertificateUpdatedEvent;
import io.quarkus.tls.TlsConfigurationRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.CamelEvent.RouteStartedEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/tls-registry/reload")
@ApplicationScoped
public class TlsRegistryReloadResource {

    @Inject
    Event<CertificateUpdatedEvent> certificateEventProducer;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    CamelContext camelContext;

    @Inject
    TlsConfigurationRegistry tlsRegistry;

    @ConfigProperty(name = "tls-registry.consumer.port", defaultValue = "8643")
    int consumerPort;

    private final Map<String, RouteStarts> routeStarts = new ConcurrentHashMap<>();
    private final Map<String, SSLContext> clientContexts = new ConcurrentHashMap<>();
    private final Map<String, Integer> certificateUpdates = new ConcurrentHashMap<>();

    record RouteStarts(int count, long lastStarted) {
    }

    void onRouteStarted(@Observes RouteStartedEvent event) {
        routeStarts.merge(event.getRoute().getId(), new RouteStarts(1, System.currentTimeMillis()),
                (previous, started) -> new RouteStarts(previous.count() + 1, started.lastStarted()));
    }

    void onCertificateUpdated(@Observes CertificateUpdatedEvent event) {
        certificateUpdates.merge(event.name(), 1, Integer::sum);
    }

    /**
     * How many certificate updated events the TLS configuration had. Quarkus fires one on every reload period, also
     * when no file changed.
     */
    @GET
    @Path("/certificate-updates/{certName}")
    @Produces(MediaType.TEXT_PLAIN)
    public int certificateUpdates(@PathParam("certName") String certName) {
        return certificateUpdates.getOrDefault(certName, 0);
    }

    /**
     * Fires a certificate updated event, without changing the certificate.
     */
    @POST
    @Path("/fire-event/{certName}")
    @Produces(MediaType.TEXT_PLAIN)
    public String fireCertificateEvent(@PathParam("certName") String certName) {
        certificateEventProducer.fire(new CertificateUpdatedEvent(certName, null));
        return "Event fired for: " + certName;
    }

    /**
     * Sends a message to the route, see {@link TlsRegistryReloadRoutes}. For a route calling the
     * {@link MutualTlsServer}, the reply is the serial number of the client certificate the component presented.
     */
    @GET
    @Path("/route/{routeId}/call")
    @Produces(MediaType.TEXT_PLAIN)
    public Response call(@PathParam("routeId") String routeId) {
        try {
            return Response.ok(producerTemplate.requestBody("direct:" + routeId, null, String.class)).build();
        } catch (Exception e) {
            return Response.serverError().entity(String.valueOf(e.getMessage())).build();
        }
    }

    /**
     * Sends a line to the TLS consumer of {@link TlsRegistryReloadRoutes}, as a client using the given TLS
     * configuration. The SSLContext is kept between calls, so that a call can resume the session of an earlier one.
     */
    @GET
    @Path("/consumer/{clientTlsConfiguration}/call")
    @Produces(MediaType.TEXT_PLAIN)
    public Response callConsumer(@PathParam("clientTlsConfiguration") String clientTlsConfiguration) {
        try {
            SSLContext context = clientContexts.computeIfAbsent(clientTlsConfiguration, name -> {
                try {
                    return tlsRegistry.get(name).orElseThrow().createSSLContext();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("localhost", consumerPort)) {
                socket.setSoTimeout(10000);
                Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                writer.write("ping\n");
                writer.flush();
                String reply = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                        .readLine();
                if (reply == null) {
                    return Response.serverError().entity("Connection closed").build();
                }
                return Response.ok(reply).build();
            }
        } catch (Exception e) {
            return Response.serverError().entity(e.getClass().getName() + ": " + e.getMessage()).build();
        }
    }

    /**
     * How many times the route started, and when it last started, as {@code <count>,<epoch millis>}.
     */
    @GET
    @Path("/route/{routeId}/starts")
    @Produces(MediaType.TEXT_PLAIN)
    public String starts(@PathParam("routeId") String routeId) {
        RouteStarts starts = routeStarts.getOrDefault(routeId, new RouteStarts(0, 0));
        return starts.count() + "," + starts.lastStarted();
    }

    @GET
    @Path("/route/{routeId}/status")
    @Produces(MediaType.TEXT_PLAIN)
    public String status(@PathParam("routeId") String routeId) {
        return camelContext.getRouteController().getRouteStatus(routeId).name();
    }

    @POST
    @Path("/route/{routeId}/suspend")
    public void suspend(@PathParam("routeId") String routeId) throws Exception {
        camelContext.getRouteController().suspendRoute(routeId);
    }

    @POST
    @Path("/route/{routeId}/stop")
    public void stop(@PathParam("routeId") String routeId) throws Exception {
        camelContext.getRouteController().stopRoute(routeId);
    }
}
