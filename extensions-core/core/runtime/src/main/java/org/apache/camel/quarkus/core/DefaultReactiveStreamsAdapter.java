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

import io.smallrye.mutiny.Multi;
import org.apache.camel.Exchange;

/**
 * Default no-op implementation of {@link ReactiveStreamsAdapter}.
 * <p>
 * This implementation throws {@link IllegalStateException} when streaming methods are called,
 * indicating that the camel-quarkus-reactive-streams extension is required.
 */
public class DefaultReactiveStreamsAdapter implements ReactiveStreamsAdapter {

    @Override
    public <T> Multi<T> streamFrom(String endpointUri, Class<T> type) {
        throw new IllegalStateException(
                "Reactive streaming is not available. Add the camel-quarkus-reactive-streams extension to use streamFrom() methods.");
    }

    @Override
    public Multi<Exchange> streamFromExchange(String endpointUri) {
        throw new IllegalStateException(
                "Reactive streaming is not available. Add the camel-quarkus-reactive-streams extension to use streamFrom() methods.");
    }

    @Override
    public <T> void streamTo(String endpointUri, Multi<T> stream) {
        throw new IllegalStateException(
                "Reactive streaming is not available. Add the camel-quarkus-reactive-streams extension to use streamTo() methods.");
    }

    @Override
    public <T> Multi<T> streamFromEndpoint(String endpointUri, Class<T> type) {
        throw new IllegalStateException(
                "Reactive streaming is not available. Add the camel-quarkus-reactive-streams extension to use streamFromEndpoint() methods.");
    }
}
