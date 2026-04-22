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
package org.apache.camel.quarkus.component.bean.deployment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.Handler;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.quarkus.core.CamelCapabilities;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslMethodHandlerBuildItem;
import org.apache.camel.support.language.DefaultAnnotationExpressionFactory;
import org.apache.camel.support.language.LanguageAnnotation;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BeanProcessor {

    private static final String FEATURE = "camel-bean";
    private static final Logger LOGGER = LoggerFactory.getLogger(BeanProcessor.class);
    private static final DotName LANGUAGE_ANNOTATION = DotName.createSimple(LanguageAnnotation.class.getName());
    private static final DotName HANDLER_ANNOTATION = DotName.createSimple(Handler.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerReflectiveClasses(CombinedIndexBuildItem index, BuildProducer<ReflectiveClassBuildItem> producer) {
        IndexView view = index.getIndex();
        for (AnnotationInstance languageAnnotationInstance : view.getAnnotations(LANGUAGE_ANNOTATION)) {
            ClassInfo languageClassInfo = languageAnnotationInstance.target().asClass();
            LOGGER.debug("Found language @interface {} annotated with @LanguageAnnotation", languageClassInfo.name());
            if (!view.getAnnotations(languageClassInfo.name()).isEmpty()) {
                LOGGER.debug("Registered {} as reflective class", languageClassInfo.name());
                producer.produce(ReflectiveClassBuildItem.builder(languageClassInfo.name().toString()).methods()
                        .build());

                AnnotationValue languageAnnotationExpressionFactory = languageAnnotationInstance.value("factory");
                if (languageAnnotationExpressionFactory == null) {
                    LOGGER.debug("Registered {} as reflective class", DefaultAnnotationExpressionFactory.class.getName());
                    producer.produce(ReflectiveClassBuildItem.builder(DefaultAnnotationExpressionFactory.class)
                            .build());
                } else {
                    LOGGER.debug("Registered {} as reflective class", languageAnnotationExpressionFactory.asString());
                    producer.produce(
                            ReflectiveClassBuildItem.builder(languageAnnotationExpressionFactory.asString())
                                    .build());
                }
            }
        }
    }

    @BuildStep
    void registerBeanHandlersForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            CombinedIndexBuildItem combinedIndex) {
        IndexView index = combinedIndex.getIndex();
        index.getAnnotations(HANDLER_ANNOTATION).forEach(annotationInstance -> {
            DotName className = annotationInstance.target().asMethod().declaringClass().name();
            ReflectiveClassBuildItem reflectiveClassBuildItem = ReflectiveClassBuildItem.builder(className.toString())
                    .methods()
                    .build();

            reflectiveClass.produce(reflectiveClassBuildItem);

        });
    }

    /**
     * Auto-detects bean classes referenced in .bean() DSL calls (Java, XML, YAML) and registers them
     * for reflection. This eliminates the need for manual @RegisterForReflection annotations on bean classes.
     * <p>
     * This handler is consumed by:
     * <ul>
     * <li>Java DSL scanner - detects {@code .bean(MyClass.class)} calls in RouteBuilders</li>
     * <li>XML DSL scanner - detects {@code <bean>} elements (if camel-xml-io-dsl is present)</li>
     * <li>YAML DSL scanner - detects bean references (if camel-yaml-dsl is present)</li>
     * </ul>
     *
     * @see <a href="https://github.com/apache/camel-quarkus/issues/2171">camel-quarkus#2171</a>
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void registerBeanDslMethodHandlers(
            Capabilities capabilities,
            BuildProducer<CamelDslMethodHandlerBuildItem> dslHandlers) {

        dslHandlers.produce(new CamelDslMethodHandlerBuildItem(
                ctx -> {
                    String className = ctx.getTypeName();
                    LOGGER.debug("Auto-detected bean class from {} DSL: {}",
                            ctx.getSource(), className);
                    ctx.produce(ReflectiveClassBuildItem.builder(className).methods().build());
                },
                capabilities.isPresent(CamelCapabilities.XML_IO_DSL) ? BeanProcessor::extractBeanTypesFromXml : null,
                capabilities.isPresent(CamelCapabilities.YAML_DSL) ? BeanProcessor::extractBeanTypesFromYaml : null,
                "bean"));
    }

    /**
     * Extracts bean type references from XML DSL ProcessorDefinitions.
     *
     * @param processorDef a ProcessorDefinition from the XML model
     * @return map of method name to discovered type names (empty if not a bean element)
     */
    private static Map<String, Set<String>> extractBeanTypesFromXml(Object processorDef) {
        if (!(processorDef instanceof BeanDefinition beanDef)) {
            return Map.of();
        }

        Set<String> types = new HashSet<>();

        // Extract beanType attribute: <bean beanType="com.example.MyClass"/>
        String beanType = beanDef.getBeanType();
        if (beanType != null && !beanType.isEmpty()) {
            types.add(beanType);
        }

        // Extract ref attribute if it looks like a class name (contains dots)
        // Example: <bean ref="com.example.MyClass"/>
        String ref = beanDef.getRef();
        if (ref != null && ref.contains(".")) {
            types.add(ref);
        }

        return types.isEmpty() ? Map.of() : Map.of("bean", types);
    }

    /**
     * Extracts bean type references from YAML DSL maps.
     *
     * @param key the YAML key (e.g., "bean")
     * @param map the YAML map value
     * @return map of method name to discovered type names (empty if not a bean element)
     */
    private static Map<String, Set<String>> extractBeanTypesFromYaml(String key, Map<?, ?> map) {
        if (!"bean".equals(key)) {
            return Map.of();
        }

        Set<String> types = new HashSet<>();

        // Extract beanType field
        Object beanType = map.get("beanType");
        if (beanType instanceof String className && !className.isEmpty()) {
            types.add(className);
        }

        // Extract ref field if it looks like a class name (contains dots)
        Object ref = map.get("ref");
        if (ref instanceof String refStr && refStr.contains(".")) {
            types.add(refStr);
        }

        return types.isEmpty() ? Map.of() : Map.of("bean", types);
    }
}
