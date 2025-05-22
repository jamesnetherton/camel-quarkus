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

import java.lang.reflect.Field;
import java.util.Set;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.AbstractCamelContext;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.eclipse.microprofile.config.ConfigProvider;

final class CamelTestSupportHelper {
    private CamelTestSupportHelper() {
        // Utility class
    }

    static boolean isReloadRoutes() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.camel.test.reload-routes", boolean.class)
                .orElse(false);
    }

    static void reloadCamelRoutes() throws Exception {
        // Retrieve the original route state captured on startup in CamelQuarkusDumpTestRoutesStrategy
        InjectableInstance<CamelQuarkusDumpTestRoutesStrategy> instance = Arc.container()
                .select(CamelQuarkusDumpTestRoutesStrategy.class, CamelQuarkusTestSupportRouteDumper.Literal.INSTANCE);
        if (instance.isUnsatisfied()) {
            return;
        }

        CamelQuarkusDumpTestRoutesStrategy strategy = instance.get();

        // Remove routes
        CamelContext context = strategy.getCamelContext();
        context.getEndpointRegistry().clear();

        // endpointStrategies is package-private so use reflection to manipulate it
        Field field = AbstractCamelContext.class.getDeclaredField("endpointStrategies");
        field.setAccessible(true);
        Set<EndpointStrategy> endpointStrategies = (Set<EndpointStrategy>) field.get(context);
        endpointStrategies.clear();

        // Reload original routes
        RoutesLoader routesLoader = PluginHelper.getRoutesLoader(context);
        routesLoader.loadRoutes(strategy.getResources());
    }
}
