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

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A {@link MultiBuildItem} that allows extensions to register interest in specific Camel DSL
 * methods and provide custom handling logic for when those methods are discovered during route
 * scanning.
 * <p>
 * The scanning framework will invoke the handler for each discovered usage of the specified DSL
 * methods, passing a {@link CamelDslDiscoveryContext} that contains information about the method
 * invocation and allows the handler to produce BuildItems (e.g., for reflection registration).
 * <p>
 * <b>Thread Safety:</b> Handler implementations must be thread-safe and stateless. Multiple
 * scanner build steps (Java, XML, YAML) may invoke the same handler concurrently. Each invocation
 * receives a fresh {@link CamelDslDiscoveryContext} instance, so handlers should only access data
 * through the context parameter and avoid capturing or modifying shared mutable state.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * @BuildStep
 * CamelDslMethodHandlerBuildItem handleCustomMethods(CombinedIndexBuildItem combinedIndex) {
 *     final IndexView index = combinedIndex.getIndex(); // Immutable - safe to capture
 *     return new CamelDslMethodHandlerBuildItem(
 *             ctx -> {
 *                 String type = ctx.getTypeName();
 *                 if (CamelSupport.isAssignableTo(type, MyType.class, index)) {
 *                     ctx.produce(ReflectiveClassBuildItem.builder(type).methods().build());
 *                 }
 *             },
 *             "myCustomMethod", "anotherMethod");
 * }
 * }</pre>
 *
 * @see CamelDslDiscoveryContext
 */
public final class CamelDslMethodHandlerBuildItem extends MultiBuildItem {

    private final Set<String> methodNames;
    private final Consumer<CamelDslDiscoveryContext> handler;

    /**
     * Creates a new DSL method handler registration.
     *
     * @param handler     the handler that will be invoked when a registered method is discovered
     * @param methodNames the DSL method names to watch for (must not be empty)
     */
    public CamelDslMethodHandlerBuildItem(Consumer<CamelDslDiscoveryContext> handler, String... methodNames) {
        Objects.requireNonNull(handler, "handler must not be null");
        Objects.requireNonNull(methodNames, "methodNames must not be null");
        if (methodNames.length == 0) {
            throw new IllegalArgumentException("At least one method name must be specified");
        }
        this.methodNames = Set.of(methodNames);
        this.handler = handler;
    }

    /**
     * @return the set of DSL method names this handler is interested in
     */
    public Set<String> getMethodNames() {
        return Collections.unmodifiableSet(methodNames);
    }

    /**
     * @return the handler to invoke when a registered method is discovered
     */
    public Consumer<CamelDslDiscoveryContext> getHandler() {
        return handler;
    }
}
