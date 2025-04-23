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
package org.apache.camel.quarkus.core.deployment;

import java.util.List;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.RawCommandLineArgumentsBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import org.apache.camel.quarkus.core.CamelBootstrapRecorder;
import org.apache.camel.quarkus.core.CamelRuntimeConfig;
import org.apache.camel.quarkus.core.deployment.spi.CamelBootstrapCompletedBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.DisableCamelRuntimeAutoStartupBuildItem;

class CamelBootstrapProcessor {
    /**
     * Starts the given {@link CamelRuntimeBuildItem}.
     */
    @BuildStep
    @Record(value = ExecutionTime.RUNTIME_INIT)
    @Produce(CamelBootstrapCompletedBuildItem.class)
    void boot(
            CamelBootstrapRecorder recorder,
            CamelRuntimeBuildItem runtime,
            RawCommandLineArgumentsBuildItem commandLineArguments,
            List<DisableCamelRuntimeAutoStartupBuildItem> disableCamelRuntimeAutoStartupBuildItems,
            ShutdownContextBuildItem shutdown,
            BuildProducer<ServiceStartBuildItem> serviceStartBuildItems,
            CamelRuntimeConfig camelRuntimeConfig) {

        recorder.addShutdownTask(shutdown, runtime.runtime());
        if (runtime.isAutoStartup() && disableCamelRuntimeAutoStartupBuildItems.isEmpty()) {
            System.out.println("=====> STARTING!!!");
            //recorder.start(camelRuntimeConfig, runtime.runtime(), commandLineArguments, CamelQuarkusVersion.getVersion());
        }
        /* Make sure that Quarkus orders this method before starting to serve HTTP endpoints.
         * Otherwise first requests might reach Camel context in a non-yet-started state. */
        serviceStartBuildItems.produce(new ServiceStartBuildItem("camel-runtime"));
    }
}
