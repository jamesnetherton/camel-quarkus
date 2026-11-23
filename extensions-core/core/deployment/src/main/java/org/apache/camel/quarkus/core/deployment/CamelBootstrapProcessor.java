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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.core.Phase;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.apache.camel.CamelContext;
import org.apache.camel.main.MainListener;
import org.apache.camel.quarkus.core.CamelBootstrap;
import org.apache.camel.quarkus.core.CamelConfig;
import org.apache.camel.quarkus.core.CamelRecorder;
import org.apache.camel.quarkus.core.CamelRuntime;
import org.apache.camel.quarkus.core.CamelRuntimeConfig;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextCustomizerBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelMainBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelMainListenerBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelJAXBContextFactoryBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelToXMLDumperBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelToYAMLDumperBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelPackageScanClassBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRegistryBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRoutesBuilderClassBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRuntimeTaskBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelTypeConverterRegistryBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.ContainerBeansBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.RuntimeCamelContextCustomizerBuildItem;
import org.apache.camel.quarkus.core.deployment.util.CamelQuarkusVersion;
import org.apache.camel.quarkus.core.deployment.util.CamelSupport;
import org.apache.camel.quarkus.core.deployment.util.PathFilter;
import org.apache.camel.quarkus.core.util.FileUtils;
import org.apache.camel.quarkus.main.CamelMain;
import org.apache.camel.quarkus.main.CamelMainApplication;
import org.apache.camel.quarkus.main.CamelMainRunTimeConfig;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.jboss.jandex.DotName;

import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.CONTEXT_CUSTOMIZERS;
import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.EVENT_BRIDGES;
import static org.apache.camel.quarkus.core.deployment.CamelServiceNames.RUNTIME_CONTEXT_CUSTOMIZERS;

/**
 * Build steps that assemble and start the Camel runtime, in the order they run: the {@link CamelContext}, the
 * {@link CamelMain} instance and finally the runtime that starts it.
 */
class CamelBootstrapProcessor {
    private static final String FEATURE = "camel-core";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Assembles the {@link CamelContext} service.
     *
     * @param  customizers a list of {@link CamelContextCustomizer} applied to the {@link CamelContext} once it is
     *                     created during {@link ExecutionTime#STATIC_INIT}.
     * @return             a build item holding the {@link CamelContext}
     */
    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    @BuildStep
    CamelContextBuildItem context(
            ServiceRegistrar serviceRegistrar,
            CamelRecorder recorder,
            BeanContainerBuildItem beanContainer,
            ApplicationArchivesBuildItem applicationArchives,
            CamelRegistryBuildItem registry,
            CamelTypeConverterRegistryBuildItem typeConverterRegistry,
            Optional<CamelModelJAXBContextFactoryBuildItem> modelJAXBContextFactory,
            Optional<CamelModelToXMLDumperBuildItem> xmlModelDumper,
            Optional<CamelModelToYAMLDumperBuildItem> yamlModelDumper,
            List<CamelContextCustomizerBuildItem> customizers,
            List<CamelServiceBuildItem> camelServices,
            List<CamelPackageScanClassBuildItem> packageScanClasses,
            NativeConfig nativeConfig,
            CamelConfig config) {

        modelJAXBContextFactory.ifPresent(item -> serviceRegistrar.aliasRecorderValue(
                ModelJAXBContextFactory.class,
                recorder.modelJAXBContextFactory(item.getContextFactory())));
        xmlModelDumper.ifPresent(item -> serviceRegistrar.aliasRecorderValue(
                ModelToXMLDumper.class,
                recorder.modelToXMLDumper(item.getValue())));
        yamlModelDumper.ifPresent(item -> serviceRegistrar.aliasRecorderValue(
                ModelToYAMLDumper.class,
                recorder.modelToYAMLDumper(item.getValue())));
        if (!customizers.isEmpty()) {
            serviceRegistrar.aliasRecorderValue(
                    CamelContextCustomizer.class,
                    CONTEXT_CUSTOMIZERS,
                    recorder.contextCustomizers(customizers.stream().map(CamelContextCustomizerBuildItem::get).toList()));
        }

        Map<String, String> factoryFinderEntries = factoryFinderEntries(camelServices);
        List<String> componentNames = componentNames(applicationArchives, config);
        Set<Class<?>> packageScanClassCache = packageScanClassCache(packageScanClasses);
        boolean nativeImage = nativeConfig.enabled();
        String version = CamelSupport.getCamelVersion();

        serviceRegistrar.forService(CamelContext.class)
                .atPhase(Phase.STATIC_INIT)
                .require(Registry.class)
                .require(TypeConverterRegistry.class)
                .request(ModelJAXBContextFactory.class)
                .request(ModelToXMLDumper.class)
                .request(ModelToYAMLDumper.class)
                .request(CamelContextCustomizer.class, CONTEXT_CUSTOMIZERS)
                .require(BeanContainer.class)
                .onStart((ctx, camelRegistry, camelTypeConverterRegistry, contextFactory, xmlDumper, yamlDumper,
                        contextCustomizer, container) -> CamelBootstrap.createContext(
                                camelRegistry,
                                camelTypeConverterRegistry,
                                contextFactory,
                                xmlDumper,
                                yamlDumper,
                                contextCustomizer,
                                container,
                                factoryFinderEntries,
                                componentNames,
                                packageScanClassCache,
                                nativeImage,
                                version,
                                config));

        return new CamelContextBuildItem(serviceRegistrar.staticInitServiceAsRuntimeValue(CamelContext.class));
    }

    /**
     * Assembles the camel-main service.
     *
     * @param  containerBeans       a list of bean known by the CDI container used to filter out auto-discovered routes from
     *                              those known by the CDI container.
     * @param  context              placeholder ensuring that this step runs after the {@link CamelContext} is assembled.
     * @param  routesBuilderClasses a list of known {@link org.apache.camel.RoutesBuilder} classes.
     * @return                      a build item holding a {@link CamelMain} instance.
     */
    @BuildStep
    CamelMainBuildItem main(
            ServiceRegistrar serviceRegistrar,
            ContainerBeansBuildItem containerBeans,
            CamelContextBuildItem context,
            List<CamelRoutesBuilderClassBuildItem> routesBuilderClasses) {

        // don't add routes builders that are known by the container
        List<String> routesBuilderClassNames = routesBuilderClasses.stream()
                .map(CamelRoutesBuilderClassBuildItem::getDotName)
                .filter(dotName -> !containerBeans.getClasses().contains(dotName))
                .map(DotName::toString)
                .toList();

        serviceRegistrar.forService(CamelMain.class)
                .atPhase(Phase.STATIC_INIT)
                .require(CamelContext.class)
                .require(BeanContainer.class)
                .onStart((ctx, camelContext, container) -> CamelBootstrap.createCamelMain(
                        camelContext,
                        container,
                        routesBuilderClassNames));

        return new CamelMainBuildItem(serviceRegistrar.staticInitServiceAsRuntimeValue(CamelMain.class));
    }

    /**
     * Adds the {@link MainListener}s from {@link CamelMainListenerBuildItem}s to camel-main.
     *
     * @param main      a build item holding the {@link CamelMain} instance.
     * @param listeners a list of known {@link MainListener} instances.
     */
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void mainListeners(CamelRecorder recorder, CamelMainBuildItem main, List<CamelMainListenerBuildItem> listeners) {
        for (CamelMainListenerBuildItem listener : listeners) {
            recorder.addMainListener(main.getInstance(), listener.getListener());
        }
    }

    /**
     * Registers Camel CDI event bridges if quarkus.camel.event-bridge.enabled=true and if
     * the relevant events have CDI observers configured for them.
     *
     * @param beanDiscovery build item containing the results of bean discovery
     * @param main          placeholder ensuring that this step runs after camel-main is assembled
     */
    @BuildStep(onlyIf = EventBridgeEnabled.class)
    void registerEventBridges(
            ServiceRegistrar serviceRegistrar,
            BeanDiscoveryFinishedBuildItem beanDiscovery,
            CamelMainBuildItem main) {

        Set<String> observedTypes = beanDiscovery.getObservers()
                .stream()
                .map(observerInfo -> observerInfo.getObservedType().name().toString())
                .collect(Collectors.toSet());

        Set<String> observedLifecycleEvents = observedTypes.stream()
                .filter(observedType -> observedType.startsWith("org.apache.camel.quarkus.core.events"))
                .collect(Collectors.toUnmodifiableSet());

        // For management events the event class simple name is collected as users can
        // observe events on either the Camel event interface or the concrete event class, and
        // these are located in different packages
        final Pattern pattern = Pattern.compile("org.apache.camel(?!.quarkus).*Event$");
        Set<String> observedManagementEvents = observedTypes.stream()
                .filter(className -> pattern.matcher(className).matches())
                .map(className -> CamelSupport.loadClass(className, Thread.currentThread().getContextClassLoader()))
                .map(Class::getSimpleName)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> observedMainEvents = observedTypes.stream()
                .filter(observedType -> observedType.startsWith("org.apache.camel.quarkus.main.events"))
                .collect(Collectors.toUnmodifiableSet());

        if (observedLifecycleEvents.isEmpty() && observedManagementEvents.isEmpty() && observedMainEvents.isEmpty()) {
            return;
        }

        serviceRegistrar.forService(EVENT_BRIDGES)
                .atPhase(Phase.STATIC_INIT)
                .require(CamelContext.class)
                .require(CamelMain.class)
                .onStart((ctx, camelContext, camelMain) -> CamelBootstrap.registerEventBridges(
                        camelContext,
                        camelMain,
                        observedLifecycleEvents,
                        observedManagementEvents,
                        observedMainEvents));
    }

    /**
     * Creates the camel-main based {@link CamelRuntime} and starts it, unless the application provides its own
     * {@link QuarkusMain}, in which case {@link CamelMainApplication} starts it.
     *
     * @param index        a reference to a {@link org.jboss.jandex.IndexView}
     * @param main         placeholder ensuring that this step runs after camel-main is assembled.
     * @param customizers  a list of {@link CamelContextCustomizer} that will be executed before starting the
     *                     {@link CamelContext} at {@link ExecutionTime#RUNTIME_INIT}.
     * @param runtimeTasks placeholder ensuring that all the runtime tasks run before the runtime starts.
     */
    @Record(value = ExecutionTime.RUNTIME_INIT, optional = true)
    @BuildStep
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    ServiceStartBuildItem boot(
            ServiceRegistrar serviceRegistrar,
            CamelRecorder recorder,
            CombinedIndexBuildItem index,
            CamelMainBuildItem main,
            List<RuntimeCamelContextCustomizerBuildItem> customizers,
            List<CamelRuntimeTaskBuildItem> runtimeTasks) {

        if (!customizers.isEmpty()) {
            serviceRegistrar.aliasRecorderValue(
                    CamelContextCustomizer.class,
                    RUNTIME_CONTEXT_CUSTOMIZERS,
                    recorder.contextCustomizers(
                            customizers.stream().map(RuntimeCamelContextCustomizerBuildItem::get).toList()));
        }

        boolean start = index.getIndex().getAnnotations(DotName.createSimple(QuarkusMain.class.getName())).isEmpty();
        String camelQuarkusVersion = CamelQuarkusVersion.getVersion();

        serviceRegistrar.forService(CamelRuntime.class)
                // camel-main may look up beans from the container, so it must be fully initialized
                .afterBuildItem(SyntheticBeansRuntimeInitBuildItem.class)
                .afterBuildItem(CamelRuntimeTaskBuildItem.class)
                .require(CamelMain.class)
                .require(BeanContainer.class)
                .require(CamelMainRunTimeConfig.class)
                .require(CamelRuntimeConfig.class)
                .request(CamelContextCustomizer.class, RUNTIME_CONTEXT_CUSTOMIZERS)
                .onStart((ctx, camelMain, beanContainer, mainConfig, runtimeConfig, contextCustomizer) -> {
                    CamelRuntime runtime = CamelBootstrap.createRuntime(
                            camelMain,
                            beanContainer,
                            mainConfig,
                            contextCustomizer);
                    ctx.onStop(() -> CamelBootstrap.stop(runtime));
                    if (start) {
                        CamelBootstrap.start(runtime, beanContainer, runtimeConfig, camelQuarkusVersion);
                    }
                    return runtime;
                });

        /* Make sure that Quarkus orders this method before starting to serve HTTP endpoints.
         * Otherwise first requests might reach Camel context in a non-yet-started state. */
        return new ServiceStartBuildItem("camel-runtime");
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem indexCamelMainApplication() {
        // Required for launching CamelMain based applications from the IDE
        return new AdditionalIndexedClassesBuildItem(CamelMainApplication.class.getName());
    }

    private static Map<String, String> factoryFinderEntries(List<CamelServiceBuildItem> camelServices) {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Map<String, String> entries = new HashMap<>();
        for (CamelServiceBuildItem service : camelServices) {
            // Fail the build on unresolvable service classes, as they are only loaded on demand at runtime
            CamelSupport.loadClass(service.type, tccl);
            entries.put(FileUtils.nixifyPath(service.path), service.type);
        }
        return Map.copyOf(entries);
    }

    private static List<String> componentNames(ApplicationArchivesBuildItem applicationArchives, CamelConfig config) {
        PathFilter pathFilter = new PathFilter.Builder()
                .include("META-INF/services/org/apache/camel/component/*")
                .exclude(config.service().registry().excludePatterns())
                .build();

        return CamelSupport.services(applicationArchives, pathFilter)
                .map(CamelServiceBuildItem::getName)
                .distinct()
                .sorted()
                .toList();
    }

    private static Set<Class<?>> packageScanClassCache(List<CamelPackageScanClassBuildItem> packageScanClasses) {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return packageScanClasses.stream()
                .map(CamelPackageScanClassBuildItem::getClassNames)
                .flatMap(Set::stream)
                .map(className -> CamelSupport.loadClass(className, tccl))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static final class EventBridgeEnabled implements BooleanSupplier {
        CamelConfig config;

        @Override
        public boolean getAsBoolean() {
            return config.eventBridge().enabled();
        }
    }
}
