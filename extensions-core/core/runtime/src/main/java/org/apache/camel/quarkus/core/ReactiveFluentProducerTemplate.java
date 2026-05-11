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
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Processor;

/**
 * Reactive fluent API wrapper for {@link FluentProducerTemplate} that provides a fluent builder interface with methods
 * returning {@link Uni}.
 * <p>
 * This template combines the fluent API pattern of {@link FluentProducerTemplate} with Mutiny's {@link Uni} type for
 * non-blocking reactive operations.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;Inject
 * ReactiveFluentProducerTemplate template;
 *
 * &#64;GET
 * &#64;Path("/process")
 * public Uni&lt;String&gt; process(String data) {
 *     return template.to("direct:process")
 *             .withBody(data)
 *             .withHeader("Source", "API")
 *             .request(String.class);
 * }
 * </pre>
 *
 * <p>
 * <b>Important:</b> Each instance is not thread-safe as it's assumed that a single thread calls the fluent methods to
 * build up the message to be sent. This matches the behavior of the underlying {@link FluentProducerTemplate}.
 */
public interface ReactiveFluentProducerTemplate {

    /**
     * Sets the endpoint URI to send to.
     *
     * @param  endpointUri the endpoint URI
     * @return             this template for method chaining
     */
    ReactiveFluentProducerTemplate to(String endpointUri);

    /**
     * Sets the endpoint to send to.
     *
     * @param  endpoint the endpoint
     * @return          this template for method chaining
     */
    ReactiveFluentProducerTemplate to(Endpoint endpoint);

    /**
     * Sets the default endpoint URI.
     *
     * @param  endpointUri the default endpoint URI
     * @return             this template for method chaining
     */
    ReactiveFluentProducerTemplate withDefaultEndpoint(String endpointUri);

    /**
     * Sets the default endpoint.
     *
     * @param  endpoint the default endpoint
     * @return          this template for method chaining
     */
    ReactiveFluentProducerTemplate withDefaultEndpoint(Endpoint endpoint);

    /**
     * Sets the message body.
     *
     * @param  body the body
     * @return      this template for method chaining
     */
    ReactiveFluentProducerTemplate withBody(Object body);

    /**
     * Sets the message body with type conversion.
     *
     * @param  body the body
     * @param  type the type to convert to
     * @return      this template for method chaining
     */
    ReactiveFluentProducerTemplate withBodyAs(Object body, Class<?> type);

    /**
     * Sets a message header.
     *
     * @param  key   the header name
     * @param  value the header value
     * @return       this template for method chaining
     */
    ReactiveFluentProducerTemplate withHeader(String key, Object value);

    /**
     * Sets multiple message headers.
     *
     * @param  headers the headers
     * @return         this template for method chaining
     */
    ReactiveFluentProducerTemplate withHeaders(Map<String, Object> headers);

    /**
     * Sets an exchange property.
     *
     * @param  key   the property name
     * @param  value the property value
     * @return       this template for method chaining
     */
    ReactiveFluentProducerTemplate withExchangeProperty(String key, Object value);

    /**
     * Sets multiple exchange properties.
     *
     * @param  properties the properties
     * @return            this template for method chaining
     */
    ReactiveFluentProducerTemplate withExchangeProperties(Map<String, Object> properties);

    /**
     * Sets a variable.
     *
     * @param  key   the variable name
     * @param  value the variable value
     * @return       this template for method chaining
     */
    ReactiveFluentProducerTemplate withVariable(String key, Object value);

    /**
     * Sets multiple variables.
     *
     * @param  variables the variables
     * @return           this template for method chaining
     */
    ReactiveFluentProducerTemplate withVariables(Map<String, Object> variables);

    /**
     * Configures the exchange using a processor.
     *
     * @param  processor the processor
     * @return           this template for method chaining
     */
    ReactiveFluentProducerTemplate withProcessor(Processor processor);

    /**
     * Configures the exchange using a processor supplier.
     *
     * @param  processorSupplier the processor supplier
     * @return                   this template for method chaining
     */
    ReactiveFluentProducerTemplate withProcessor(Supplier<Processor> processorSupplier);

    /**
     * Sets the exchange.
     *
     * @param  exchange the exchange
     * @return          this template for method chaining
     */
    ReactiveFluentProducerTemplate withExchange(Exchange exchange);

    /**
     * Sets the exchange using a supplier.
     *
     * @param  exchangeSupplier the exchange supplier
     * @return                  this template for method chaining
     */
    ReactiveFluentProducerTemplate withExchange(Supplier<Exchange> exchangeSupplier);

    /**
     * Sends asynchronously and returns a Uni with the result object.
     *
     * @return a {@link Uni} that emits the result
     */
    Uni<Object> request();

    /**
     * Sends asynchronously and returns a Uni with the result converted to the specified type.
     *
     * @param  type the expected result type
     * @param  <T>  the result type
     * @return      a {@link Uni} that emits the result converted to the specified type
     */
    <T> Uni<T> request(Class<T> type);

    /**
     * Sends asynchronously using InOnly exchange pattern.
     *
     * @return a {@link Uni} that emits the exchange
     */
    Uni<Exchange> send();

    /**
     * Sends asynchronously and returns a Uni with the resulting exchange.
     *
     * @return a {@link Uni} that emits the exchange
     */
    Uni<Exchange> asyncSend();

    /**
     * Cleanup the cache (purging stale entries).
     *
     * @return this template for method chaining
     */
    ReactiveFluentProducerTemplate cleanUp();

    /**
     * Gets the underlying {@link FluentProducerTemplate}.
     *
     * @return the underlying fluent producer template
     */
    FluentProducerTemplate getFluentProducerTemplate();
}
