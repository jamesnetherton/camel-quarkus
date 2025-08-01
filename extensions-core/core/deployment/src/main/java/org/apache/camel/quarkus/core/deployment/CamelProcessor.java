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

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Overridable;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.runtime.RuntimeValue;
import io.smallrye.common.annotation.Identifier;
import org.apache.camel.impl.converter.BaseTypeConverterRegistry;
import org.apache.camel.quarkus.core.CamelConfig;
import org.apache.camel.quarkus.core.CamelConfigFlags;
import org.apache.camel.quarkus.core.CamelProducers;
import org.apache.camel.quarkus.core.CamelRecorder;
import org.apache.camel.quarkus.core.FastFactoryFinderResolver.Builder;
import org.apache.camel.quarkus.core.deployment.spi.CamelComponentNameResolverBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelFactoryFinderResolverBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelJAXBContextFactoryBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelReifierFactoryBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelToXMLDumperBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelModelToYAMLDumperBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelPackageScanClassBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelPackageScanClassResolverBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelRoutesBuilderClassBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceDestination;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceFilter;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceFilterBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServicePatternBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelTypeConverterLoaderBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelTypeConverterRegistryBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.ContainerBeansBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.RoutesBuilderClassExcludeBuildItem;
import org.apache.camel.quarkus.core.deployment.util.CamelSupport;
import org.apache.camel.quarkus.core.deployment.util.PathFilter;
import org.apache.camel.quarkus.core.util.FileUtils;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.quarkus.core.CamelCapabilities.CLOUD_EVENTS;

class CamelProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelProcessor.class);

    private static final DotName ROUTES_BUILDER_TYPE = DotName.createSimple(
            "org.apache.camel.RoutesBuilder");
    private static final DotName LAMBDA_ROUTE_BUILDER_TYPE = DotName.createSimple(
            "org.apache.camel.builder.LambdaRouteBuilder");
    private static final DotName LAMBDA_ENDPOINT_ROUTE_BUILDER_TYPE = DotName.createSimple(
            "org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder");
    private static final DotName ADVICE_WITH_ROUTE_BUILDER_TYPE = DotName.createSimple(
            "org.apache.camel.builder.AdviceWithRouteBuilder");
    private static final DotName DATA_FORMAT_TYPE = DotName.createSimple(
            "org.apache.camel.spi.DataFormat");
    private static final DotName LANGUAGE_TYPE = DotName.createSimple(
            "org.apache.camel.spi.Language");
    private static final DotName COMPONENT_TYPE = DotName.createSimple(
            "org.apache.camel.Component");
    private static final DotName PRODUCER_TYPE = DotName.createSimple(
            "org.apache.camel.Producer");
    private static final DotName PREDICATE_TYPE = DotName.createSimple(
            "org.apache.camel.Predicate");
    private static final DotName CONVERTER_TYPE = DotName.createSimple(
            "org.apache.camel.Converter");

    private static final Set<DotName> UNREMOVABLE_BEANS_TYPES = CamelSupport.setOf(
            ROUTES_BUILDER_TYPE,
            LAMBDA_ROUTE_BUILDER_TYPE,
            LAMBDA_ENDPOINT_ROUTE_BUILDER_TYPE,
            DATA_FORMAT_TYPE,
            LANGUAGE_TYPE,
            COMPONENT_TYPE,
            PRODUCER_TYPE,
            PREDICATE_TYPE);

    @BuildStep
    BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem containerBeans(
            BeanRegistrationPhaseBuildItem beanRegistrationPhase,
            BuildProducer<ContainerBeansBuildItem> containerBeans) {

        containerBeans.produce(
                new ContainerBeansBuildItem(beanRegistrationPhase.getContext().get(BuildExtension.Key.BEANS)));

        // method using BeanRegistrationPhaseBuildItem should return a BeanConfiguratorBuildItem
        // otherwise the build step may be processed at the wrong time.
        return new BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem();
    }

    /*
     * Configure filters for core services.
     */
    @BuildStep
    void coreServiceFilter(BuildProducer<CamelServiceFilterBuildItem> filterBuildItems) {
        filterBuildItems.produce(
                new CamelServiceFilterBuildItem(CamelServiceFilter.forService("properties-component-factory")));

        // The reactive executor is programmatically configured by an extension or
        // a default implementation is provided by this processor thus we can safely
        // prevent loading of this service.
        filterBuildItems.produce(
                new CamelServiceFilterBuildItem(CamelServiceFilter.forService("reactive-executor")));
    }

    @BuildStep
    void coreServicePatterns(BuildProducer<CamelServicePatternBuildItem> services) {
        services.produce(new CamelServicePatternBuildItem(
                CamelServiceDestination.REGISTRY,
                true,
                "META-INF/services/org/apache/camel/component/*",
                "META-INF/services/org/apache/camel/language/constant",
                "META-INF/services/org/apache/camel/language/file",
                "META-INF/services/org/apache/camel/language/header",
                "META-INF/services/org/apache/camel/language/ref",
                "META-INF/services/org/apache/camel/language/simple"));

        services.produce(new CamelServicePatternBuildItem(
                CamelServiceDestination.DISCOVERY,
                true,
                "META-INF/services/org/apache/camel/*",
                "META-INF/services/org/apache/camel/routes-loader/*",
                "META-INF/services/org/apache/camel/resource-resolver/*",
                "META-INF/services/org/apache/camel/invoke-on-header/*",
                "META-INF/services/org/apache/camel/management/*",
                "META-INF/services/org/apache/camel/model/*",
                "META-INF/services/org/apache/camel/configurer/*",
                "META-INF/services/org/apache/camel/language/*",
                "META-INF/services/org/apache/camel/dataformat/*",
                "META-INF/services/org/apache/camel/send-dynamic/*",
                "META-INF/services/org/apache/camel/urifactory/*",
                "META-INF/services/org/apache/camel/properties-function/*",
                "META-INF/services/org/apache/camel/health-check/*",
                "META-INF/services/org/apache/camel/periodic-task/*",
                "META-INF/services/org/apache/camel/transformer/*",
                "META-INF/services/org/apache/camel/tokenizer/*",
                "META-INF/services/org/apache/camel/simple-function-factory/*"));
    }

    @BuildStep
    void userServicePatterns(
            CamelConfig camelConfig,
            BuildProducer<CamelServicePatternBuildItem> services) {

        CamelConfig.ServiceDiscoveryConfig discovery = camelConfig.service().discovery();
        discovery.includePatterns().ifPresent(list -> services.produce(new CamelServicePatternBuildItem(
                CamelServiceDestination.DISCOVERY,
                true,
                list)));

        discovery.excludePatterns().ifPresent(list -> services.produce(new CamelServicePatternBuildItem(
                CamelServiceDestination.DISCOVERY,
                false,
                list)));

        CamelConfig.ServiceRegistryConfig registry = camelConfig.service().registry();
        registry.includePatterns().ifPresent(list -> services.produce(new CamelServicePatternBuildItem(
                CamelServiceDestination.REGISTRY,
                true,
                list)));

        registry.excludePatterns().ifPresent(list -> services.produce(new CamelServicePatternBuildItem(
                CamelServiceDestination.REGISTRY,
                false,
                list)));
    }

    @BuildStep
    CamelServicePatternBuildItem conditionalCloudEventsTransformerServiceExcludePattern(Capabilities capabilities) {
        // Exclude cloudevents transformers unless optional camel-quarkus-cloudevents is present
        if (capabilities.isMissing(CLOUD_EVENTS)) {
            return new CamelServicePatternBuildItem(
                    CamelServiceDestination.DISCOVERY,
                    false,
                    "META-INF/services/org/apache/camel/transformer/*cloudevents*");
        }
        return null;
    }

    @BuildStep
    void camelServices(
            ApplicationArchivesBuildItem applicationArchives,
            List<CamelServicePatternBuildItem> servicePatterns,
            BuildProducer<CamelServiceBuildItem> camelServices) {

        final PathFilter pathFilter = servicePatterns.stream()
                .filter(patterns -> patterns.getDestination() == CamelServiceDestination.DISCOVERY)
                .collect(
                        PathFilter.Builder::new,
                        (builder, patterns) -> builder.patterns(patterns.isInclude(), patterns.getPatterns()),
                        PathFilter.Builder::combine)
                .build();
        CamelSupport.services(applicationArchives, pathFilter)
                .forEach(camelServices::produce);

        if (LOGGER.isDebugEnabled()) {
            debugCamelServiceInclusion(applicationArchives, servicePatterns);
        }
    }

    /*
     * Discover {@link TypeConverterLoader}.
     */
    @SuppressWarnings("unchecked")
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelTypeConverterRegistryBuildItem typeConverterRegistry(
            CamelConfig config,
            CamelRecorder recorder,
            ApplicationArchivesBuildItem applicationArchives,
            List<CamelTypeConverterLoaderBuildItem> additionalLoaders,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<UnremovableBeanBuildItem> unremovableBean) {

        IndexView index = combinedIndex.getIndex();

        RuntimeValue<TypeConverterRegistry> typeConverterRegistry = recorder
                .createTypeConverterRegistry(config.typeConverter().statisticsEnabled());

        //
        // This should be simplified by searching for classes implementing TypeConverterLoader but that
        // would lead to have org.apache.camel.impl.converter.AnnotationTypeConverterLoader taken into
        // account even if it should not.
        //
        final ClassLoader TCCL = Thread.currentThread().getContextClassLoader();

        for (ApplicationArchive archive : applicationArchives.getAllApplicationArchives()) {
            for (Path root : archive.getRootDirectories()) {
                Path path = root.resolve(BaseTypeConverterRegistry.META_INF_SERVICES_TYPE_CONVERTER_LOADER);
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                try {
                    Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                            .map(String::trim)
                            .filter(l -> !l.isEmpty())
                            .filter(l -> !l.startsWith("#"))
                            .map(l -> (Class<? extends TypeConverterLoader>) CamelSupport.loadClass(l, TCCL))
                            .forEach(loader -> recorder.addTypeConverterLoader(typeConverterRegistry, loader));
                } catch (IOException e) {
                    throw new RuntimeException("Error discovering TypeConverterLoader", e);
                }
            }
        }

        Set<String> internalConverters = new HashSet<>();
        //ignore all @converters from org.apache.camel:camel-* dependencies
        for (ApplicationArchive archive : applicationArchives.getAllApplicationArchives()) {
            ArtifactKey artifactKey = archive.getKey();
            if (artifactKey != null && "org.apache.camel".equals(artifactKey.getGroupId())
                    && artifactKey.getArtifactId().startsWith("camel-")) {
                internalConverters.addAll(archive.getIndex().getAnnotations(CONVERTER_TYPE)
                        .stream().filter(a -> a.target().kind() == AnnotationTarget.Kind.CLASS)
                        .map(a -> a.target().asClass().name().toString())
                        .collect(Collectors.toSet()));
            }
        }

        Set<Class<?>> convertersClasses = index
                .getAnnotations(CONVERTER_TYPE)
                .stream().filter(a -> a.target().kind() == AnnotationTarget.Kind.CLASS &&
                        (a.value("generateBulkLoader") == null || !a.value("generateBulkLoader").asBoolean()) &&
                        (a.value("generateLoader") == null || !a.value("generateLoader").asBoolean()))
                .map(a -> a.target().asClass().name().toString())
                .filter(s -> !internalConverters.contains(s))
                .map(s -> CamelSupport.loadClass(s, TCCL))
                .collect(Collectors.toSet());

        recorder.loadAnnotatedConverters(typeConverterRegistry, convertersClasses);

        // Enable @Converter annotated classes to be CDI beans
        unremovableBean.produce(UnremovableBeanBuildItem.beanClassAnnotation(CONVERTER_TYPE));

        //
        // User can register loaders by providing a CamelTypeConverterLoaderBuildItem that can be used to
        // provide additional TypeConverter or override default converters discovered by the previous step.
        //
        for (CamelTypeConverterLoaderBuildItem item : additionalLoaders) {
            recorder.addTypeConverterLoader(typeConverterRegistry, item.getValue());
        }

        return new CamelTypeConverterRegistryBuildItem(typeConverterRegistry);
    }

    @Overridable
    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    public CamelModelJAXBContextFactoryBuildItem createJaxbContextFactory(CamelRecorder recorder) {
        return new CamelModelJAXBContextFactoryBuildItem(recorder.newDisabledModelJAXBContextFactory());
    }

    @Overridable
    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    public CamelModelToXMLDumperBuildItem createModelToXMLDumper(CamelRecorder recorder) {
        return new CamelModelToXMLDumperBuildItem(recorder.newDisabledModelToXMLDumper());
    }

    @Overridable
    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    public CamelModelToYAMLDumperBuildItem createModelToYAMLDumper(CamelRecorder recorder) {
        return new CamelModelToYAMLDumperBuildItem(recorder.newDisabledModelToYAMLDumper());
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelFactoryFinderResolverBuildItem factoryFinderResolver(
            CamelRecorder recorder,
            List<CamelServiceBuildItem> camelServices) {
        final ClassLoader TCCL = Thread.currentThread().getContextClassLoader();

        RuntimeValue<Builder> builder = recorder.factoryFinderResolverBuilder();

        camelServices.forEach(service -> {
            recorder.factoryFinderResolverEntry(
                    builder,
                    FileUtils.nixifyPath(service.path),
                    CamelSupport.loadClass(service.type, TCCL));
        });

        return new CamelFactoryFinderResolverBuildItem(recorder.factoryFinderResolver(builder));
    }

    @BuildStep
    void unremovableBeans(
            BuildProducer<AdditionalBeanBuildItem> beanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {

        beanProducer.produce(AdditionalBeanBuildItem.unremovableOf(CamelProducers.class));

        unremovableBeans.produce(UnremovableBeanBuildItem.targetWithAnnotation(DotName.createSimple(Identifier.class)));
        unremovableBeans.produce(new UnremovableBeanBuildItem(
                b -> b.getTypes().stream().map(Type::name).anyMatch(UNREMOVABLE_BEANS_TYPES::contains)));
    }

    @BuildStep(onlyIf = { CamelConfigFlags.RoutesDiscoveryEnabled.class })
    public List<CamelRoutesBuilderClassBuildItem> discoverRoutesBuilderClassNames(
            CombinedIndexBuildItem combinedIndex,
            CamelConfig camelConfig,
            List<RoutesBuilderClassExcludeBuildItem> routesBuilderClassExcludes) {

        final IndexView index = combinedIndex.getIndex();

        Set<ClassInfo> allKnownImplementors = index.getAllKnownImplementations(ROUTES_BUILDER_TYPE)
                .stream()
                .filter(classInfo -> !classInfo.superName().equals(ADVICE_WITH_ROUTE_BUILDER_TYPE))
                .collect(Collectors.toSet());

        final Predicate<DotName> pathFilter = new PathFilter.Builder()
                .exclude(
                        routesBuilderClassExcludes.stream()
                                .map(RoutesBuilderClassExcludeBuildItem::getPattern)
                                .collect(Collectors.toList()))
                .exclude(camelConfig.routesDiscovery().excludePatterns())
                .include(camelConfig.routesDiscovery().includePatterns())
                .build().asDotNamePredicate();

        return allKnownImplementors
                .stream()
                // public and non-abstract
                .filter(ci -> ((ci.flags() & (Modifier.ABSTRACT | Modifier.PUBLIC)) == Modifier.PUBLIC))
                .map(ClassInfo::name)
                .filter(pathFilter)
                .sorted()
                .map(CamelRoutesBuilderClassBuildItem::new)
                .collect(Collectors.toList());
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelComponentNameResolverBuildItem componentNameResolver(
            ApplicationArchivesBuildItem applicationArchives,
            CamelConfig camelConfig,
            CamelRecorder recorder) {

        PathFilter pathFilter = new PathFilter.Builder()
                .include("META-INF/services/org/apache/camel/component/*")
                .exclude(camelConfig.service().registry().excludePatterns())
                .build();

        Set<String> componentNames = CamelSupport.services(applicationArchives, pathFilter)
                .map(CamelServiceBuildItem::getName)
                .collect(Collectors.collectingAndThen(Collectors.toUnmodifiableSet(), TreeSet::new));

        return new CamelComponentNameResolverBuildItem(recorder.createComponentNameResolver(componentNames));
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelPackageScanClassResolverBuildItem packageScanClassResolver(
            List<CamelPackageScanClassBuildItem> camelPackageScanClassBuildItems,
            CamelRecorder recorder) {
        Set<? extends Class<?>> packageScanClassCache = camelPackageScanClassBuildItems.stream()
                .map(CamelPackageScanClassBuildItem::getClassNames)
                .flatMap(Set::stream)
                .map(className -> CamelSupport.loadClass(className, Thread.currentThread().getContextClassLoader()))
                .collect(Collectors.toUnmodifiableSet());

        return new CamelPackageScanClassResolverBuildItem(recorder.createPackageScanClassResolver(packageScanClassCache));

    }

    @BuildStep
    NativeImageResourceBuildItem initResources() {
        return new NativeImageResourceBuildItem(
                "META-INF/services/org/apache/camel/bean-processor-factory",
                "META-INF/services/org/apache/camel/rest-registry-factory");
    }

    @Overridable
    @BuildStep
    @Record(value = ExecutionTime.STATIC_INIT, optional = true)
    CamelModelReifierFactoryBuildItem modelReifierFactory(
            CamelRecorder recorder) {

        return new CamelModelReifierFactoryBuildItem(recorder.modelReifierFactory());
    }

    /**
     * Useful for identifying Camel services that are potentially not covered by inclusion patterns
     */
    private void debugCamelServiceInclusion(ApplicationArchivesBuildItem applicationArchives,
            List<CamelServicePatternBuildItem> servicePatterns) {
        PathFilter.Builder pathBuilder = new PathFilter.Builder();
        servicePatterns.forEach(camelServicePatternBuildItem -> {
            camelServicePatternBuildItem.getPatterns().forEach(pathBuilder::include);
        });

        PathFilter filter = pathBuilder.build();
        HashSet<String> missingServiceIncludes = new HashSet<>();

        for (ApplicationArchive archive : applicationArchives.getAllApplicationArchives()) {
            for (Path root : archive.getRootDirectories()) {
                final Path resourcePath = root.resolve("META-INF/services/org/apache/camel");

                if (!Files.isDirectory(resourcePath)) {
                    continue;
                }

                try (Stream<Path> files = Files.walk(resourcePath)) {
                    files.filter(Files::isRegularFile).forEach(file -> {
                        Path key = root.relativize(file);
                        if (!filter.asPathPredicate().test(key)) {
                            missingServiceIncludes.add(key.toString());
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (!missingServiceIncludes.isEmpty()) {
            // Note this is only partly reliable info as some include patterns are provided elsewhere independently of camel-quarkus-core
            LOGGER.debug("Found potential missing service include patterns for the following paths:");
            missingServiceIncludes.forEach(path -> {
                LOGGER.debug("Missing service include path: {}", path);
            });
        }
    }
}
