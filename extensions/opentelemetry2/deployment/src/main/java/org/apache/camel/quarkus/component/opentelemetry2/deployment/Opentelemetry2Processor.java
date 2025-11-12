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
package org.apache.camel.quarkus.component.opentelemetry2.deployment;

import java.util.function.BooleanSupplier;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.VertxWebRouterBuildItem;
import org.apache.camel.quarkus.component.opentelemetry2.OpenTelemetry2Recorder;
import org.apache.camel.quarkus.component.opentelemetry2.OpenTelemetry2TracerProducer;
import org.apache.camel.telemetry.Tracer;

import static io.quarkus.bootstrap.classloading.QuarkusClassLoader.isClassPresentAtRuntime;

class Opentelemetry2Processor {

    private static final String FEATURE = "camel-opentelemetry2";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem telemetryDevTracerProducerBean() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClass(OpenTelemetry2TracerProducer.class)
                .build();
    }

    @BuildStep
    UnremovableBeanBuildItem camelTracerUnremovableBean() {
        return UnremovableBeanBuildItem.beanTypes(Tracer.class);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = PlatformHttpExtensionIsPresent.class)
    void configureVertxWebTracePropagation(
            VertxWebRouterBuildItem routerBuildItem,
            OpenTelemetry2Recorder recorder) {
        recorder.configureVertxWebTracePropagation(routerBuildItem.getHttpRouter());
    }

    static final class PlatformHttpExtensionIsPresent implements BooleanSupplier {
        private static final boolean IS_PLATFORM_HTTP_EXTENSION_PRESENT = isClassPresentAtRuntime(
                "org.apache.camel.component.platform.http.PlatformHttpComponent");

        @Override
        public boolean getAsBoolean() {
            return IS_PLATFORM_HTTP_EXTENSION_PRESENT;
        }
    }
}
