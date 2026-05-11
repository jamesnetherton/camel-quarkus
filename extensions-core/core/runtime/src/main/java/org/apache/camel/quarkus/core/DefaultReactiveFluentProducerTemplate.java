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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Processor;

/**
 * Default implementation of {@link ReactiveFluentProducerTemplate}.
 * <p>
 * This implementation defers all configuration and execution to request/send time.
 * <p>
 * Each instance is not thread-safe as it's assumed that a single thread calls the fluent methods to build up the
 * message to be sent. This matches the behavior of the underlying {@link FluentProducerTemplate}.
 */
public class DefaultReactiveFluentProducerTemplate implements ReactiveFluentProducerTemplate {

    private final CamelContext camelContext;
    private final List<Consumer<FluentProducerTemplate>> configurators = new ArrayList<>();

    public DefaultReactiveFluentProducerTemplate(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public ReactiveFluentProducerTemplate to(String endpointUri) {
        synchronized (configurators) {
            configurators.add(t -> t.to(endpointUri));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate to(Endpoint endpoint) {
        synchronized (configurators) {
            configurators.add(t -> t.to(endpoint));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withDefaultEndpoint(String endpointUri) {
        synchronized (configurators) {
            configurators.add(t -> t.withDefaultEndpoint(endpointUri));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withDefaultEndpoint(Endpoint endpoint) {
        synchronized (configurators) {
            configurators.add(t -> t.withDefaultEndpoint(endpoint));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withBody(Object body) {
        synchronized (configurators) {
            configurators.add(t -> t.withBody(body));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withBodyAs(Object body, Class<?> type) {
        synchronized (configurators) {
            configurators.add(t -> t.withBodyAs(body, type));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withHeader(String key, Object value) {
        synchronized (configurators) {
            configurators.add(t -> t.withHeader(key, value));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withHeaders(Map<String, Object> headers) {
        synchronized (configurators) {
            configurators.add(t -> t.withHeaders(headers));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withExchangeProperty(String key, Object value) {
        synchronized (configurators) {
            configurators.add(t -> t.withExchangeProperty(key, value));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withExchangeProperties(Map<String, Object> properties) {
        synchronized (configurators) {
            configurators.add(t -> t.withExchangeProperties(properties));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withVariable(String key, Object value) {
        synchronized (configurators) {
            configurators.add(t -> t.withVariable(key, value));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withVariables(Map<String, Object> variables) {
        synchronized (configurators) {
            configurators.add(t -> t.withVariables(variables));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withProcessor(Processor processor) {
        synchronized (configurators) {
            configurators.add(t -> t.withProcessor(processor));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withProcessor(Supplier<Processor> processorSupplier) {
        synchronized (configurators) {
            configurators.add(t -> t.withProcessor(processorSupplier));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withExchange(Exchange exchange) {
        synchronized (configurators) {
            configurators.add(t -> t.withExchange(exchange));
        }
        return this;
    }

    @Override
    public ReactiveFluentProducerTemplate withExchange(Supplier<Exchange> exchangeSupplier) {
        synchronized (configurators) {
            configurators.add(t -> t.withExchange(exchangeSupplier));
        }
        return this;
    }

    @Override
    public Uni<Object> request() {
        List<Consumer<FluentProducerTemplate>> captured;
        synchronized (configurators) {
            captured = new ArrayList<>(configurators);
            configurators.clear();
        }

        return Uni.createFrom().future(() -> {
            FluentProducerTemplate template = camelContext.createFluentProducerTemplate();
            captured.forEach(c -> c.accept(template));
            return template.asyncRequest();
        });
    }

    @Override
    public <T> Uni<T> request(Class<T> type) {
        List<Consumer<FluentProducerTemplate>> captured;
        synchronized (configurators) {
            captured = new ArrayList<>(configurators);
            configurators.clear();
        }

        return Uni.createFrom().future(() -> {
            FluentProducerTemplate template = camelContext.createFluentProducerTemplate();
            captured.forEach(c -> c.accept(template));
            return template.asyncRequest(type);
        });
    }

    @Override
    public Uni<Exchange> send() {
        List<Consumer<FluentProducerTemplate>> captured;
        synchronized (configurators) {
            captured = new ArrayList<>(configurators);
            configurators.clear();
        }

        return Uni.createFrom().future(() -> {
            FluentProducerTemplate template = camelContext.createFluentProducerTemplate();
            captured.forEach(c -> c.accept(template));
            return template.asyncSend();
        });
    }

    @Override
    public Uni<Exchange> asyncSend() {
        return send();
    }

    @Override
    public ReactiveFluentProducerTemplate cleanUp() {
        configurators.clear();
        return this;
    }

    @Override
    public FluentProducerTemplate getFluentProducerTemplate() {
        throw new UnsupportedOperationException(
                "ReactiveFluentProducerTemplate does not expose the underlying FluentProducerTemplate. " +
                        "Use request() or send() to execute the configured template.");
    }
}
