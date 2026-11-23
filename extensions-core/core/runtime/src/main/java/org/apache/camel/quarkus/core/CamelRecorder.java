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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.apache.camel.main.MainListener;
import org.apache.camel.quarkus.main.CamelMain;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TypeConverterLoader;

/**
 * Bridges values from the core SPI build items, which carry {@link RuntimeValue}s produced by recorders, to the
 * services that assemble the Camel runtime. A service cannot depend on a {@link RuntimeValue}, so the values are
 * returned as recorder proxies that can be published as services. Lists of values are combined into a single value, as
 * a service cannot consume every published recorder value of a type.
 */
@Recorder
public class CamelRecorder {
    public RuntimeValue<Registry> createRegistry(
            List<String> beanTypeNames,
            List<String> beanNames,
            List<RuntimeValue<CamelBeanQualifierResolver>> resolvers) {

        Map<BeanQualifierResolverIdentifier, CamelBeanQualifierResolver> beanQualifiers = new HashMap<>();
        for (int i = 0; i < resolvers.size(); i++) {
            CamelBeanQualifierResolver resolver = resolvers.get(i).getValue();
            BeanQualifierResolverIdentifier identifier = resolver.getIdentifier(beanTypeNames.get(i), beanNames.get(i));

            if (beanQualifiers.containsKey(identifier)) {
                throw new RuntimeException("Duplicate CamelBeanQualifierResolver detected for: " + identifier);
            }

            beanQualifiers.put(identifier, resolver);
        }
        return new RuntimeValue<>(new RuntimeRegistry(beanQualifiers));
    }

    public Registry registry(RuntimeValue<Registry> value) {
        return value.getValue();
    }

    public TypeConverterLoader typeConverterLoaders(List<RuntimeValue<TypeConverterLoader>> loaders) {
        return registry -> loaders.forEach(loader -> loader.getValue().load(registry));
    }

    public ModelJAXBContextFactory modelJAXBContextFactory(RuntimeValue<ModelJAXBContextFactory> value) {
        return value.getValue();
    }

    public ModelToXMLDumper modelToXMLDumper(RuntimeValue<ModelToXMLDumper> value) {
        return value.getValue();
    }

    public ModelToYAMLDumper modelToYAMLDumper(RuntimeValue<ModelToYAMLDumper> value) {
        return value.getValue();
    }

    public CamelContextCustomizer contextCustomizers(List<RuntimeValue<CamelContextCustomizer>> customizers) {
        return context -> customizers.forEach(customizer -> customizer.getValue().configure(context));
    }

    public void addMainListener(RuntimeValue<CamelMain> main, RuntimeValue<MainListener> listener) {
        main.getValue().addMainListener(listener.getValue());
    }

    public void bind(RuntimeValue<Registry> registry, String name, Class<?> type, RuntimeValue<?> instance) {
        registry.getValue().bind(name, type, instance.getValue());
    }
}
