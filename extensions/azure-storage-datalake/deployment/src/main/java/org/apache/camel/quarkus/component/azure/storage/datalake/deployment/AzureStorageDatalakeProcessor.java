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
package org.apache.camel.quarkus.component.azure.storage.datalake.deployment;

import java.util.LinkedHashSet;

import com.azure.core.annotation.ServiceInterface;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

class AzureStorageDatalakeProcessor {

    private static final Logger LOG = Logger.getLogger(AzureStorageDatalakeProcessor.class);
    private static final String FEATURE = "camel-azure-storage-datalake";

    private static final DotName SERVICE_INSTANCE_ANNOTATION = DotName.createSimple(ServiceInterface.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem activateSslNativeSupport() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    @BuildStep
    ReflectiveClassBuildItem registerForReflection(CombinedIndexBuildItem combinedIndex) {
        IndexView index = combinedIndex.getIndex();

        LinkedHashSet<String> dtos = new LinkedHashSet<>(index.getKnownClasses().stream()
                .map(ci -> ci.name().toString())
                .filter(n -> n.startsWith("com.azure.storage.file.datalake.implementation.models"))
                .toList());

        dtos.add("com.azure.storage.file.datalake.implementation.ServicesImpl$ServicesService");

        return ReflectiveClassBuildItem.builder(dtos.toArray(String[]::new)).methods().fields().build();
    }

    @BuildStep
    void registerBeanHandlersForReflection(BuildProducer<NativeImageProxyDefinitionBuildItem> proxiesProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveProdicer,
            CombinedIndexBuildItem combinedIndex) {
        IndexView index = combinedIndex.getIndex();

        index.getAnnotations(SERVICE_INSTANCE_ANNOTATION).stream()
                .map(annotationInstance -> annotationInstance.target().asClass().name().toString())
                .forEach(ci -> {
                    proxiesProducer.produce(new NativeImageProxyDefinitionBuildItem(ci));
                });
    }

    @BuildStep
    IndexDependencyBuildItem registerDependencyForIndex() {
        return new IndexDependencyBuildItem("com.azure", "azure-storage-file-datalake");
    }

}
