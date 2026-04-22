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
                XmlIoDslProcessor::extractExceptionTypesFromXml,
                null, // No YAML extractor needed - YAML DSL has its own handler
                "onException", "doCatch", "throwException");
    }

    /**
     * Extracts exception type references from XML DSL ProcessorDefinitions.
     */
    private static Map<String, Set<String>> extractExceptionTypesFromXml(Object processorDef) {
        Map<String, Set<String>> result = new HashMap<>();

        if (processorDef instanceof OnExceptionDefinition onEx) {
            Set<String> exceptions = new HashSet<>(onEx.getExceptions());
            if (!exceptions.isEmpty()) {
                result.put("onException", exceptions);
            }
        } else if (processorDef instanceof CatchDefinition catchDef) {
            Set<String> exceptions = new HashSet<>(catchDef.getExceptions());
            if (!exceptions.isEmpty()) {
                result.put("doCatch", exceptions);
            }
        } else if (processorDef instanceof ThrowExceptionDefinition throwEx) {
            if (throwEx.getExceptionType() != null) {
                result.put("throwException", Set.of(throwEx.getExceptionType()));
            }
        }

        return result;
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

        // Filter handlers that have XML extractors
        List<CamelDslMethodHandlerBuildItem> xmlHandlers = handlers.stream()
                .filter(h -> h.getXmlExtractor() != null)
                .collect(java.util.stream.Collectors.toList());

        if (xmlHandlers.isEmpty()) {
            return;
        }

        // Scan XML files using handler extractors
        Map<String, Map<String, Set<String>>> discoveries = new HashMap<>();

        for (CamelRouteResourceBuildItem routeResource : camelRouteResources) {
            String sourcePath = routeResource.getSourcePath();
            if (!sourcePath.endsWith(".xml")) {
                continue;
            }

            // Try each XML format: <routes>, <routeConfiguration(s)>, <beans>/<camel>
            if (tryParseRoutes(sourcePath, xmlHandlers, discoveries)) {
                continue;
            }
            if (tryParseRouteConfigurations(sourcePath, xmlHandlers, discoveries)) {
                continue;
            }
            tryParseApplication(sourcePath, xmlHandlers, discoveries);
        }

        // Invoke handlers for each discovery
        for (CamelDslMethodHandlerBuildItem handler : xmlHandlers) {
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

    private boolean tryParseRoutes(String sourcePath, List<CamelDslMethodHandlerBuildItem> handlers,
            Map<String, Map<String, Set<String>>> discoveries) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RoutesDefinition> routesOpt = parser.parseRoutesDefinition();
            if (routesOpt.isPresent()) {
                RoutesDefinition routes = routesOpt.get();
                collectOnExceptionClasses(routes.getOnExceptions(), handlers, discoveries, sourcePath);
                if (routes.getRoutes() != null) {
                    for (RouteDefinition route : routes.getRoutes()) {
                        collectFromOutputs(route.getOutputs(), handlers, discoveries, sourcePath);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route resource: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseRouteConfigurations(String sourcePath, List<CamelDslMethodHandlerBuildItem> handlers,
            Map<String, Map<String, Set<String>>> discoveries) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RouteConfigurationsDefinition> rcOpt = parser.parseRouteConfigurationsDefinition();
            if (rcOpt.isPresent()) {
                for (RouteConfigurationDefinition rc : rcOpt.get().getRouteConfigurations()) {
                    collectOnExceptionClasses(rc.getOnExceptions(), handlers, discoveries, sourcePath);
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route configuration: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseApplication(String sourcePath, List<CamelDslMethodHandlerBuildItem> handlers,
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
                        collectFromOutputs(route.getOutputs(), handlers, discoveries, sourcePath);
                    }
                }
                if (app.getRouteConfigurations() != null) {
                    for (RouteConfigurationDefinition rc : app.getRouteConfigurations()) {
                        collectOnExceptionClasses(rc.getOnExceptions(), handlers, discoveries, sourcePath);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML application definition: {}", sourcePath, e);
        }
        return false;
    }

    private static void collectOnExceptionClasses(List<OnExceptionDefinition> onExceptions,
            List<CamelDslMethodHandlerBuildItem> handlers,
            Map<String, Map<String, Set<String>>> discoveries, String sourcePath) {
        if (onExceptions == null) {
            return;
        }
        for (OnExceptionDefinition onEx : onExceptions) {
            // Try each handler's extractor on this OnExceptionDefinition
            for (CamelDslMethodHandlerBuildItem handler : handlers) {
                Map<String, Set<String>> extracted = handler.getXmlExtractor().apply(onEx);
                mergeDiscoveries(extracted, discoveries, sourcePath);
            }
        }
    }

    private static void collectFromOutputs(List<ProcessorDefinition<?>> outputs,
            List<CamelDslMethodHandlerBuildItem> handlers,
            Map<String, Map<String, Set<String>>> discoveries, String sourcePath) {
        if (outputs == null) {
            return;
        }
        for (ProcessorDefinition<?> output : outputs) {
            // Try each handler's extractor on this ProcessorDefinition
            for (CamelDslMethodHandlerBuildItem handler : handlers) {
                Map<String, Set<String>> extracted = handler.getXmlExtractor().apply(output);
                mergeDiscoveries(extracted, discoveries, sourcePath);
            }
            // Recurse into nested outputs
            collectFromOutputs(output.getOutputs(), handlers, discoveries, sourcePath);
        }
    }

    private static void mergeDiscoveries(Map<String, Set<String>> extracted,
            Map<String, Map<String, Set<String>>> discoveries, String sourcePath) {
        for (Map.Entry<String, Set<String>> entry : extracted.entrySet()) {
            String methodName = entry.getKey();
            for (String typeName : entry.getValue()) {
                discoveries.computeIfAbsent(methodName, k -> new HashMap<>())
                        .computeIfAbsent(sourcePath, k -> new HashSet<>())
                        .add(typeName);
            }
        }
    }
}
