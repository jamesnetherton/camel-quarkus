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
package org.apache.camel.quarkus.component.azure.storage.queue.deployment;

import com.azure.xml.XmlSerializable;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

class AzureStorageQueueProcessor {

    private static final Logger LOG = Logger.getLogger(AzureStorageQueueProcessor.class);
    private static final String FEATURE = "camel-azure-storage-queue";
    private static final DotName XML_SERIALIZABLE_NAME = DotName.createSimple(XmlSerializable.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem activateSslNativeSupport() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    @BuildStep
    void reflectiveClasses(CombinedIndexBuildItem combinedIndex, BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        final String[] modelClasses = combinedIndex
                .getIndex()
                .getKnownClasses()
                .stream()
                .map(ClassInfo::name)
                .map(DotName::toString)
                .filter(n -> n.startsWith("com.azure.storage.queue.implementation.models.")
                        || n.startsWith("com.azure.storage.queue.models."))
                .sorted()
                .toArray(String[]::new);
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(modelClasses).fields().build());

        final String[] knownSerializableImpls = combinedIndex.getIndex()
                .getAllKnownImplementations(XML_SERIALIZABLE_NAME)
                .stream()
                .map(ClassInfo::name)
                .map(DotName::toString)
                .sorted()
                .toArray(String[]::new);
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(knownSerializableImpls).methods().build());
    }

    @BuildStep
    IndexDependencyBuildItem indexDependency() {
        return new IndexDependencyBuildItem("com.azure", "azure-storage-queue");
    }
}
