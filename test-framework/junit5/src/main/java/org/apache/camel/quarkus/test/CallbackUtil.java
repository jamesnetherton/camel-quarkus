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
package org.apache.camel.quarkus.test;

import java.util.Optional;
import java.util.Set;

import io.quarkus.arc.Arc;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.engine.execution.NamespaceAwareStore;
import org.junit.platform.engine.support.store.NamespacedHierarchicalStore;

public class CallbackUtil {
    private CallbackUtil() {
        // Utility class
    }

    static boolean isPerClass(CamelQuarkusTestSupport testSupport) {
        return getLifecycle(testSupport).filter(lc -> lc.equals(TestInstance.Lifecycle.PER_CLASS)).isPresent();
    }

    static Optional<TestInstance.Lifecycle> getLifecycle(CamelQuarkusTestSupport testSupport) {
        if (testSupport.getClass().getAnnotation(TestInstance.class) != null) {
            return Optional.of(testSupport.getClass().getAnnotation(TestInstance.class).value());
        }

        return Optional.empty();
    }

    static void resetContext(CamelQuarkusTestSupport testInstance) {
        CamelContext context = testInstance.context();

        //if routeBuilder (from the test) was used, all routes from that builder has to be stopped and removed
        //because routes will be created again (in case of TestInstance.Lifecycle.PER_CLASS, this method is not executed)
        Set<String> createdRoutes = testInstance.getCreatedRoutes();
        if (testInstance.testConfigurationBuilder().useRouteBuilder() && createdRoutes != null) {
            try {
                for (String routeId : createdRoutes) {
                    context.getRouteController().stopRoute(routeId);
                    context.removeRoute(routeId);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // Recreate all non-test routes since they may have been modified in ways that can break following tests
        try {
            MockEndpoint.resetMocks(context);
            context.getComponentNames().forEach(context::removeComponent);

            if (CamelTestSupportHelper.isReloadRoutes()) {
                System.out.println("========> Restoring original application route state");

                // Retrieve the original route state captured on startup in CamelQuarkusDumpTestRoutesStrategy
                CamelQuarkusDumpTestRoutesStrategy strategy = Arc.container()
                        .select(CamelQuarkusDumpTestRoutesStrategy.class, CamelQuarkusTestSupportRouteDumper.Literal.INSTANCE)
                        .get();

                // Remove existing routes and restore original
                CamelTestSupportHelper.reloadCamelRoutes(strategy);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class MockExtensionContext {
        private final Optional<TestInstance.Lifecycle> lifecycle;
        private final String currentTestName;
        private final ExtensionContext.Store globalStore;

        public MockExtensionContext(Optional<TestInstance.Lifecycle> lifecycle, String currentTestName) {
            this.lifecycle = lifecycle;
            this.currentTestName = currentTestName;
            this.globalStore = new NamespaceAwareStore(new NamespacedHierarchicalStore<>(null),
                    ExtensionContext.Namespace.GLOBAL);
        }

        public String getDisplayName() {
            return currentTestName;
        }

        public Optional<TestInstance.Lifecycle> getTestInstanceLifecycle() {
            return lifecycle;
        }

        public ExtensionContext.Store getStore(ExtensionContext.Namespace namespace) {
            if (namespace == ExtensionContext.Namespace.GLOBAL) {
                return globalStore;
            }
            return null;
        }
    }
}
