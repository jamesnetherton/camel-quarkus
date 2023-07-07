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
package org.apache.camel.quarkus.support.xalan.deployment;

import java.util.Arrays;
import java.util.List;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedNativeImageClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JPMSExportBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ExcludeConfigBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBundleBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import org.apache.camel.quarkus.support.xalan.XalanTransformerFactory;

class XalanNativeImageProcessor {
    private static final String TRANSFORMER_FACTORY_SERVICE_FILE_PATH = "META-INF/services/javax.xml.transform.TransformerFactory";
    private static final String[] XALAN_PACKAGE_EXPORTS = new String[] {
            "com.sun.org.apache.xalan.internal.xsltc.runtime",
            "com.sun.org.apache.xalan.internal.xsltc.dom",
            "com.sun.org.apache.xalan.internal.xsltc",
            "com.sun.org.apache.xml.internal.dtm",
            "com.sun.org.apache.xml.internal.serializer"
    };

    @BuildStep
    ReflectiveClassBuildItem reflectiveClasses() {
        return ReflectiveClassBuildItem.builder("org.apache.camel.quarkus.support.xalan.XalanTransformerFactory").methods()
                .build();
    }

    @BuildStep
    List<NativeImageResourceBundleBuildItem> resourceBundles() {
        return Arrays.asList(
                new NativeImageResourceBundleBuildItem("com.sun.org.apache.xalan.internal.xsltc.compiler.util.ErrorMessages"),
                new NativeImageResourceBundleBuildItem("com.sun.org.apache.xml.internal.serializer.utils.SerializerMessages"),
                new NativeImageResourceBundleBuildItem("com.sun.org.apache.xml.internal.res.XMLErrorResources"));
    }

    @BuildStep
    void installTransformerFactory(
            BuildProducer<ExcludeConfigBuildItem> excludeConfig,
            BuildProducer<ServiceProviderBuildItem> serviceProvider) {

        excludeConfig
                .produce(new ExcludeConfigBuildItem("xalan\\.xalan-.*\\.jar", "/" + TRANSFORMER_FACTORY_SERVICE_FILE_PATH));
        serviceProvider.produce(new ServiceProviderBuildItem("javax.xml.transform.TransformerFactory",
                XalanTransformerFactory.class.getName()));

    }

    @BuildStep
    void xalanPackageExports(BuildProducer<JPMSExportBuildItem> packageExport) {
        Arrays.stream(XALAN_PACKAGE_EXPORTS)
                .map(packageName -> new JPMSExportBuildItem("java.xml", packageName))
                .forEach(packageExport::produce);
    }
}
