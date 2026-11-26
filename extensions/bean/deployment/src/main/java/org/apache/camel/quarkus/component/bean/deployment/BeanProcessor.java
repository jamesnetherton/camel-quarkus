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

import java.util.List;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.apache.camel.Handler;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslTypeReferenceBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelDslTypeReferenceBuildItem.Kind;
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
     * Registers bean classes referenced by the bean EIP in Java, XML and YAML routes for reflection, so that Camel can
     * load and introspect them in native mode.
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void registerDslBeanClassesForReflection(
            List<CamelDslTypeReferenceBuildItem> typeReferences,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {

        String[] beanClasses = typeReferences.stream()
                .filter(reference -> reference.getKind() == Kind.BEAN)
                .map(CamelDslTypeReferenceBuildItem::getClassName)
                .distinct()
                .filter(BeanProcessor::isClassPresent)
                .toArray(String[]::new);

        if (beanClasses.length > 0) {
            LOGGER.debug("Registering DSL bean classes for reflection: {}", (Object) beanClasses);
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(beanClasses).methods().build());
        }
    }

    /**
     * Names that do not resolve to a class at build time, such as property placeholders, are skipped.
     */
    private static boolean isClassPresent(String className) {
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return true;
        }
        LOGGER.debug("Skipping unresolvable DSL bean class: {}", className);
        return false;
    }
}
