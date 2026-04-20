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

package org.apache.camel.quarkus.dsl.xml.io.deployment;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.app.ApplicationDefinition;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslDiscoveryContext;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslMethodHandlerBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelToXMLDumperBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRouteResourceBuildItem;
import org.apache.camel.quarkus.dsl.xml.XmlIoDslRecorder;
import org.apache.camel.xml.in.ModelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlIoDslProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlIoDslProcessor.class);
    private static final String FEATURE = "camel-xml-io-dsl";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    CamelModelToXMLDumperBuildItem xmlModelDumper(XmlIoDslRecorder recorder) {
        return new CamelModelToXMLDumperBuildItem(recorder.newXmlIoModelToXMLDumper());
    }

    /**
     * Registers a DSL method handler for exception-related DSL elements in XML (onException, doCatch,
     * throwException). When these elements are discovered in XML routes, the handler registers the exception
     * types for reflection.
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    CamelDslMethodHandlerBuildItem registerExceptionHandlerDslMethods() {
        return new CamelDslMethodHandlerBuildItem(
                ctx -> ctx.produce(ReflectiveClassBuildItem.builder(ctx.getTypeName()).build()),
                "onException", "doCatch", "throwException");
    }

    /**
     * Parses XML DSL route files to detect type references in DSL elements registered by extensions
     * via CamelDslMethodHandlerBuildItem. Discovered types are passed to the registered handlers which can
     * produce BuildItems for native image configuration.
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void scanXmlDslMethods(
            List<CamelDslMethodHandlerBuildItem> handlers,
            List<CamelRouteResourceBuildItem> camelRouteResources,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<NativeImageResourceBuildItem> nativeResource) {

        if (handlers.isEmpty()) {
            return;
        }

        // Collect all method names that handlers are interested in
        Set<String> interestedMethods = new HashSet<>();
        for (CamelDslMethodHandlerBuildItem handler : handlers) {
            interestedMethods.addAll(handler.getMethodNames());
        }

        // Scan XML files for all interested methods
        Map<String, Map<String, Set<String>>> discoveries = new HashMap<>();

        for (CamelRouteResourceBuildItem routeResource : camelRouteResources) {
            String sourcePath = routeResource.getSourcePath();
            if (!sourcePath.endsWith(".xml")) {
                continue;
            }

            // Try each XML format: <routes>, <routeConfiguration(s)>, <beans>/<camel>
            if (tryParseRoutes(sourcePath, interestedMethods, discoveries)) {
                continue;
            }
            if (tryParseRouteConfigurations(sourcePath, interestedMethods, discoveries)) {
                continue;
            }
            tryParseApplication(sourcePath, interestedMethods, discoveries);
        }

        // Invoke handlers for each discovery
        for (CamelDslMethodHandlerBuildItem handler : handlers) {
            for (String methodName : handler.getMethodNames()) {
                Map<String, Set<String>> methodDiscoveries = discoveries.get(methodName);
                if (methodDiscoveries != null) {
                    for (Map.Entry<String, Set<String>> entry : methodDiscoveries.entrySet()) {
                        String sourceLocation = entry.getKey();
                        for (String typeName : entry.getValue()) {
                            CamelDslDiscoveryContext ctx = new CamelDslDiscoveryContext(
                                    methodName, typeName, sourceLocation, CamelDslDiscoveryContext.Source.XML_DSL);
                            handler.getHandler().accept(ctx);

                            // Forward produced BuildItems to actual producers
                            ctx.getProducedItems(ReflectiveClassBuildItem.class).forEach(reflectiveClass::produce);
                            ctx.getProducedItems(NativeImageResourceBuildItem.class).forEach(nativeResource::produce);
                        }
                    }
                }
            }
        }
    }

    private boolean tryParseRoutes(String sourcePath, Set<String> interestedMethods,
            Map<String, Map<String, Set<String>>> discoveries) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RoutesDefinition> routesOpt = parser.parseRoutesDefinition();
            if (routesOpt.isPresent()) {
                RoutesDefinition routes = routesOpt.get();
                collectOnExceptionClasses(routes.getOnExceptions(), interestedMethods, discoveries, sourcePath);
                if (routes.getRoutes() != null) {
                    for (RouteDefinition route : routes.getRoutes()) {
                        collectExceptionClassesFromOutputs(route.getOutputs(), interestedMethods, discoveries, sourcePath);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route resource: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseRouteConfigurations(String sourcePath, Set<String> interestedMethods,
            Map<String, Map<String, Set<String>>> discoveries) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RouteConfigurationsDefinition> rcOpt = parser.parseRouteConfigurationsDefinition();
            if (rcOpt.isPresent()) {
                for (RouteConfigurationDefinition rc : rcOpt.get().getRouteConfigurations()) {
                    collectOnExceptionClasses(rc.getOnExceptions(), interestedMethods, discoveries, sourcePath);
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route configuration: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseApplication(String sourcePath, Set<String> interestedMethods,
            Map<String, Map<String, Set<String>>> discoveries) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<ApplicationDefinition> appOpt = parser.parseApplicationDefinition();
            if (appOpt.isPresent()) {
                ApplicationDefinition app = appOpt.get();
                if (app.getRoutes() != null) {
                    for (RouteDefinition route : app.getRoutes()) {
                        collectExceptionClassesFromOutputs(route.getOutputs(), interestedMethods, discoveries, sourcePath);
                    }
                }
                if (app.getRouteConfigurations() != null) {
                    for (RouteConfigurationDefinition rc : app.getRouteConfigurations()) {
                        collectOnExceptionClasses(rc.getOnExceptions(), interestedMethods, discoveries, sourcePath);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML application definition: {}", sourcePath, e);
        }
        return false;
    }

    private static void collectOnExceptionClasses(List<OnExceptionDefinition> onExceptions, Set<String> interestedMethods,
            Map<String, Map<String, Set<String>>> discoveries, String sourcePath) {
        if (onExceptions != null && interestedMethods.contains("onException")) {
            for (OnExceptionDefinition onEx : onExceptions) {
                for (String exceptionClass : onEx.getExceptions()) {
                    discoveries.computeIfAbsent("onException", k -> new HashMap<>())
                            .computeIfAbsent(sourcePath, k -> new HashSet<>())
                            .add(exceptionClass);
                }
            }
        }
    }

    private static void collectExceptionClassesFromOutputs(List<ProcessorDefinition<?>> outputs,
            Set<String> interestedMethods, Map<String, Map<String, Set<String>>> discoveries, String sourcePath) {
        if (outputs == null) {
            return;
        }
        for (ProcessorDefinition<?> output : outputs) {
            if (output instanceof OnExceptionDefinition onEx && interestedMethods.contains("onException")) {
                for (String exceptionClass : onEx.getExceptions()) {
                    discoveries.computeIfAbsent("onException", k -> new HashMap<>())
                            .computeIfAbsent(sourcePath, k -> new HashSet<>())
                            .add(exceptionClass);
                }
            } else if (output instanceof CatchDefinition catchDef && interestedMethods.contains("doCatch")) {
                for (String exceptionClass : catchDef.getExceptions()) {
                    discoveries.computeIfAbsent("doCatch", k -> new HashMap<>())
                            .computeIfAbsent(sourcePath, k -> new HashSet<>())
                            .add(exceptionClass);
                }
            } else if (output instanceof ThrowExceptionDefinition throwEx && interestedMethods.contains("throwException")) {
                if (throwEx.getExceptionType() != null) {
                    discoveries.computeIfAbsent("throwException", k -> new HashMap<>())
                            .computeIfAbsent(sourcePath, k -> new HashSet<>())
                            .add(throwEx.getExceptionType());
                }
            }
            collectExceptionClassesFromOutputs(output.getOutputs(), interestedMethods, discoveries, sourcePath);
        }
    }
}
