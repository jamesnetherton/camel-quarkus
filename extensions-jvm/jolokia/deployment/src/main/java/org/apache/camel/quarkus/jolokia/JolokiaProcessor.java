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
package org.apache.camel.quarkus.jolokia;

import java.util.function.BooleanSupplier;

import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.tls.TlsCertificateBuildItem;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.jolokia.server.core.http.HttpRequestHandler;
import org.jolokia.server.core.service.api.JolokiaContext;

@BuildSteps(onlyIf = JolokiaProcessor.JolokiaEnabled.class)
public class JolokiaProcessor {
    private static final String FEATURE = "camel-jolokia";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    JolokiaContextBuildItem startJolokiaServiceManager(
            ShutdownContextBuildItem shutdownContextBuildItem,
            CamelJolokiaRecorder recorder) {
        RuntimeValue<JolokiaContext> jolokiaContext = recorder.startJolokiaServiceManager(shutdownContextBuildItem);
        return new JolokiaContextBuildItem(jolokiaContext);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    JolokiaHttpRequestHandlerBuildItem createJolokiaHttpRequestHandler(
            JolokiaContextBuildItem jolokiaContext,
            CamelJolokiaRecorder recorder) {
        RuntimeValue<HttpRequestHandler> jolokiaHttpRequestHandler = recorder
                .createJolokiaHttpRequestHandler(jolokiaContext.getRuntimeValue());
        return new JolokiaHttpRequestHandlerBuildItem(jolokiaHttpRequestHandler);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createManagementRoute(
            JolokiaHttpRequestHandlerBuildItem jolokiaHttpRequestHandler,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            BodyHandlerBuildItem bodyHandler,
            BuildProducer<RouteBuildItem> routes,
            CamelJolokiaRecorder recorder) {

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction("jolokia/*", recorder.route(bodyHandler.getHandler()))
                .handler(recorder.getHandler(jolokiaHttpRequestHandler.getHandler()))
                .blockingRoute()
                .build());
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = JolokiaKubernetesSupportEnabled.class)
    void kubernetesConfiguration(
            JolokiaRuntimeConfig runtimeConfig,
            BuildProducer<RunTimeConfigBuilderBuildItem> runtimeConfigBuilder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBean,
            BuildProducer<TlsCertificateBuildItem> tlsCertificate,
            CamelJolokiaRecorder recorder) {

        // Set up Quarkus security configuration
        //runtimeConfigBuilder.produce(new RunTimeConfigBuilderBuildItem(JolokiaRuntimeConfigBuilder.class));

        // Configure SecurityIdentityAugmentor for client auth
        syntheticBean.produce(
                SyntheticBeanBuildItem.create(SecurityIdentityAugmentor.class)
                        .runtimeValue(recorder.createSecurityIdentityAugmentor(runtimeConfig))
                        .scope(ApplicationScoped.class)
                        .setRuntimeInit()
                        .done());

        tlsCertificate.produce(new TlsCertificateBuildItem("jolokia", recorder.createSupplier(runtimeConfig)));
    }

    static final class JolokiaEnabled implements BooleanSupplier {
        JolokiaBuildTimeConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.enabled();
        }
    }

    static final class JolokiaKubernetesSupportEnabled implements BooleanSupplier {
        JolokiaBuildTimeConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.kubernetes().enabled();
        }
    }
}
