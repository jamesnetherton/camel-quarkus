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

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.Registry;

/**
 * Producers of beans that are injectable via CDI.
 */
@Singleton
public class CamelProducers {
    private volatile CamelContext context;
    private volatile CamelRuntime runtime;

    public void setContext(CamelContext context) {
        this.context = context;
    }

    public void setRuntime(CamelRuntime runtime) {
        this.runtime = runtime;
    }

    @Singleton
    @Produces
    CamelContext camelContext() {
        return this.context;
    }

    @Singleton
    @Produces
    Registry camelRegistry() {
        return this.context.getRegistry();
    }

    @Singleton
    @Produces
    CamelRuntime camelRuntime() {
        return this.runtime;
    }

    @Produces
    ProducerTemplate camelProducerTemplate() {
        /*
         * Note that ProducerTemplate injection points qualified with @EndpointInject and @Produce are handled in
         * InjectionPointsProcessor.syntheticBeans()
         */
        return this.context.createProducerTemplate();
    }

    @Produces
    FluentProducerTemplate camelFluentProducerTemplate() {
        /*
         * Note that FluentProducerTemplate injection points qualified with @EndpointInject and @Produce are handled in
         * InjectionPointsProcessor.syntheticBeans()
         */
        return this.context.createFluentProducerTemplate();
    }

    @Produces
    ConsumerTemplate camelConsumerTemplate() {
        return this.context.createConsumerTemplate();
    }

    @Produces
    ReactiveProducerTemplate reactiveProducerTemplate() {
        ReactiveStreamsAdapter adapter = getReactiveStreamsAdapter();
        return new DefaultReactiveProducerTemplate(this.context.createProducerTemplate(), adapter);
    }

    @Dependent
    @Produces
    ReactiveFluentProducerTemplate reactiveFluentProducerTemplate() {
        return new DefaultReactiveFluentProducerTemplate(this.context);
    }

    @Produces
    ReactiveConsumerTemplate reactiveConsumerTemplate() {
        ReactiveStreamsAdapter adapter = getReactiveStreamsAdapter();
        return new DefaultReactiveConsumerTemplate(this.context.createConsumerTemplate(), adapter);
    }

    private ReactiveStreamsAdapter getReactiveStreamsAdapter() {
        // Try to find adapter provided by reactive-streams extension
        ReactiveStreamsAdapter adapter = this.context.getRegistry()
                .lookupByNameAndType("reactive-streams-adapter", ReactiveStreamsAdapter.class);
        // Fallback to default no-op implementation
        return adapter != null ? adapter : new DefaultReactiveStreamsAdapter();
    }
}
