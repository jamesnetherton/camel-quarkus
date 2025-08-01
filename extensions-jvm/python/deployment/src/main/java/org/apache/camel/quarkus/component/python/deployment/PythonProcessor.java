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
package org.apache.camel.quarkus.component.python.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.quarkus.core.JvmOnlyRecorder;
import org.jboss.logging.Logger;

class PythonProcessor {

    private static final Logger LOG = Logger.getLogger(PythonProcessor.class);
    private static final String FEATURE = "camel-python";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Remove this once this extension starts supporting the native mode.
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    @Record(value = ExecutionTime.RUNTIME_INIT)
    void warnJvmInNative(JvmOnlyRecorder recorder) {
        JvmOnlyRecorder.warnJvmInNative(LOG, FEATURE); // warn at build time
        recorder.warnJvmInNative(FEATURE); // warn at runtime
    }
}
