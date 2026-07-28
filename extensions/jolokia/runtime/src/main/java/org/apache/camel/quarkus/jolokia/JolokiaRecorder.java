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
package org.apache.camel.quarkus.jolokia;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.quarkus.jolokia.config.JolokiaBuildTimeConfig;
import org.apache.camel.quarkus.jolokia.config.JolokiaRuntimeConfig;
import org.apache.camel.quarkus.jolokia.restrictor.CamelJolokiaRestrictor;
import org.jolokia.core.api.LogHandler;
import org.jolokia.json.JSONStructure;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.http.HttpRequestHandler;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.JolokiaServiceManager;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.service.jmx.LocalRequestHandler;
import org.jolokia.service.serializer.JolokiaSerializer;

@Recorder
public class JolokiaRecorder {
    private final JolokiaBuildTimeConfig buildTimeConfig;
    private final RuntimeValue<JolokiaRuntimeConfig> runtimeConfig;

    public JolokiaRecorder(JolokiaBuildTimeConfig buildTimeConfig, RuntimeValue<JolokiaRuntimeConfig> runtimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
        this.runtimeConfig = runtimeConfig;
    }

    public RuntimeValue<HttpRequestHandler> createJolokiaHttpHandler(String applicationName,
            ShutdownContext shutdownContext) {
        JolokiaRuntimeConfig config = runtimeConfig.getValue();

        Map<String, String> configMap = new HashMap<>(config.additionalProperties());
        // AGENT_ID is required by AgentDetails constructor — generate a stable identifier
        configMap.putIfAbsent(ConfigKey.AGENT_ID.getKeyValue(),
                org.jolokia.server.core.util.NetworkUtil.getAgentId(System.identityHashCode(this), "quarkus"));
        configMap.putIfAbsent(ConfigKey.AGENT_DESCRIPTION.getKeyValue(), applicationName);

        // Resolve log handler — use custom class if configured, otherwise the default bridge
        LogHandler logHandler;
        String logHandlerClassName = configMap.remove(ConfigKey.LOGHANDLER_CLASS.getKeyValue());
        if (logHandlerClassName != null) {
            logHandler = instantiateJolokiaClass(logHandlerClassName, LogHandler.class, "log handler");
        } else {
            logHandler = new CamelQuarkusJolokiaLogHandler();
        }

        // Resolve restrictor — prefer explicitly configured class, then CamelJolokiaRestrictor, then AllowAll
        Restrictor restrictor;
        String restrictorClassName = configMap.get(ConfigKey.RESTRICTOR_CLASS.getKeyValue());
        if (restrictorClassName != null) {
            restrictor = instantiateJolokiaClass(restrictorClassName, Restrictor.class, "restrictor");
        } else if (config.registerCamelRestrictor()) {
            restrictor = new CamelJolokiaRestrictor();
            configMap.put(ConfigKey.RESTRICTOR_CLASS.getKeyValue(), CamelJolokiaRestrictor.class.getName());
        } else {
            restrictor = new org.jolokia.server.core.restrictor.AllowAllRestrictor();
        }

        StaticConfiguration jolokiaConfig = new StaticConfiguration(configMap);

        JolokiaServiceManager serviceManager = JolokiaServiceManagerFactory.createJolokiaServiceManager(
                jolokiaConfig, logHandler, restrictor);
        // Register core services explicitly rather than relying on classpath SPI discovery,
        // which may not work reliably in all Quarkus classloader configurations.
        serviceManager.addService(new JolokiaSerializer());
        serviceManager.addService(new LocalRequestHandler(1));

        HttpRequestHandler handler = new HttpRequestHandler(serviceManager.start());
        shutdownContext.addShutdownTask(serviceManager::stop);

        return new RuntimeValue<>(handler);
    }

    public Handler<RoutingContext> createHandler(RuntimeValue<HttpRequestHandler> handlerValue, String resolvedBasePath) {
        return new JolokiaVertxRouteHandler(handlerValue.getValue(), resolvedBasePath);
    }

    public Consumer<Route> routeFunction() {
        return route -> route.produces("application/json");
    }

    private static <T> T instantiateJolokiaClass(String className, Class<T> type, String description) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = Class.forName(className, true, cl);
            return type.cast(clazz.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate Jolokia " + description + ": " + className, e);
        }
    }

    static final class JolokiaVertxRouteHandler implements Handler<RoutingContext> {
        private final HttpRequestHandler jolokiaHandler;
        private final String contextPath;

        JolokiaVertxRouteHandler(HttpRequestHandler jolokiaHandler, String contextPath) {
            this.jolokiaHandler = jolokiaHandler;
            this.contextPath = contextPath;
        }

        @Override
        public void handle(RoutingContext ctx) {
            try {
                if (ctx.request().method() == HttpMethod.OPTIONS) {
                    handleCors(ctx);
                    return;
                }

                String scheme = ctx.request().scheme();
                String remoteHost = ctx.request().remoteAddress().host();
                String remoteAddr = ctx.request().remoteAddress().hostAddress();
                String origin = ctx.request().getHeader("Origin");
                if (origin == null) {
                    origin = ctx.request().getHeader("Referer");
                }
                jolokiaHandler.checkAccess(scheme, remoteHost, remoteAddr, origin);

                Map<String, String[]> params = convertQueryParams(ctx);
                String uri = ctx.request().uri();
                JSONStructure response;

                if (ctx.request().method() == HttpMethod.POST) {
                    String charset = extractCharset(ctx.request().getHeader("Content-Type"));
                    byte[] bodyBytes = ctx.body().buffer() != null ? ctx.body().buffer().getBytes() : new byte[0];
                    InputStream bodyStream = new ByteArrayInputStream(bodyBytes);
                    response = jolokiaHandler.handlePostRequest(uri, bodyStream, charset, params);
                } else {
                    // pathInfo is everything after the Jolokia context path in the URL.
                    // contextPath is the fully-resolved base path (e.g. /q/jolokia or /jolokia).
                    // The request path is percent-encoded; decode it so Jolokia can parse MBean names.
                    String requestPath = URLDecoder.decode(ctx.request().path(), StandardCharsets.UTF_8);
                    String pathInfo = "/";
                    if (requestPath.startsWith(contextPath)) {
                        String remainder = requestPath.substring(contextPath.length());
                        pathInfo = remainder.isEmpty() ? "/" : remainder;
                    }
                    response = jolokiaHandler.handleGetRequest(uri, pathInfo, params);
                }

                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(response.toJSONString());

            } catch (SecurityException e) {
                ctx.response().setStatusCode(403).end(e.getMessage());
            } catch (EmptyResponseException e) {
                ctx.response().setStatusCode(204).end();
            } catch (BadRequestException | IOException e) {
                ctx.response().setStatusCode(400).end(e.getMessage());
            }
        }

        private void handleCors(RoutingContext ctx) {
            String origin = ctx.request().getHeader("Origin");
            String headers = ctx.request().getHeader("Access-Control-Request-Headers");
            Map<String, String> corsHeaders = jolokiaHandler.handleCorsPreflightRequest(origin, headers);
            corsHeaders.forEach((k, v) -> ctx.response().putHeader(k, v));
            ctx.response().setStatusCode(200).end();
        }

        private static Map<String, String[]> convertQueryParams(RoutingContext ctx) {
            Map<String, String[]> result = new HashMap<>();
            ctx.queryParams().forEach(entry -> {
                String key = entry.getKey();
                result.merge(key, new String[] { entry.getValue() },
                        (existing, added) -> {
                            String[] merged = new String[existing.length + 1];
                            System.arraycopy(existing, 0, merged, 0, existing.length);
                            merged[existing.length] = added[0];
                            return merged;
                        });
            });
            return result;
        }

        private static String extractCharset(String contentType) {
            if (contentType == null) {
                return StandardCharsets.UTF_8.name();
            }
            for (String part : contentType.split(";")) {
                String trimmed = part.trim();
                if (trimmed.toLowerCase().startsWith("charset=")) {
                    return trimmed.substring("charset=".length()).trim();
                }
            }
            return StandardCharsets.UTF_8.name();
        }
    }
}
