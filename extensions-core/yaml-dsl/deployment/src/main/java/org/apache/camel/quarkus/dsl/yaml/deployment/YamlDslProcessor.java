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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslTypeReferenceBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslTypeReferenceBuildItem.Kind;
import org.apache.camel.quarkus.core.deployment.spi.CamelRouteResourceBuildItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public class YamlDslProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlDslProcessor.class);
    private static final String FEATURE = "camel-yaml-dsl";
    private static final Set<String> EXCEPTION_CONTAINER_KEYS = Set.of("onException", "on-exception", "doCatch", "do-catch");
    private static final Set<String> THROW_EXCEPTION_KEYS = Set.of("throwException", "throw-exception");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Parses YAML DSL route files to detect classes referenced by DSL elements such as onException and doCatch.
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void scanYamlDslTypeReferences(
            List<CamelRouteResourceBuildItem> camelRouteResources,
            BuildProducer<CamelDslTypeReferenceBuildItem> typeReferences) {

        final Set<CamelDslTypeReferenceBuildItem> references = new LinkedHashSet<>();

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
                    collectTypeReferences(document, references);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to parse YAML route resource for type reference detection: {}", sourcePath, e);
            }
        }

        if (!references.isEmpty()) {
            LOGGER.debug("Detected YAML DSL type references: {}", references);
            references.forEach(typeReferences::produce);
        }
    }

    /**
     * Recursively walks a parsed YAML structure looking for DSL mappings that reference classes.
     */
    private static void collectTypeReferences(Object node, Set<CamelDslTypeReferenceBuildItem> references) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> inner) {
                    if (EXCEPTION_CONTAINER_KEYS.contains(key)) {
                        addReferences(references, Kind.EXCEPTION, inner.get("exception"));
                    } else if (THROW_EXCEPTION_KEYS.contains(key)) {
                        addReferences(references, Kind.EXCEPTION, inner.get("exceptionType"));
                    }
                }
                collectTypeReferences(entry.getValue(), references);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectTypeReferences(item, references);
            }
        }
    }

    /**
     * Adds a class name, or each class name in a list, from a YAML mapping value.
     */
    private static void addReferences(Set<CamelDslTypeReferenceBuildItem> references, Kind kind, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                addReferences(references, kind, item);
            }
        } else if (value instanceof String className && !className.isBlank()) {
            references.add(new CamelDslTypeReferenceBuildItem(kind, className.trim()));
        }
    }
}
