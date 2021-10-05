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
package org.apache.camel.quarkus.component.kafka;

import java.util.Map;

import javax.enterprise.util.TypeLiteral;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.kubernetes.service.binding.runtime.KubernetesServiceBindingConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.common.annotation.Identifier;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;

@Recorder
public class CamelKafkaRecorder {

    public RuntimeValue<KafkaComponent> createKafkaComponent() {
        return new RuntimeValue<>(new KafkaComponent());
    }

    public void configureKafkaComponentForDevServices(RuntimeValue<KafkaComponent> kafkaComponentRuntimeValue, String brokers) {
        KafkaComponent component = kafkaComponentRuntimeValue.getValue();
        KafkaConfiguration configuration = component.getConfiguration();
        if (configuration == null) {
            configuration = new KafkaConfiguration();
        }
        configuration.setBrokers(brokers);
        component.setConfiguration(configuration);
    }

    @SuppressWarnings("serial")
    public void configureKafkaClientFactory(
        RuntimeValue<KafkaComponent> kafkaComponentRuntimeValue,
        CamelKafkaRuntimeConfig camelKafkaRuntimeConfig,
        KubernetesServiceBindingConfig kubernetesServiceBindingConfig) {

        if (kubernetesServiceBindingConfig.enabled && camelKafkaRuntimeConfig.kubernetesServiceBinding.mergeConfiguration) {
            final InstanceHandle<Map<String, Object>> instance = Arc.container()
                    .instance(new TypeLiteral<>() {
                    }, Identifier.Literal.of("default-kafka-broker"));

            if (instance.isAvailable()) {
                Map<String, Object> kafkaConfig = instance.get();
                if (!kafkaConfig.isEmpty()) {
                    QuarkusKafkaClientFactory quarkusKafkaClientFactory = new QuarkusKafkaClientFactory(kafkaConfig);
                    KafkaComponent component = kafkaComponentRuntimeValue.getValue();
                    component.setKafkaClientFactory(quarkusKafkaClientFactory);
                }
            }
        }
    }
}
