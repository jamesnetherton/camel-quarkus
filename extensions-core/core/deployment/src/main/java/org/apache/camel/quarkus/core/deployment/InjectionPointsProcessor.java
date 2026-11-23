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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.QualifierRegistrarBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem.ExtendedBeanConfigurator;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.arc.processor.QualifierRegistrar;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.quarkus.core.CamelCapabilities;
import org.apache.camel.quarkus.core.InjectionPointBeanCreators;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

public class InjectionPointsProcessor {
    private static final Logger LOGGER = Logger.getLogger(InjectionPointsProcessor.class);

    private static final DotName ANNOTATION_NAME_NAMED = DotName.createSimple(
            Named.class.getName());
    private static final DotName INTERFACE_NAME_COMPONENT = DotName.createSimple(
            Component.class.getName());
    private static final DotName ENDPOINT_INJECT_ANNOTATION = DotName
            .createSimple(EndpointInject.class.getName());
    private static final DotName PRODUCE_ANNOTATION = DotName
            .createSimple(Produce.class.getName());
    private static final DotName TEST_SUPPORT_CLASS_NAME = DotName
            .createSimple("org.apache.camel.quarkus.test.CamelQuarkusTestSupport");

    private static final Type CAMEL_CONTEXT_TYPE = ClassType.create(CamelContext.class);

    private static SyntheticBeanBuildItem componentBean(DotName type, String componentName) {
        return SyntheticBeanBuildItem.configure(type)
                .creator(InjectionPointBeanCreators.ComponentCreator.class)
                .addInjectionPoint(CAMEL_CONTEXT_TYPE)
                .param(InjectionPointBeanCreators.PARAM_NAME, componentName)
                .param(InjectionPointBeanCreators.PARAM_TYPE, type.toString())
                .scope(Singleton.class)
                .unremovable()
                .done();
    }

    private static SyntheticBeanBuildItem injectionPointBean(
            DotName type,
            Class<? extends BeanCreator<?>> creator,
            AnnotationInstance qualifier,
            String uri) {
        ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem.configure(type)
                .addInjectionPoint(CAMEL_CONTEXT_TYPE)
                .param(InjectionPointBeanCreators.PARAM_TYPE, type.toString())
                .scope(Singleton.class)
                .addQualifier(qualifier);
        if (uri != null) {
            configurator.param(InjectionPointBeanCreators.PARAM_URI, uri);
        }
        configurator.creator(creatorClass(creator));
        return configurator.done();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Class<? extends BeanCreator<Object>> creatorClass(Class<? extends BeanCreator<?>> creator) {
        return (Class) creator;
    }

    @BuildStep
    void injectedComponents(
            CombinedIndexBuildItem index,
            BeanRegistrationPhaseBuildItem beanRegistrationPhase,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem> beanConfigurator) {

        final Collection<ClassInfo> components = index.getIndex().getAllKnownImplementations(INTERFACE_NAME_COMPONENT);
        final Set<String> created = new HashSet<>();

        for (InjectionPointInfo injectionPoint : beanRegistrationPhase.getContext().get(BuildExtension.Key.INJECTION_POINTS)) {
            if (injectionPoint.getAnnotationTarget().kind() == AnnotationTarget.Kind.FIELD) {
                FieldInfo target = injectionPoint.getAnnotationTarget().asField();

                if (!created.add(target.type().name().toString())) {
                    continue;
                }

                if (components.stream().anyMatch(ci -> Objects.equals(ci.name(), target.type().name()))) {
                    final AnnotationInstance named = target.annotation(ANNOTATION_NAME_NAMED);
                    final String componentName = named == null ? target.name() : named.value().asString();

                    LOGGER.debugf("Creating synthetic component bean [name=%s, type=%s]", componentName, target.type().name());

                    syntheticBeans.produce(componentBean(target.type().name(), componentName));
                }
            }

            if (injectionPoint.getAnnotationTarget().kind() == AnnotationTarget.Kind.METHOD) {
                final MethodInfo target = injectionPoint.getAnnotationTarget().asMethod();
                final List<Type> types = target.parameterTypes();

                for (int i = 0; i < types.size(); i++) {
                    Type type = types.get(0);

                    if (!created.add(type.name().toString())) {
                        continue;
                    }

                    if (components.stream().anyMatch(ci -> Objects.equals(ci.name(), type.name()))) {
                        final AnnotationInstance named = target.annotation(ANNOTATION_NAME_NAMED);
                        final String componentName = named == null ? target.parameterName(i) : named.value().asString();

                        LOGGER.debugf("Creating synthetic component bean [name=%s, type=%s]", componentName, type.name());

                        syntheticBeans.produce(componentBean(type.name(), componentName));
                    }
                }
            }
        }

        // Methods using BeanRegistrationPhaseBuildItem should return/produce a BeanConfiguratorBuildItem
        // otherwise the build step may be processed at the wrong time.
        //
        // See BeanRegistrationPhaseBuildItem javadoc
        beanConfigurator.produce(new BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem());
    }

    @BuildStep
    void annotationsTransformers(
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformers) {

        annotationsTransformers.produce(new AnnotationsTransformerBuildItem(new AnnotationTransformation() {

            @Override
            public void apply(TransformationContext ctx) {
                final AnnotationTarget target = ctx.declaration();
                switch (target.kind()) {
                case FIELD: {
                    final FieldInfo fieldInfo = target.asField();
                    if (fieldInfo.annotation(ENDPOINT_INJECT_ANNOTATION) != null
                            || fieldInfo.annotation(PRODUCE_ANNOTATION) != null) {
                        ctx.add(Inject.class);
                    }
                    break;
                }
                case METHOD: {
                    final MethodInfo methodInfo = target.asMethod();
                    fail(methodInfo, ENDPOINT_INJECT_ANNOTATION);
                    fail(methodInfo, PRODUCE_ANNOTATION);
                    break;
                }
                default:
                    throw new IllegalStateException("Expected only field or method, got " + target.kind());
                }
            }

            @Override
            public boolean supports(org.jboss.jandex.AnnotationTarget.Kind kind) {
                return kind == Kind.FIELD || kind == Kind.METHOD;
            }
        }));

    }

    static void fail(final MethodInfo methodInfo, DotName annotType) {
        if (methodInfo.annotation(annotType) != null) {
            // See https://github.com/apache/camel-quarkus/issues/2579
            throw new IllegalStateException(
                    "@" + annotType + " is only supported on fields. Remove it from "
                            + methodInfo + " in " + methodInfo.declaringClass().name());
        }
    }

    @BuildStep
    void qualifierRegistrars(
            BuildProducer<QualifierRegistrarBuildItem> qualifierRegistrars) {
        qualifierRegistrars.produce(new QualifierRegistrarBuildItem(new QualifierRegistrar() {
            @Override
            public Map<DotName, Set<String>> getAdditionalQualifiers() {
                Map<DotName, Set<String>> result = new LinkedHashMap<>();
                result.put(ENDPOINT_INJECT_ANNOTATION, Collections.emptySet());
                result.put(PRODUCE_ANNOTATION, Collections.emptySet());
                return Collections.unmodifiableMap(result);
            }
        }));
    }

    @BuildStep
    void syntheticBeans(
            CombinedIndexBuildItem index,
            Capabilities capabilities,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyDefinitions) {

        Set<String> injectionPointIdentifiers = new HashSet<>();

        for (AnnotationInstance annotationInstance : index.getIndex().getAnnotations(ENDPOINT_INJECT_ANNOTATION)) {
            final AnnotationTarget target = annotationInstance.target();
            switch (target.kind()) {
            case FIELD: {
                // Avoid producing multiple beans for the same @EndpointInject URI
                String identifier = annotationIdentifier(annotationInstance);
                if (injectionPointIdentifiers.add(identifier)) {
                    endpointInjectBeans(syntheticBeans, index.getIndex(), annotationInstance, target.asField());
                }
                break;
            }
            case METHOD: {
                final MethodInfo methodInfo = target.asMethod();
                fail(methodInfo, ENDPOINT_INJECT_ANNOTATION);
                break;
            }
            default:
                throw new IllegalStateException("Expected field, got " + target.kind());
            }
        }

        for (AnnotationInstance annotation : index.getIndex().getAnnotations(PRODUCE_ANNOTATION)) {
            final AnnotationTarget target = annotation.target();
            String identifier = annotationIdentifier(annotation);

            switch (target.kind()) {
            case FIELD: {
                if (injectionPointIdentifiers.add(identifier)) {
                    produceBeans(capabilities, syntheticBeans, proxyDefinitions, index.getIndex(), annotation,
                            target.asField());
                }
                break;
            }
            case METHOD: {
                final MethodInfo methodInfo = target.asMethod();
                fail(methodInfo, PRODUCE_ANNOTATION);
                break;
            }
            default:
                throw new IllegalStateException("Expected field, got " + target.kind());
            }
        }
    }

    void produceBeans(
            Capabilities capabilities,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyDefinitions,
            IndexView index,
            AnnotationInstance annot,
            FieldInfo field) {
        try {
            Type fieldType = field.type();
            Class<?> clazz = Class.forName(fieldType.toString(), false,
                    Thread.currentThread().getContextClassLoader());
            if (ProducerTemplate.class.isAssignableFrom(clazz)) {
                syntheticBeans.produce(injectionPointBean(
                        fieldType.name(),
                        InjectionPointBeanCreators.ProducerTemplateCreator.class,
                        annot,
                        resolveAnnotValue(index, annot)));
                /*
                 * Note that ProducerTemplate injection points not having @EndpointInject are produced via
                 * CamelProducers.camelProducerTemplate()
                 */
            } else if (FluentProducerTemplate.class.isAssignableFrom(clazz)) {
                syntheticBeans.produce(injectionPointBean(
                        fieldType.name(),
                        InjectionPointBeanCreators.FluentProducerTemplateCreator.class,
                        annot,
                        resolveAnnotValue(index, annot)));
                /*
                 * Note that FluentProducerTemplate injection points not having @EndpointInject are produced via
                 * CamelProducers.camelFluentProducerTemplate()
                 */
            } else if (clazz.isInterface()) {
                /* Requires camel-quarkus-bean */
                if (capabilities.isMissing(CamelCapabilities.BEAN)) {
                    throw new IllegalStateException(
                            "Add camel-quarkus-bean dependency to be able to use @org.apache.camel.Produce on fields with interface type: "
                                    + fieldType.name()
                                    + " " + field.name() + " in "
                                    + field.declaringClass().name());
                }

                proxyDefinitions.produce(new NativeImageProxyDefinitionBuildItem(fieldType.name().toString()));
                syntheticBeans.produce(injectionPointBean(
                        fieldType.name(),
                        InjectionPointBeanCreators.ProxyCreator.class,
                        annot,
                        resolveAnnotValue(index, annot)));
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void endpointInjectBeans(
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            IndexView index,
            AnnotationInstance annot,
            FieldInfo field) {
        try {
            Type fieldType = field.type();
            Class<?> clazz = Class.forName(fieldType.toString());
            if (Endpoint.class.isAssignableFrom(clazz)) {
                syntheticBeans.produce(injectionPointBean(
                        fieldType.name(),
                        InjectionPointBeanCreators.EndpointCreator.class,
                        annot,
                        resolveAnnotValue(index, annot)));
            } else if (ProducerTemplate.class.isAssignableFrom(clazz)) {
                syntheticBeans.produce(injectionPointBean(
                        fieldType.name(),
                        InjectionPointBeanCreators.ProducerTemplateCreator.class,
                        annot,
                        resolveAnnotValue(index, annot)));
                /*
                 * Note that ProducerTemplate injection points not having @EndpointInject are produced via
                 * CamelProducers.camelProducerTemplate()
                 */
            } else if (FluentProducerTemplate.class.isAssignableFrom(clazz)) {
                syntheticBeans.produce(injectionPointBean(
                        fieldType.name(),
                        InjectionPointBeanCreators.FluentProducerTemplateCreator.class,
                        annot,
                        resolveAnnotValue(index, annot)));
                /*
                 * Note that FluentProducerTemplate injection points not having @EndpointInject are produced via
                 * CamelProducers.camelFluentProducerTemplate()
                 */
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private String resolveAnnotValue(IndexView index, AnnotationInstance annot) {
        return annot.valueWithDefault(index).asString();
    }

    private String annotationIdentifier(AnnotationInstance annotationInstance) {
        return annotationInstance.toString(false) + ":" + getTargetClass(annotationInstance);
    }

    private DotName getTargetClass(AnnotationInstance annotationInstance) {
        AnnotationTarget target = annotationInstance.target();
        switch (target.kind()) {
        case FIELD:
            return target.asField().type().name();
        case METHOD:
            return target.asMethod().returnType().name();
        default:
            return null;
        }
    }
}
