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

package org.apache.camel.quarkus.dsl.yaml.deployment;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslDiscoveryContext;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslMethodHandlerBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRouteResourceBuildItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public class YamlDslProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlDslProcessor.class);
    private static final String FEATURE = "camel-yaml-dsl";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Registers a DSL method handler for exception-related DSL elements in YAML (onException, doCatch,
     * throwException). When these elements are discovered in YAML routes, the handler registers the exception
     * types for reflection.
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    CamelDslMethodHandlerBuildItem registerExceptionHandlerDslMethods() {
        return new CamelDslMethodHandlerBuildItem(
                ctx -> ctx.produce(ReflectiveClassBuildItem.builder(ctx.getTypeName()).build()),
                null, // No XML extractor needed - XML DSL has its own handler
                YamlDslProcessor::extractExceptionTypesFromYaml,
                "onException", "doCatch", "throwException");
    }

    /**
     * Extracts exception type references from YAML DSL maps.
     */
    private static Map<String, Set<String>> extractExceptionTypesFromYaml(String key, Map<?, ?> map) {
        Map<String, Set<String>> result = new HashMap<>();

        // Check for onException or doCatch (using both camelCase and kebab-case)
        if (key.equals("onException") || key.equals("on-exception")) {
            Set<String> exceptions = extractExceptionField(map);
            if (!exceptions.isEmpty()) {
                result.put("onException", exceptions);
            }
        } else if (key.equals("doCatch") || key.equals("do-catch")) {
            Set<String> exceptions = extractExceptionField(map);
            if (!exceptions.isEmpty()) {
                result.put("doCatch", exceptions);
            }
        } else if (key.equals("throwException") || key.equals("throw-exception")) {
            Object exType = map.get("exceptionType");
            if (exType instanceof String className) {
                result.put("throwException", Set.of(className));
            }
        }

        return result;
    }

    /**
     * Extracts exception class names from the "exception" field of a YAML mapping.
     */
    private static Set<String> extractExceptionField(Map<?, ?> mapping) {
        Set<String> result = new HashSet<>();
        Object exceptions = mapping.get("exception");
        if (exceptions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String className) {
                    result.add(className);
                }
            }
        } else if (exceptions instanceof String className) {
            result.add(className);
        }
        return result;
    }

    /**
     * Parses YAML DSL route files to detect type references in DSL elements registered by extensions
     * via CamelDslMethodHandlerBuildItem. Discovered types are passed to the registered handlers which can
     * produce BuildItems for native image configuration.
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void scanYamlDslMethods(
            List<CamelDslMethodHandlerBuildItem> handlers,
            List<CamelRouteResourceBuildItem> camelRouteResources,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<NativeImageResourceBuildItem> nativeResource) {

        if (handlers.isEmpty()) {
            return;
        }

        // Filter handlers that have YAML extractors
        List<CamelDslMethodHandlerBuildItem> yamlHandlers = handlers.stream()
                .filter(h -> h.getYamlExtractor() != null)
                .collect(java.util.stream.Collectors.toList());

        if (yamlHandlers.isEmpty()) {
            return;
        }

        // Scan YAML files using handler extractors
        Map<String, Map<String, Set<String>>> discoveries = new HashMap<>();

        for (CamelRouteResourceBuildItem routeResource : camelRouteResources) {
            String sourcePath = routeResource.getSourcePath();
            if (!sourcePath.endsWith(".yaml") && !sourcePath.endsWith(".yml")) {
                continue;
            }

            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
                if (is == null) {
                    LOGGER.debug("Could not read YAML route resource: {}", sourcePath);
                    continue;
                }

                LoadSettings settings = LoadSettings.builder().build();
                Load load = new Load(settings);
                for (Object document : load.loadAllFromInputStream(is)) {
                    collectDslMethodClasses(document, yamlHandlers, discoveries, sourcePath);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to parse YAML route resource for DSL method detection: {}", sourcePath, e);
            }
        }

        // Invoke handlers for each discovery
        for (CamelDslMethodHandlerBuildItem handler : yamlHandlers) {
            for (String methodName : handler.getMethodNames()) {
                Map<String, Set<String>> methodDiscoveries = discoveries.get(methodName);
                if (methodDiscoveries != null) {
                    for (Map.Entry<String, Set<String>> entry : methodDiscoveries.entrySet()) {
                        String sourceLocation = entry.getKey();
                        for (String typeName : entry.getValue()) {
                            CamelDslDiscoveryContext ctx = new CamelDslDiscoveryContext(
                                    methodName, typeName, sourceLocation, CamelDslDiscoveryContext.Source.YAML_DSL);
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

    /**
     * Recursively walks a parsed YAML structure looking for DSL method mappings and extracts
     * type references using registered extractors.
     */
    @SuppressWarnings("unchecked")
    private static void collectDslMethodClasses(Object node, List<CamelDslMethodHandlerBuildItem> handlers,
            Map<String, Map<String, Set<String>>> discoveries, String sourcePath) {
        if (node instanceof java.util.Map<?, ?> map) {
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());

                // Try each handler's extractor if value is a map
                if (entry.getValue() instanceof java.util.Map<?, ?> innerMap) {
                    for (CamelDslMethodHandlerBuildItem handler : handlers) {
                        Map<String, Set<String>> extracted = handler.getYamlExtractor().apply(key, innerMap);
                        mergeDiscoveries(extracted, discoveries, sourcePath);
                    }
                }

                // Continue walking the tree
                collectDslMethodClasses(entry.getValue(), handlers, discoveries, sourcePath);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectDslMethodClasses(item, handlers, discoveries, sourcePath);
            }
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
