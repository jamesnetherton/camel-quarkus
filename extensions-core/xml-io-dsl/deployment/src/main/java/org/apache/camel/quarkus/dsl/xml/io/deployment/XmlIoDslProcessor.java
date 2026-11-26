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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
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
import org.apache.camel.quarkus.core.deployment.spi.CamelDslTypeReferenceBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslTypeReferenceBuildItem.Kind;
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
     * Parses XML DSL route files to detect classes referenced by DSL elements such as onException and doCatch.
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/7841">camel-quarkus#7841</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void scanXmlDslTypeReferences(
            List<CamelRouteResourceBuildItem> camelRouteResources,
            BuildProducer<CamelDslTypeReferenceBuildItem> typeReferences) {

        final Set<CamelDslTypeReferenceBuildItem> references = new LinkedHashSet<>();

        for (CamelRouteResourceBuildItem routeResource : camelRouteResources) {
            String sourcePath = routeResource.getSourcePath();
            if (!sourcePath.endsWith(".xml")) {
                continue;
            }

            // Try each XML format: <routes>, <routeConfiguration(s)>, <beans>/<camel>
            if (tryParseRoutes(sourcePath, references)) {
                continue;
            }
            if (tryParseRouteConfigurations(sourcePath, references)) {
                continue;
            }
            tryParseApplication(sourcePath, references);
        }

        if (!references.isEmpty()) {
            LOGGER.debug("Detected XML DSL type references: {}", references);
            references.forEach(typeReferences::produce);
        }
    }

    private boolean tryParseRoutes(String sourcePath, Set<CamelDslTypeReferenceBuildItem> references) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RoutesDefinition> routesOpt = parser.parseRoutesDefinition();
            if (routesOpt.isPresent()) {
                RoutesDefinition routes = routesOpt.get();
                collectOnExceptions(routes.getOnExceptions(), references);
                if (routes.getRoutes() != null) {
                    for (RouteDefinition route : routes.getRoutes()) {
                        collectFromOutputs(route.getOutputs(), references);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route resource: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseRouteConfigurations(String sourcePath, Set<CamelDslTypeReferenceBuildItem> references) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sourcePath)) {
            if (is == null) {
                return false;
            }
            ModelParser parser = new ModelParser(is);
            Optional<RouteConfigurationsDefinition> rcOpt = parser.parseRouteConfigurationsDefinition();
            if (rcOpt.isPresent()) {
                for (RouteConfigurationDefinition rc : rcOpt.get().getRouteConfigurations()) {
                    collectOnExceptions(rc.getOnExceptions(), references);
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML route configuration: {}", sourcePath, e);
        }
        return false;
    }

    private boolean tryParseApplication(String sourcePath, Set<CamelDslTypeReferenceBuildItem> references) {
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
                        collectFromOutputs(route.getOutputs(), references);
                    }
                }
                if (app.getRouteConfigurations() != null) {
                    for (RouteConfigurationDefinition rc : app.getRouteConfigurations()) {
                        collectOnExceptions(rc.getOnExceptions(), references);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse XML application definition: {}", sourcePath, e);
        }
        return false;
    }

    private static void collectOnExceptions(List<OnExceptionDefinition> onExceptions,
            Set<CamelDslTypeReferenceBuildItem> references) {
        if (onExceptions != null) {
            for (OnExceptionDefinition onEx : onExceptions) {
                addReferences(references, Kind.EXCEPTION, onEx.getExceptions());
            }
        }
    }

    private static void collectFromOutputs(List<ProcessorDefinition<?>> outputs,
            Set<CamelDslTypeReferenceBuildItem> references) {
        if (outputs == null) {
            return;
        }
        for (ProcessorDefinition<?> output : outputs) {
            if (output instanceof OnExceptionDefinition onEx) {
                addReferences(references, Kind.EXCEPTION, onEx.getExceptions());
            } else if (output instanceof CatchDefinition catchDef) {
                addReferences(references, Kind.EXCEPTION, catchDef.getExceptions());
            } else if (output instanceof ThrowExceptionDefinition throwEx) {
                addReference(references, Kind.EXCEPTION, throwEx.getExceptionType());
            }
            collectFromOutputs(output.getOutputs(), references);
        }
    }

    private static void addReferences(Set<CamelDslTypeReferenceBuildItem> references, Kind kind,
            Collection<String> classNames) {
        if (classNames != null) {
            for (String className : classNames) {
                addReference(references, kind, className);
            }
        }
    }

    private static void addReference(Set<CamelDslTypeReferenceBuildItem> references, Kind kind, String className) {
        if (className != null && !className.isBlank()) {
            references.add(new CamelDslTypeReferenceBuildItem(kind, className.trim()));
        }
    }
}
