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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import jakarta.decorator.Decorator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.quarkus.core.CamelBootstrap;
import org.apache.camel.quarkus.core.CamelBootstrap.ConsumeRoute;
import org.apache.camel.quarkus.core.CamelCapabilities;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeTaskBuildItem;
import org.apache.camel.util.StringHelper;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.CONSUME_ROUTES;

/**
 * Support for Camel {@link Consume} annotation.
 */
public class ConsumeProcessor {

    private static final DotName CONSUME_ANNOTATION = DotName.createSimple(Consume.class.getName());
    private static final DotName NAMED_ANNOTATION = DotName.createSimple(Named.class.getName());
    /**
     * Based on https://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#builtin_scopes
     * the list is not 100% complete, but hopefully it will suffice for our purposes
     */
    public static final Set<DotName> BEAN_DEFINING_ANNOTATIONS = Set.of(
            DotName.createSimple(ApplicationScoped.class.getName()),
            DotName.createSimple(SessionScoped.class.getName()),
            DotName.createSimple(ConversationScoped.class.getName()),
            DotName.createSimple(RequestScoped.class.getName()),
            DotName.createSimple(Interceptor.class.getName()),
            DotName.createSimple(Decorator.class.getName()),
            DotName.createSimple(Dependent.class.getName()),
            DotName.createSimple(Singleton.class.getName()));

    @BuildStep
    void annotationsTransformers(
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformers) {

        annotationsTransformers.produce(new AnnotationsTransformerBuildItem(new AnnotationTransformation() {
            @Override
            public boolean supports(org.jboss.jandex.AnnotationTarget.Kind kind) {
                return kind == Kind.CLASS;
            }

            @Override
            public void apply(TransformationContext ctx) {
                final ClassInfo classInfo = ctx.declaration().asClass();
                if (hasConsumeMethod(classInfo)) {
                    /* If there is @Consume on a method, make the declaring class a named injectable bean */
                    String beanName = namedValue(classInfo);
                    if (classInfo.annotationsMap().keySet().stream().noneMatch(BEAN_DEFINING_ANNOTATIONS::contains)) {
                        /* Only add @Singleton if there is no other bean defining annotation yet */
                        ctx.add(Singleton.class);
                    }

                    if (beanName == null) {
                        beanName = ConsumeProcessor.uniqueBeanName(classInfo);
                        AnnotationInstance namedAnnotation = AnnotationInstance.builder(Named.class)
                                .add("value", beanName)
                                .build();
                        ctx.add(namedAnnotation);
                    }
                }
            }
        }));

    }

    static String namedValue(ClassInfo classInfo) {
        String beanName = null;
        final AnnotationInstance named = classInfo.declaredAnnotation(NAMED_ANNOTATION);
        if (named != null) {
            if (named.value() != null) {
                beanName = named.value().asString();
            }
            if (beanName == null) {
                /* default bean name */
                beanName = ConsumeProcessor.firstLower(classInfo.simpleName());
            }
        }
        return beanName;
    }

    static boolean hasConsumeMethod(ClassInfo classInfo) {
        for (MethodInfo methodInfo : classInfo.methods()) {
            if (methodInfo.annotation(CONSUME_ANNOTATION) != null) {
                return true;
            }
        }
        return false;
    }

    @BuildStep
    void generateConsumeRoutes(
            ServiceRegistrar serviceRegistrar,
            CombinedIndexBuildItem index,
            List<CapabilityBuildItem> capabilities,
            // CamelContextBuildItem placeholder ensures this build step runs after the CamelContext is created
            CamelContextBuildItem camelContext,
            BuildProducer<CamelRuntimeTaskBuildItem> runtimeTasks) {

        final Collection<AnnotationInstance> consumeAnnotations = index.getIndex().getAnnotations(CONSUME_ANNOTATION);
        if (consumeAnnotations.isEmpty()) {
            return;
        }

        final boolean beanCapabilityAvailable = capabilities.stream()
                .map(CapabilityBuildItem::getName)
                .anyMatch(CamelCapabilities.BEAN::equals);

        final List<ConsumeRoute> consumeRoutes = new ArrayList<>();
        for (AnnotationInstance annot : consumeAnnotations) {
            final AnnotationTarget target = annot.target();
            switch (target.kind()) {
            case METHOD: {
                final MethodInfo methodInfo = target.asMethod();
                String uri = null;
                String endpointMethodName = null;
                final ClassInfo declaringClass = methodInfo.declaringClass();
                String beanName = namedValue(declaringClass);
                if (beanName == null) {
                    beanName = ConsumeProcessor.uniqueBeanName(declaringClass);
                }
                if (annot.value() != null) {
                    uri = annot.value().asString();
                } else if (annot.value("property") != null) {
                    endpointMethodName = findEndpointMethodName(annot.value("property").asString(), declaringClass,
                            new ArrayList<>());
                } else {
                    endpointMethodName = findEndpointMethodName(methodInfo.name(), declaringClass, new ArrayList<>());
                }
                if (uri == null && endpointMethodName == null) {
                    throw new IllegalStateException("@" + Consume.class.getName() + " on " + methodInfo + " in "
                            + declaringClass.name()
                            + " requires to specify a Camel Endpoint or endpoint URI through one of the following options:\n"
                            + " * via @Consume value, e.g. @Consume(\"direct:myDirect\")\n"
                            + " * via explicit @Consume property parameter, e.g. @Consume(property = \"myUriProperty\") and getMyUriProperty() or getMyUriPropertyEndpoint() in the same class must exist and return a String or org.apache.camel.Endpoint\n"
                            + " * via naming convention: if your @Consume method is called myEvent or onMyEvent then getMyEvent() or getMyEventEndpoint() must exist in the same class and return a String or org.apache.camel.Endpoint\n"
                            + "See https://camel.apache.org/manual/latest/pojo-consuming.html for more details");
                }
                if (!beanCapabilityAvailable) {
                    throw new IllegalStateException(
                            "Add camel-quarkus-bean dependency to be able to use @" + Consume.class.getName()
                                    + " on method:"
                                    + methodInfo
                                    + " in " + declaringClass.name());
                }
                consumeRoutes.add(new ConsumeRoute(uri, endpointMethodName, beanName, methodInfo.name()));
                break;
            }
            default:
                throw new IllegalStateException("Expected method, got " + target.kind() + ": " + target);
            }
        }

        List<ConsumeRoute> capturedConsumeRoutes = List.copyOf(consumeRoutes);
        serviceRegistrar.forService(CONSUME_ROUTES)
                .require(CamelContext.class)
                .onStart((ctx, context) -> CamelBootstrap.addConsumeRoutes(context, capturedConsumeRoutes));

        runtimeTasks.produce(new CamelRuntimeTaskBuildItem("consume-routes"));
    }

    private String findEndpointMethodName(final String propertyName, ClassInfo declaringClass, List<String> triedMethods) {
        /* Here we attempt to mimic what Camel does
         * in org.apache.camel.impl.engine.CamelPostProcessorHelper.doGetEndpointInjection(Object, String, String) */
        final String isGetter = "is" + StringHelper.capitalize(propertyName, false);
        final String getGetter = "get" + StringHelper.capitalize(propertyName, false);
        Optional<MethodInfo> method = Stream.of(isGetter, getGetter, isGetter + "Endpoint", getGetter + "Endpoint")
                .peek(triedMethods::add)
                .map(declaringClass::method)
                .filter(Objects::nonNull)
                .findFirst();
        if (method.isEmpty()) {
            if (propertyName.startsWith("on")) {
                // retry but without the on as prefix
                return findEndpointMethodName(propertyName.substring(2), declaringClass, triedMethods);
            }
            throw new IllegalStateException("Could not find any endpoint URI or Endpoint returning getter. Looked for " +
                    triedMethods.stream().map(m -> m + "()").collect(Collectors.joining(", ")) + " in "
                    + declaringClass.name().toString());
        }
        return method.get().name();
    }

    @BuildStep
    void unremovables(
            CombinedIndexBuildItem index,
            BuildProducer<UnremovableBeanBuildItem> unremovables,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        final Collection<AnnotationInstance> consumeAnnotations = index.getIndex().getAnnotations(CONSUME_ANNOTATION);
        if (!consumeAnnotations.isEmpty()) {
            final Set<DotName> declaringClasses = new LinkedHashSet<>();
            for (AnnotationInstance annot : consumeAnnotations) {
                final AnnotationTarget target = annot.target();
                switch (target.kind()) {
                case METHOD: {
                    final MethodInfo methodInfo = target.asMethod();
                    declaringClasses.add(methodInfo.declaringClass().name());
                    break;
                }
                default:
                    throw new IllegalStateException("Expected method, got " + target.kind() + ": " + target);
                }
            }
            unremovables.produce(UnremovableBeanBuildItem.beanTypes(declaringClasses));

            reflectiveClasses.produce(
                    ReflectiveClassBuildItem.builder(declaringClasses.stream()
                            .map(DotName::toString)
                            .toArray(String[]::new)).methods().build());
        }
    }

    static String uniqueBeanName(ClassInfo classInfo) {
        return firstLower(classInfo.simpleName()) + classInfo.name().hashCode();
    }

    static String firstLower(String str) {
        char[] c = str.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

}
