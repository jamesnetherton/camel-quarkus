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
package org.apache.camel.quarkus.core.deployment;

/**
 * Names of the services the core extension registers.
 */
final class CamelServiceNames {
    /**
     * The {@link org.apache.camel.spi.CamelContextCustomizer} combining the customizers from
     * {@link org.apache.camel.quarkus.core.deployment.spi.CamelContextCustomizerBuildItem}s.
     */
    static final String CONTEXT_CUSTOMIZERS = "camel.context-customizers";
    /**
     * The {@link org.apache.camel.spi.CamelContextCustomizer} combining the customizers from
     * {@link org.apache.camel.quarkus.core.deployment.spi.RuntimeCamelContextCustomizerBuildItem}s.
     */
    static final String RUNTIME_CONTEXT_CUSTOMIZERS = "camel.runtime-context-customizers";
    /**
     * The {@link org.apache.camel.spi.TypeConverterLoader} combining the loaders from
     * {@link org.apache.camel.quarkus.core.deployment.spi.CamelTypeConverterLoaderBuildItem}s.
     */
    static final String TYPE_CONVERTER_LOADERS = "camel.type-converter-loaders";

    static final String BIND_BEANS = "camel.registry.bind-beans";
    static final String BIND_RUNTIME_BEANS = "camel.registry.bind-runtime-beans";
    static final String BIND_TO_REGISTRY = "camel.registry.bind-to-registry";
    static final String EVENT_BRIDGES = "camel.event-bridges";
    static final String CONSUME_ROUTES = "camel.consume-routes";
    static final String TEST_LIFECYCLE = "camel.test-lifecycle";

    private CamelServiceNames() {
    }
}
