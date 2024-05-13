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
package org.apache.camel.quarkus.component.infinispan.deployment;

import java.util.function.BooleanSupplier;
import java.util.stream.StreamSupport;

import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.infinispan.client.deployment.InfinispanClientNameBuildItem;
import io.quarkus.infinispan.client.runtime.InfinispanClientUtil;
import io.quarkus.infinispan.client.runtime.InfinispanClientsBuildTimeConfig;
import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.ConfigProvider;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;

class InfinispanProcessor {

    private static final String FEATURE = "camel-infinispan";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    NativeImageResourceBuildItem nativeImageResources() {
        // Only required when Camel instantiates and manages its own internal CacheContainer
        return new NativeImageResourceBuildItem("org/infinispan/protostream/message-wrapping.proto");
    }

    @BuildStep
    ReflectiveClassBuildItem reflectiveClasses() {
        // Only required when Camel instantiates and manages its own internal CacheContainer
        return ReflectiveClassBuildItem.builder(ProtoStreamMarshaller.class).build();
    }

    @BuildStep(onlyIf = { GlobalDevServicesConfig.Enabled.class, InfinispanDevOrTestMode.class })
    InfinispanClientNameBuildItem produceDefaultInfinispanClientBeanIfRequiredForDevOrTestMode(
            BeanDiscoveryFinishedBuildItem beans) {
        boolean infinispanComponentBeansAbsent = beans.getBeans()
                .stream()
                .filter(BeanInfo::isProducerMethod)
                .map(BeanInfo::getImplClazz)
                .noneMatch(classInfo -> classInfo.toString().startsWith("org.apache.camel.component.infinispan"));

        Iterable<String> propertyNames = ConfigProvider.getConfig().getPropertyNames();
        boolean infinispanComponentPropertiesAbsent = StreamSupport.stream(propertyNames.spliterator(), false)
                .noneMatch(key -> key.startsWith("camel.component.infinispan"));

        // To help UX in dev / test mode, try to determine if Camel Infinispan beans or config properties have been configured.
        if (infinispanComponentBeansAbsent && infinispanComponentPropertiesAbsent) {
            // Assume plain Quarkus Infinispan is to be used and force creation of the default client bean.
            // As it's disabled by default by InfinispanDevServicesConfigBuilder
            return new InfinispanClientNameBuildItem(InfinispanClientUtil.DEFAULT_INFINISPAN_CLIENT_NAME);
        }

        return null;
    }

    static final class InfinispanDevOrTestMode implements BooleanSupplier {
        InfinispanClientsBuildTimeConfig infinispanConfig;

        @Override
        public boolean getAsBoolean() {
            return devServicesEnabled() && isDevOrTestMode();
        }

        boolean devServicesEnabled() {
            return infinispanConfig.defaultInfinispanClient != null
                    && infinispanConfig.defaultInfinispanClient.devService.devservices.enabled;
        }

        boolean isDevOrTestMode() {
            return LaunchMode.current().isDevOrTest();
        }
    }
}
