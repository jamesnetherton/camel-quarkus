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
package org.apache.camel.quarkus.component.weaviate.it;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WeaviateRoutes extends RouteBuilder {

    @ConfigProperty(name = WeaviateResource.WEAVIATE_CONTAINER_ADDRESS)
    Optional<String> weaviateContainerAddress;

    @ConfigProperty(name = WeaviateResource.WEAVIATE_HOST_ENV)
    Optional<String> host;

    @ConfigProperty(name = WeaviateResource.WEAVIATE_API_KEY_ENV)
    Optional<String> apiKey;

    @Override
    public void configure() throws Exception {
        from("direct:weaviate")
                .to(getUrl());
    }

    private String getUrl() {
        if (weaviateContainerAddress.isPresent()) {
            return "weaviate:test-collection?scheme=http&host=" + weaviateContainerAddress.get();
        }

        if (host.isPresent() && apiKey.isPresent()) {
            return "weaviate:test-collection?scheme=https&host=%s&apiKey=%s".formatted(host.get(), apiKey.get());
        }

        throw new IllegalStateException("Weaviate is not configured. Bot container or real account configuration is missing.");
    }
}
