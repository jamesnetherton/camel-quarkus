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
package org.apache.camel.quarkus.core.tls.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.deployment.VertxBuildItem;
import org.apache.camel.quarkus.core.deployment.catalog.SchemaResource;
import org.apache.camel.quarkus.core.deployment.spi.BuildTimeCamelCatalogBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeTaskBuildItem;
import org.apache.camel.quarkus.core.tls.TlsCertificateReloadObserver;
import org.apache.camel.quarkus.core.tls.TlsRegistryRecorder;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;

public class TlsRegistryProcessor {
    private static final String FEATURE = "camel-tls-registry";
    private static final String SSL_CONTEXT_PARAMETERS = "org.apache.camel.support.jsse.SSLContextParameters";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerReloadObserver() {
        return AdditionalBeanBuildItem.unremovableOf(TlsCertificateReloadObserver.class);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    CamelRuntimeTaskBuildItem registerTlsConfigurations(
            TlsRegistryRecorder recorder,
            CamelContextBuildItem camelContext,
            VertxBuildItem vertx,
            BuildTimeCamelCatalogBuildItem catalog) {

        Map<String, List<String>> endpointSslOptions = new TreeMap<>();
        Map<String, List<String>> componentSslOptions = new TreeMap<>();
        for (SchemaResource resource : catalog.getCatalog().getJSonSchemaResolver().getSchemaResources()) {
            if (!"component".equals(resource.getType())) {
                continue;
            }
            // A component class can serve several schemes, each with its own JSON schema
            for (String scheme : resource.getName().split(",")) {
                SchemaResource schemeResource = new SchemaResource();
                schemeResource.setType(resource.getType());
                schemeResource.setName(scheme.trim());
                schemeResource.setClassName(resource.getClassName());
                String json = schemeResource.load();
                if (json == null) {
                    continue;
                }
                ComponentModel model = JsonMapper.generateComponentModel(json);
                addSslOptions(endpointSslOptions, model.getScheme(), model.getEndpointOptions());
                addSslOptions(componentSslOptions, model.getScheme(), model.getComponentOptions());
            }
        }

        recorder.registerTlsConfigurations(camelContext.getCamelContext(), vertx.getVertx(), endpointSslOptions,
                componentSslOptions);

        return new CamelRuntimeTaskBuildItem("tls-registry");
    }

    private static void addSslOptions(Map<String, List<String>> target, String scheme,
            List<? extends BaseOptionModel> options) {
        List<String> names = options.stream()
                .filter(option -> SSL_CONTEXT_PARAMETERS.equals(option.getJavaType()))
                .map(BaseOptionModel::getName)
                .toList();
        if (!names.isEmpty()) {
            target.put(scheme, new ArrayList<>(names));
        }
    }
}
