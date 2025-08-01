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
package org.apache.camel.quarkus.component.kubernetes.cluster.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.RuntimeValue;
import org.apache.camel.component.kubernetes.cluster.KubernetesClusterService;
import org.apache.camel.quarkus.component.kubernetes.cluster.KubernetesClusterServiceBuildTimeConfig;
import org.apache.camel.quarkus.component.kubernetes.cluster.KubernetesClusterServiceRecorder;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.apache.camel.support.cluster.RebalancingCamelClusterService;

class KubernetesClusterServiceProcessor {

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = KubernetesClusterServiceBuildTimeConfig.Enabled.class)
    @Consume(CamelContextBuildItem.class)
    CamelRuntimeBeanBuildItem setupKubernetesClusterService(
            KubernetesClusterServiceBuildTimeConfig buildTimeConfig,
            KubernetesClusterServiceRecorder recorder) {

        if (buildTimeConfig.rebalancing()) {
            final RuntimeValue<RebalancingCamelClusterService> krcs = recorder
                    .createKubernetesRebalancingClusterService();
            return new CamelRuntimeBeanBuildItem("kubernetesRebalancingClusterService",
                    RebalancingCamelClusterService.class.getName(), krcs);
        } else {
            final RuntimeValue<KubernetesClusterService> kcs = recorder.createKubernetesClusterService();
            return new CamelRuntimeBeanBuildItem("kubernetesClusterService", KubernetesClusterService.class.getName(), kcs);
        }
    }
}
