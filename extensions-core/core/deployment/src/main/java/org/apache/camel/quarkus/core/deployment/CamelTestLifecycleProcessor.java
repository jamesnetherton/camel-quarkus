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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import io.quarkus.core.Phase;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.IsTest;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.TestProfileBuildItem;
import org.apache.camel.quarkus.core.test.CamelTestLifecycleHook;
import org.apache.camel.quarkus.core.test.CamelTestLifecycleMainListener;
import org.apache.camel.quarkus.main.CamelMain;

import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.TEST_LIFECYCLE;

public class CamelTestLifecycleProcessor {
    private static final String HOOK_SERVICE_RESOURCE = "META-INF/services/" + CamelTestLifecycleHook.class.getName();

    /**
     * Registers a {@link org.apache.camel.main.MainListener} that delegates to every {@link CamelTestLifecycleHook}
     * implementation, when the application is built for tests and at least one implementation is present.
     */
    @BuildStep(onlyIf = IsTest.class)
    void testLifecycleHooks(
            ServiceRegistrar serviceRegistrar,
            Optional<TestProfileBuildItem> testProfile) {
        if (!hasHookImplementations()) {
            return;
        }
        String testProfileClassName = testProfile.map(TestProfileBuildItem::getTestProfileClassName).orElse("");
        serviceRegistrar.forService(TEST_LIFECYCLE)
                .atPhase(Phase.STATIC_INIT)
                .require(CamelMain.class)
                .onStart((ctx, main) -> main.addMainListener(new CamelTestLifecycleMainListener(testProfileClassName)));
    }

    static boolean hasHookImplementations() {
        try {
            return Thread.currentThread().getContextClassLoader().getResources(HOOK_SERVICE_RESOURCE).hasMoreElements();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
