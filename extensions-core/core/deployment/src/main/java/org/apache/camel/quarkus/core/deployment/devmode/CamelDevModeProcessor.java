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
package org.apache.camel.quarkus.core.deployment.devmode;

import java.util.List;

import io.quarkus.core.Phase;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import org.apache.camel.CamelContext;
import org.apache.camel.quarkus.core.CamelBootstrap;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelMainBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRouteResourceBuildItem;
import org.apache.camel.quarkus.main.CamelMain;
import org.jboss.logging.Logger;

import static org.apache.camel.quarkus.core.CamelCapabilities.DSL_MODELINE;

/**
 * Build steps relating to customizations that should only be made in Dev Mode.
 */
@BuildSteps(onlyIf = IsDevelopment.class)
class CamelDevModeProcessor {
    private static final Logger LOGGER = Logger.getLogger(CamelDevModeProcessor.class);

    @BuildStep
    void customizeDevModeCamelContext(
            ServiceRegistrar serviceRegistrar,
            // CamelContextBuildItem placeholder ensures this build step runs after the CamelContext is created
            CamelContextBuildItem camelContext,
            Capabilities capabilities) {
        boolean isModeLineDslAbsent = capabilities.isMissing(DSL_MODELINE);
        serviceRegistrar.forService("camel.dev-mode.context")
                .atPhase(Phase.STATIC_INIT)
                .require(CamelContext.class)
                .onStart((ctx, context) -> CamelBootstrap.customizeDevModeCamelContext(context, isModeLineDslAbsent));
    }

    @BuildStep
    void customizeDevModeCamelMain(
            ServiceRegistrar serviceRegistrar,
            // CamelMainBuildItem placeholder ensures this build step runs after camel-main is created
            CamelMainBuildItem camelMain) {
        serviceRegistrar.forService("camel.dev-mode.main")
                .atPhase(Phase.STATIC_INIT)
                .require(CamelMain.class)
                .onStart((ctx, main) -> CamelBootstrap.customizeDevModeCamelMain(main));
    }

    @BuildStep
    void locations(
            List<CamelRouteResourceBuildItem> camelRouteResources,
            BuildProducer<HotDeploymentWatchedFileBuildItem> hotDeploymentWatchedFile) {

        camelRouteResources.stream()
                .filter(CamelRouteResourceBuildItem::isHotReloadable)
                .map(CamelRouteResourceBuildItem::getSourcePath)
                .peek(location -> LOGGER.debugf("Configuring watched file %s", location))
                .distinct()
                .map(HotDeploymentWatchedFileBuildItem::new)
                .forEach(hotDeploymentWatchedFile::produce);

        if (!camelRouteResources.isEmpty()) {
            LOGGER.info("Camel routes live reload enabled");
        }
    }
}
