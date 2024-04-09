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

import java.util.Collections;
import java.util.Map;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesAdditionalConfigBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.apache.camel.quarkus.core.CamelCapabilities;
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

    @BuildStep
    DevServicesAdditionalConfigBuildItem disableDevServicesIfCamelKPresent(Capabilities capabilities) {
        // Prevents the Infinispan extension creating a useless empty RemoteCacheManager bean
        // which is subsequently autowired into the Camel Infinispan component at runtime
        // https://github.com/apache/camel-quarkus/issues/5965
        return new DevServicesAdditionalConfigBuildItem(devServicesConfig -> {
            if (capabilities.isPresent(CamelCapabilities.CAMEL_K_CORE)) {
                System.out.println("=======> GLA LA LA LA LA LA LA LA LA LA LA. LA LA LA LA LA LA LA LA BUM BUM BUM");
                return Map.of("quarkus.infinispan-client.devservices.enabled", "false");
            }
            return Collections.emptyMap();
        });
    }
}
