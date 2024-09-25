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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeMBeanException;

import io.netty.buffer.ByteBufInputStream;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;
import org.apache.camel.util.ObjectHelper;
import org.jolokia.json.JSONStructure;
import org.jolokia.server.core.http.HttpRequestHandler;

final class JolokiaHandler implements Handler<RoutingContext> {
    private final HttpRequestHandler requestHandler;

    JolokiaHandler(HttpRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();
        String pathOffset = Utils.pathOffset(request.path(), routingContext);
        InstanceHandle<SecurityIdentity> instance = Arc.container().instance(SecurityIdentity.class);
        if (instance.isAvailable()) {
            SecurityIdentity securityIdentity = instance.get();
            Principal principal = securityIdentity.getPrincipal();
            System.out.println("===== > " + principal.getName());
        }

        requestHandler.checkAccess(request.scheme(), request.remoteAddress().host(), request.remoteAddress().host(),
                getOriginOrReferer(request));

        JSONStructure json = null;
        int status = 200;
        try {
            if (request.method() == HttpMethod.GET) {
                json = requestHandler.handleGetRequest(request.uri(), pathOffset, getParams(request.params()));
            } else {
                if (routingContext.body().isEmpty()) {
                    throw new Exception("Missing request body");
                }

                InputStream inputStream = new ByteBufInputStream(routingContext.body().buffer().getByteBuf());
                json = requestHandler.handlePostRequest(request.uri(), inputStream, StandardCharsets.UTF_8.name(),
                        getParams(request.params()));
            }
        } catch (Throwable e) {
            status = 500;
            Throwable handled = e;
            if (e instanceof RuntimeMBeanException) {
                handled = ((RuntimeMBeanException) e).getTargetException();
            }
            json = requestHandler.handleThrowable(handled);
        } finally {
            if (json == null) {
                json = requestHandler.handleThrowable(new Exception("Error occurred while processing request"));
            }

            String responseBody = json != null ? json.toJSONString() : "";
            routingContext.response()
                    .setStatusCode(status)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(responseBody);
        }
    }

    private String getOriginOrReferer(HttpServerRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (ObjectHelper.isEmpty(origin)) {
            origin = request.getHeader(HttpHeaders.REFERER);
        }
        return ObjectHelper.isNotEmpty(origin) ? origin.replaceAll("[\\n\\r]*", "") : "";
    }

    private Map<String, String[]> getParams(MultiMap params) {
        Map<String, String[]> response = new HashMap<>();
        for (String name : params.names()) {
            response.put(name, params.getAll(name).toArray(new String[0]));
        }
        return response;
    }
}
