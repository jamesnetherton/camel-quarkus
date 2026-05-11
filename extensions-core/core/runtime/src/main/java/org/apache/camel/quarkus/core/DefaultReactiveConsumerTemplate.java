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
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * Default implementation of {@link ReactiveConsumerTemplate}.
 * <p>
 * This implementation wraps blocking {@link ConsumerTemplate} operations and executes them on a worker thread pool to
 * avoid blocking the event loop.
 */
public class DefaultReactiveConsumerTemplate implements ReactiveConsumerTemplate {

    private final ConsumerTemplate consumerTemplate;
    private final ReactiveStreamsAdapter reactiveStreamsAdapter;

    public DefaultReactiveConsumerTemplate(ConsumerTemplate consumerTemplate,
            ReactiveStreamsAdapter reactiveStreamsAdapter) {
        this.consumerTemplate = consumerTemplate;
        this.reactiveStreamsAdapter = reactiveStreamsAdapter;
    }

    @Override
    public Uni<Exchange> receive(String endpointUri, long timeout) {
        return Uni.createFrom().item(() -> consumerTemplate.receive(endpointUri, timeout))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Exchange> receive(Endpoint endpoint, long timeout) {
        return Uni.createFrom().item(() -> consumerTemplate.receive(endpoint, timeout))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Exchange> receiveNoWait(String endpointUri) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveNoWait(endpointUri))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Exchange> receiveNoWait(Endpoint endpoint) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveNoWait(endpoint))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Object> receiveBody(String endpointUri, long timeout) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBody(endpointUri, timeout))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Object> receiveBody(Endpoint endpoint, long timeout) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBody(endpoint, timeout))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public <T> Uni<T> receiveBody(String endpointUri, long timeout, Class<T> type) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBody(endpointUri, timeout, type))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public <T> Uni<T> receiveBody(Endpoint endpoint, long timeout, Class<T> type) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBody(endpoint, timeout, type))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Object> receiveBodyNoWait(String endpointUri) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBodyNoWait(endpointUri))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Object> receiveBodyNoWait(Endpoint endpoint) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBodyNoWait(endpoint))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public <T> Uni<T> receiveBodyNoWait(String endpointUri, Class<T> type) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBodyNoWait(endpointUri, type))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public <T> Uni<T> receiveBodyNoWait(Endpoint endpoint, Class<T> type) {
        return Uni.createFrom().item(() -> consumerTemplate.receiveBodyNoWait(endpoint, type))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public <T> Multi<T> streamFrom(String endpointUri, Class<T> type) {
        return reactiveStreamsAdapter.streamFrom(endpointUri, type);
    }

    @Override
    public Multi<Exchange> streamFrom(String endpointUri) {
        return reactiveStreamsAdapter.streamFromExchange(endpointUri);
    }

    @Override
    public <T> Multi<T> streamFromEndpoint(String endpointUri, Class<T> type) {
        return reactiveStreamsAdapter.streamFromEndpoint(endpointUri, type);
    }

    @Override
    public ConsumerTemplate getConsumerTemplate() {
        return consumerTemplate;
    }
}
