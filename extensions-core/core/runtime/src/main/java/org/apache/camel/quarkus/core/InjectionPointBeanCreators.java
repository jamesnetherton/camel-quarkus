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

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.support.PluginHelper;

/**
 * Creators for the synthetic beans backing Camel injection points, such as {@link org.apache.camel.EndpointInject},
 * {@link org.apache.camel.Produce} and injected {@link Component} fields.
 */
public final class InjectionPointBeanCreators {
    public static final String PARAM_NAME = "name";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_URI = "uri";

    private InjectionPointBeanCreators() {
    }

    public static class ComponentCreator implements BeanCreator<Component> {
        @Override
        public Component create(SyntheticCreationalContext<Component> context) {
            CamelContext camelContext = context.getInjectedReference(CamelContext.class);
            String componentType = (String) context.getParams().get(PARAM_TYPE);
            return camelContext.getComponent(
                    (String) context.getParams().get(PARAM_NAME),
                    camelContext.getClassResolver().resolveClass(componentType, Component.class));
        }
    }

    public static class EndpointCreator implements BeanCreator<Endpoint> {
        @Override
        public Endpoint create(SyntheticCreationalContext<Endpoint> context) {
            CamelContext camelContext = context.getInjectedReference(CamelContext.class);
            String endpointType = (String) context.getParams().get(PARAM_TYPE);
            return camelContext.getEndpoint(
                    (String) context.getParams().get(PARAM_URI),
                    camelContext.getClassResolver().resolveClass(endpointType, Endpoint.class));
        }
    }

    public static class ProducerTemplateCreator implements BeanCreator<ProducerTemplate> {
        @Override
        public ProducerTemplate create(SyntheticCreationalContext<ProducerTemplate> context) {
            CamelContext camelContext = context.getInjectedReference(CamelContext.class);
            ProducerTemplate result = camelContext.createProducerTemplate();
            String uri = (String) context.getParams().get(PARAM_URI);
            if (uri != null) {
                result.setDefaultEndpointUri(uri);
            }
            return result;
        }
    }

    public static class FluentProducerTemplateCreator implements BeanCreator<FluentProducerTemplate> {
        @Override
        public FluentProducerTemplate create(SyntheticCreationalContext<FluentProducerTemplate> context) {
            CamelContext camelContext = context.getInjectedReference(CamelContext.class);
            FluentProducerTemplate result = camelContext.createFluentProducerTemplate();
            String uri = (String) context.getParams().get(PARAM_URI);
            if (uri != null) {
                result.setDefaultEndpointUri(uri);
            }
            return result;
        }
    }

    public static class ProxyCreator implements BeanCreator<Object> {
        @Override
        public Object create(SyntheticCreationalContext<Object> context) {
            CamelContext camelContext = context.getInjectedReference(CamelContext.class);
            Class<?> type = camelContext.getClassResolver().resolveClass((String) context.getParams().get(PARAM_TYPE));
            BeanProxyFactory factory = PluginHelper.getBeanProxyFactory(camelContext);
            Endpoint endpoint = camelContext.getEndpoint((String) context.getParams().get(PARAM_URI));
            try {
                return factory.createProxy(endpoint, true, type);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Could not instantiate proxy of type " + type.getName() + " on endpoint " + endpoint, e);
            }
        }
    }
}
