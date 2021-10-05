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
package org.apache.camel.quarkus.component.kafka.deployment;

import java.util.Optional;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.kafka.client.deployment.DevServicesKafkaBrokerBuildItem;
import io.quarkus.kafka.client.deployment.KafkaBuildTimeConfig;
import io.quarkus.kubernetes.service.binding.runtime.KubernetesServiceBindingConfig;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.quarkus.component.kafka.CamelKafkaRecorder;
import org.apache.camel.quarkus.component.kafka.CamelKafkaRuntimeConfig;
import org.apache.camel.quarkus.core.deployment.spi.CamelBeanBuildItem;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

class KafkaProcessor {
    private static final String FEATURE = "camel-kafka";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    public CamelKafkaComponentBuildItem createKafkaComponent(CamelKafkaRecorder recorder) {
        return new CamelKafkaComponentBuildItem(recorder.createKafkaComponent());
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void createKafkaClientFactoryProducerBean(
        Capabilities capabilities,
        CamelKafkaRuntimeConfig kafkaRuntimeConfig,
        Optional<KubernetesServiceBindingConfig> kubernetesServiceBindingConfig,
        CamelKafkaComponentBuildItem camelKafkaComponent,
        CamelKafkaRecorder recorder) {
        if (capabilities.isPresent(Capability.KUBERNETES_SERVICE_BINDING) && kubernetesServiceBindingConfig.isPresent()) {
            recorder.configureKafkaClientFactory(
                camelKafkaComponent.getValue(),
                kafkaRuntimeConfig,
                kubernetesServiceBindingConfig.get());
        }
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
    public void configureKafkaComponentForDevServices(
        DevServicesKafkaBrokerBuildItem kafkaBrokerBuildItem,
        KafkaBuildTimeConfig kafkaBuildTimeConfig,
        CamelKafkaComponentBuildItem camelKafkaComponent,
        CamelKafkaRecorder recorder) {

        Config config = ConfigProvider.getConfig();
        Optional<String> brokers = config.getOptionalValue("camel.component.kafka.brokers", String.class);
        if (brokers.isEmpty() && kafkaBuildTimeConfig.devservices.enabled.orElse(true)) {
            recorder.configureKafkaComponentForDevServices(camelKafkaComponent.getValue(),
                kafkaBrokerBuildItem.getBootstrapServers());
        }
    }

    @BuildStep
    public CamelBeanBuildItem createKafkaComponentBean(CamelKafkaComponentBuildItem camelKafkaComponent) {
        return new CamelBeanBuildItem(
            "kafka",
            KafkaComponent.class.getName(),
            camelKafkaComponent.getValue());
    }
}
