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
import java.util.List;
import java.util.Objects;

import io.quarkus.arc.ArcContainer;
import io.quarkus.core.Phase;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.runtime.RuntimeValue;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.quarkus.core.CamelBeanQualifierResolver;
import org.apache.camel.quarkus.core.CamelBootstrap;
import org.apache.camel.quarkus.core.CamelBootstrap.BeanBinding;
import org.apache.camel.quarkus.core.CamelCapabilities;
import org.apache.camel.quarkus.core.CamelRecorder;
import org.apache.camel.quarkus.core.deployment.spi.CamelBeanBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelBeanInfo;
import org.apache.camel.quarkus.core.deployment.spi.CamelBeanQualifierResolverBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRegistryBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRoutesBuilderClassBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeBeanBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeTaskBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceDestination;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceFilterBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServicePatternBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.ContainerBeansBuildItem;
import org.apache.camel.quarkus.core.deployment.util.CamelSupport;
import org.apache.camel.quarkus.core.deployment.util.PathFilter;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.BIND_BEANS;
import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.BIND_RUNTIME_BEANS;
import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.BIND_TO_REGISTRY;

public class CamelRegistryProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelRegistryProcessor.class);

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelRegistryBuildItem registry(
            ServiceRegistrar serviceRegistrar,
            CamelRecorder recorder,
            List<CamelBeanQualifierResolverBuildItem> camelBeanQualifierResolvers) {

        // The registry is created by the recorder, as it needs the CamelBeanQualifierResolver recorder values
        List<String> beanTypeNames = new ArrayList<>();
        List<String> beanNames = new ArrayList<>();
        List<RuntimeValue<CamelBeanQualifierResolver>> resolvers = new ArrayList<>();
        for (CamelBeanQualifierResolverBuildItem resolver : camelBeanQualifierResolvers) {
            beanTypeNames.add(resolver.getBeanTypeName());
            beanNames.add(resolver.getBeanName());
            resolvers.add(resolver.getRuntimeValue());
        }

        RuntimeValue<Registry> registry = recorder.createRegistry(beanTypeNames, beanNames, resolvers);
        serviceRegistrar.aliasRecorderValue(Registry.class, recorder.registry(registry));

        return new CamelRegistryBuildItem(registry);
    }

    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    @BuildStep
    public void bindBeansToRegistry(
            ServiceRegistrar serviceRegistrar,
            CamelRecorder recorder,
            ApplicationArchivesBuildItem applicationArchives,
            ContainerBeansBuildItem containerBeans,
            CamelRegistryBuildItem registry,
            // CamelContextBuildItem placeholder ensures this build step runs after the CamelContext is created
            CamelContextBuildItem camelContextBuildItem,
            List<CamelBeanBuildItem> registryItems,
            List<CamelServiceFilterBuildItem> serviceFilters,
            List<CamelServicePatternBuildItem> servicePatterns) {

        final ClassLoader TCCL = Thread.currentThread().getContextClassLoader();
        final List<BeanBinding> beans = new ArrayList<>();

        final PathFilter pathFilter = servicePatterns.stream()
                .filter(patterns -> patterns.getDestination() == CamelServiceDestination.REGISTRY)
                .collect(
                        PathFilter.Builder::new,
                        (builder, patterns) -> builder.patterns(patterns.isInclude(), patterns.getPatterns()),
                        PathFilter.Builder::combine)
                .build();

        CamelSupport.services(applicationArchives, pathFilter)
                .filter(si -> !containerBeans.getBeans().contains(si))
                .filter(si -> {
                    //
                    // by default all the service found in META-INF/service/org/apache/camel are
                    // bound to the registry but some of the services are then replaced or set
                    // to the camel context directly by extension so it does not make sense to
                    // instantiate them in this phase.
                    //
                    boolean blacklisted = serviceFilters.stream().anyMatch(filter -> filter.getPredicate().test(si));
                    if (blacklisted) {
                        LOGGER.debug("Ignore service: {}", si);
                    }

                    return !blacklisted;
                })
                .forEach(si -> {
                    LOGGER.debug("Binding bean with name: {}, type {}", si.name, si.type);
                    beans.add(new BeanBinding(si.name, CamelSupport.loadClass(si.type, TCCL)));
                });

        registryItems.stream()
                .filter(item -> !containerBeans.getBeans().contains(item))
                .forEach(item -> {
                    LOGGER.debug("Binding bean with name: {}, type {}", item.getName(), item.getType());
                    if (item.getValue().isPresent()) {
                        recorder.bind(
                                registry.getRegistry(),
                                item.getName(),
                                CamelSupport.loadClass(item.getType(), TCCL),
                                item.getValue().get());
                    } else {
                        beans.add(new BeanBinding(item.getName(), CamelSupport.loadClass(item.getType(), TCCL)));
                    }
                });

        bindBeans(serviceRegistrar, Phase.STATIC_INIT, BIND_BEANS, beans);
    }

    @Record(value = ExecutionTime.RUNTIME_INIT, optional = true)
    @BuildStep
    CamelRuntimeTaskBuildItem bindRuntimeBeansToRegistry(
            ServiceRegistrar serviceRegistrar,
            CamelRecorder recorder,
            ContainerBeansBuildItem containerBeans,
            CamelRegistryBuildItem registry,
            // CamelContextBuildItem placeholder ensures this build step runs after the CamelContext is created
            CamelContextBuildItem camelContextBuildItem,
            List<CamelRuntimeBeanBuildItem> registryItems) {

        final ClassLoader TCCL = Thread.currentThread().getContextClassLoader();
        final List<BeanBinding> beans = new ArrayList<>();

        registryItems.stream()
                .filter(item -> !containerBeans.getBeans().contains(item))
                .forEach(item -> {
                    LOGGER.debug("Binding runtime bean with name: {}, type {}", item.getName(), item.getType());

                    if (item.getValue().isPresent()) {
                        recorder.bind(
                                registry.getRegistry(),
                                item.getName(),
                                CamelSupport.loadClass(item.getType(), TCCL),
                                item.getValue().get());
                    } else {
                        beans.add(new BeanBinding(item.getName(), CamelSupport.loadClass(item.getType(), TCCL)));
                    }
                });

        bindBeans(serviceRegistrar, Phase.APPLICATION, BIND_RUNTIME_BEANS, beans);

        return new CamelRuntimeTaskBuildItem("registry");
    }

    @BuildStep
    void processBindToRegistryAnnotations(
            ServiceRegistrar serviceRegistrar,
            Capabilities capabilities,
            CombinedIndexBuildItem combinedIndex,
            ContainerBeansBuildItem containerBeans,
            List<CamelRoutesBuilderClassBuildItem> camelRoutes,
            // CamelContextBuildItem placeholder ensures this build step runs after the CamelContext is created
            CamelContextBuildItem camelContextBuildItem) {

        // java-joor-dsl has its own discovery mechanism for @BindToRegistry annotations
        if (capabilities.isPresent(CamelCapabilities.JAVA_JOOR_DSL)) {
            return;
        }

        // Process all classes containing occurrences of BindToRegistry, excluding existing known beans
        List<Class<?>> beanTypes = combinedIndex.getIndex()
                .getAnnotations(DotName.createSimple(BindToRegistry.class))
                .stream()
                .map(BindToRegistryBeanInfo::new)
                .filter(BindToRegistryBeanInfo::isValid)
                // Filter out @BindToRegistry usage on RouteBuilder impls as Camel can already handle this internally
                .filter(bindToRegistryBeanInfo -> camelRoutes.stream()
                        .noneMatch(routeBuilder -> routeBuilder.getDotName()
                                .equals(bindToRegistryBeanInfo.getDeclaringType().name())))
                // Filter any existing named beans compared to the @BindToRegistry bean name
                .filter(bindToRegistryBeanInfo -> containerBeans.getBeans()
                        .stream()
                        .map(CamelBeanInfo::getName)
                        .filter(Objects::nonNull)
                        .noneMatch(camelBeanName -> camelBeanName.equals(bindToRegistryBeanInfo.getBeanName())))
                .map(BindToRegistryBeanInfo::getDeclaringTypeNameAsString)
                .distinct()
                .<Class<?>> map(beanClassName -> CamelSupport.loadClass(beanClassName,
                        Thread.currentThread().getContextClassLoader()))
                .toList();

        if (beanTypes.isEmpty()) {
            return;
        }

        serviceRegistrar.forService(BIND_TO_REGISTRY)
                .atPhase(Phase.STATIC_INIT)
                .require(CamelContext.class)
                .require(ArcContainer.class)
                .onStart((ctx, camelContext, container) -> CamelBootstrap.postProcessBeansAndBindToRegistry(
                        camelContext,
                        container,
                        beanTypes));
    }

    private static void bindBeans(ServiceRegistrar serviceRegistrar, Phase phase, String name, List<BeanBinding> beans) {
        if (beans.isEmpty()) {
            return;
        }

        List<BeanBinding> capturedBeans = List.copyOf(beans);
        serviceRegistrar.forService(name)
                .atPhase(phase)
                .require(Registry.class)
                .after(CamelContext.class)
                .onStart((ctx, camelRegistry) -> CamelBootstrap.bind(camelRegistry, capturedBeans));
    }

    static class BindToRegistryBeanInfo {
        private String beanName;
        private ClassInfo declaringType;

        BindToRegistryBeanInfo(AnnotationInstance instance) {
            AnnotationTarget target = instance.target();
            AnnotationValue value = instance.value();
            if (value != null) {
                this.beanName = value.asString();
            } else {
                this.beanName = "";
            }

            if (target.kind().equals(AnnotationTarget.Kind.CLASS)) {
                this.declaringType = target.asClass();
                if (ObjectHelper.isEmpty(beanName)) {
                    this.beanName = this.declaringType.simpleName();
                }
            }

            if (target.kind().equals(AnnotationTarget.Kind.FIELD)) {
                FieldInfo field = target.asField();
                this.declaringType = field.declaringClass();
                if (ObjectHelper.isEmpty(this.beanName)) {
                    this.beanName = field.declaringClass().simpleName();
                }
            }

            if (target.kind().equals(AnnotationTarget.Kind.METHOD)) {
                MethodInfo method = target.asMethod();
                this.declaringType = method.declaringClass();
                if (ObjectHelper.isEmpty(this.beanName)) {
                    this.beanName = method.declaringClass().simpleName();
                }
            }
        }

        String getBeanName() {
            return this.beanName;
        }

        ClassInfo getDeclaringType() {
            return this.declaringType;
        }

        String getDeclaringTypeNameAsString() {
            return this.declaringType.name().toString();
        }

        boolean isValid() {
            return this.declaringType != null;
        }
    }
}
