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
package org.apache.camel.quarkus.component.rest.openapi.deployment;

import java.util.List;

import com.github.fge.msgsimple.load.MessageBundleLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceDirectoryBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;

class RestOpenapiProcessor {

    private static final String FEATURE = "camel-rest-openapi";
    private static final List<String> GROUP_IDS_TO_INDEX = List.of("com.github.java-json-tools", "com.atlassian.oai");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(CurateOutcomeBuildItem curateOutcome, BuildProducer<IndexDependencyBuildItem> indexedDependency) {
        curateOutcome.getApplicationModel()
                .getDependencies()
                .stream()
                .filter(dependency -> GROUP_IDS_TO_INDEX.contains(dependency.getGroupId()))
                .map(dependency -> new IndexDependencyBuildItem(dependency.getGroupId(), dependency.getArtifactId()))
                .forEach(indexedDependency::produce);
    }

    @BuildStep
    void registerForReflection(CombinedIndexBuildItem combinedIndex, BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        combinedIndex.getIndex()
                .getAllKnownImplementors(MessageBundleLoader.class)
                .stream()
                .map(classInfo -> ReflectiveClassBuildItem.builder(classInfo.name().toString()).build())
                .forEach(reflectiveClass::produce);
    }

    @BuildStep
    void nativeImageResources(
            BuildProducer<NativeImageResourceDirectoryBuildItem> nativeImageResourceDirectory,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResource) {
        nativeImageResourceDirectory.produce(new NativeImageResourceDirectoryBuildItem("swagger/validation"));
        nativeImageResourceDirectory.produce(new NativeImageResourceDirectoryBuildItem("draftv3"));
        nativeImageResourceDirectory.produce(new NativeImageResourceDirectoryBuildItem("draftv4"));
        nativeImageResource.produce(new NativeImageResourceBuildItem("com/github/fge/uritemplate/messages.properties"));
    }
}
