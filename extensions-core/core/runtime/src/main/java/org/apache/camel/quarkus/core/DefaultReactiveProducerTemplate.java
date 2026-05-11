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

import java.util.Map;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;

/**
 * Default implementation of {@link ReactiveProducerTemplate}.
 */
public class DefaultReactiveProducerTemplate implements ReactiveProducerTemplate {

    private final ProducerTemplate producerTemplate;
    private final ReactiveStreamsAdapter reactiveStreamsAdapter;

    public DefaultReactiveProducerTemplate(ProducerTemplate producerTemplate, ReactiveStreamsAdapter reactiveStreamsAdapter) {
        this.producerTemplate = producerTemplate;
        this.reactiveStreamsAdapter = reactiveStreamsAdapter;
    }

    @Override
    public Uni<Object> requestBody(String endpointUri, Object body) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncRequestBody(endpointUri, body));
    }

    @Override
    public <T> Uni<T> requestBody(String endpointUri, Object body, Class<T> type) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncRequestBody(endpointUri, body, type));
    }

    @Override
    public Uni<Object> requestBody(Endpoint endpoint, Object body) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncRequestBody(endpoint, body));
    }

    @Override
    public <T> Uni<T> requestBody(Endpoint endpoint, Object body, Class<T> type) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncRequestBody(endpoint, body, type));
    }

    @Override
    public Uni<Object> requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue) {
        return Uni.createFrom()
                .completionStage(() -> producerTemplate.asyncRequestBodyAndHeader(endpointUri, body, header, headerValue));
    }

    @Override
    public <T> Uni<T> requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue,
            Class<T> type) {
        return Uni.createFrom().completionStage(
                () -> producerTemplate.asyncRequestBodyAndHeader(endpointUri, body, header, headerValue, type));
    }

    @Override
    public Uni<Object> requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) {
        return Uni.createFrom()
                .completionStage(() -> producerTemplate.asyncRequestBodyAndHeaders(endpointUri, body, headers));
    }

    @Override
    public <T> Uni<T> requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type) {
        return Uni.createFrom()
                .completionStage(() -> producerTemplate.asyncRequestBodyAndHeaders(endpointUri, body, headers, type));
    }

    @Override
    public Uni<Object> sendBody(String endpointUri, Object body) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncSendBody(endpointUri, body));
    }

    @Override
    public Uni<Object> sendBody(Endpoint endpoint, Object body) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncSendBody(endpoint, body));
    }

    @Override
    public Uni<Exchange> send(String endpointUri, Processor processor) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncSend(endpointUri, processor));
    }

    @Override
    public Uni<Exchange> send(Endpoint endpoint, Processor processor) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncSend(endpoint, processor));
    }

    @Override
    public Uni<Exchange> send(String endpointUri, Exchange exchange) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncSend(endpointUri, exchange));
    }

    @Override
    public Uni<Exchange> send(Endpoint endpoint, Exchange exchange) {
        return Uni.createFrom().completionStage(() -> producerTemplate.asyncSend(endpoint, exchange));
    }

    @Override
    public <T> Uni<Void> streamTo(String endpointUri, Multi<T> stream) {
        return Uni.createFrom().item(() -> {
            reactiveStreamsAdapter.streamTo(endpointUri, stream);
            return null;
        });
    }

    @Override
    public ProducerTemplate getProducerTemplate() {
        return producerTemplate;
    }
}
