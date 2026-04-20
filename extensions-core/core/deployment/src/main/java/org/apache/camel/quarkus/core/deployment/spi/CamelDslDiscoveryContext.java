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
package org.apache.camel.quarkus.core.deployment.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object passed to DSL method handlers during route scanning. Provides information about
 * a discovered DSL method invocation and allows handlers to produce BuildItems for native image
 * configuration.
 * <p>
 * Handlers can call {@link #produce(Object)} to queue BuildItems which will be forwarded to the
 * appropriate BuildProducers by the scanning framework.
 *
 * @see CamelDslMethodHandlerBuildItem
 */
public class CamelDslDiscoveryContext {

    /**
     * The source from which the DSL method was discovered.
     */
    public enum Source {
        /** Discovered by scanning Java RouteBuilder bytecode */
        JAVA_BYTECODE,
        /** Discovered by parsing XML DSL route files */
        XML_DSL,
        /** Discovered by parsing YAML DSL route files */
        YAML_DSL
    }

    private final String methodName;
    private final String typeName;
    private final String sourceLocation;
    private final Source source;

    private final Map<Class<?>, List<Object>> producedItems = new HashMap<>();

    public CamelDslDiscoveryContext(String methodName, String typeName, String sourceLocation, Source source) {
        this.methodName = methodName;
        this.typeName = typeName;
        this.sourceLocation = sourceLocation;
        this.source = source;
    }

    /**
     * @return the name of the DSL method that was invoked (e.g., "onException", "doCatch")
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * @return the fully-qualified class name of the type discovered in the method arguments
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * @return the source location where this DSL usage was found (e.g., class name or file path)
     */
    public String getSourceLocation() {
        return sourceLocation;
    }

    /**
     * @return the source type from which this DSL method was discovered
     */
    public Source getSource() {
        return source;
    }

    /**
     * Queues a BuildItem to be produced by the scanning framework. The item will be forwarded
     * to the appropriate BuildProducer after all handlers have been invoked.
     *
     * @param buildItem the BuildItem to produce
     */
    public void produce(Object buildItem) {
        Class<?> type = buildItem.getClass();
        producedItems.computeIfAbsent(type, k -> new ArrayList<>()).add(buildItem);
    }

    /**
     * Retrieves produced items of a specific type. This method is used by the scanning framework
     * to forward produced BuildItems to the appropriate BuildProducers.
     *
     * @param  type the BuildItem type to retrieve
     * @return      list of produced items of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getProducedItems(Class<T> type) {
        return (List<T>) producedItems.getOrDefault(type, List.of());
    }
}
