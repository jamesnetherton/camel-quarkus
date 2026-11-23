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
package org.apache.camel.quarkus.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.CommandLineArguments;
import jakarta.enterprise.util.AnnotationLiteral;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextEvents;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.TypeConversionException;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.clock.Clock;
import org.apache.camel.impl.DefaultModelReifierFactory;
import org.apache.camel.impl.engine.DefaultVariableRepositoryFactory;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.model.Model;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.quarkus.core.devmode.NoOpModelineFactory;
import org.apache.camel.quarkus.core.devmode.NoShutdownStrategy;
import org.apache.camel.quarkus.main.CamelMain;
import org.apache.camel.quarkus.main.CamelMainEventBridge;
import org.apache.camel.quarkus.main.CamelMainRoutesCollector;
import org.apache.camel.quarkus.main.CamelMainRunTimeConfig;
import org.apache.camel.quarkus.main.CamelMainRuntime;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.VariableRepositoryFactory;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResetableClock;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Assembles and starts the Camel runtime. Called from the services that the core deployment module registers.
 */
public final class CamelBootstrap {
    private static final Logger LOGGER = Logger.getLogger(CamelBootstrap.class);

    private CamelBootstrap() {
    }

    /**
     * A bean to bind to the {@link Registry}, instantiated with its no-arg constructor.
     */
    public record BeanBinding(String name, Class<?> type) {
    }

    /**
     * A route created from a {@link org.apache.camel.Consume} annotated method. Either {@code uri} or
     * {@code endpointMethodName} is set.
     */
    public record ConsumeRoute(String uri, String endpointMethodName, String beanName, String method) {
    }

    public static TypeConverterRegistry createTypeConverterRegistry(
            boolean statisticsEnabled,
            List<Class<?>> loaderClasses,
            Set<Class<?>> annotatedConverters,
            Optional<TypeConverterLoader> additionalLoaders) {

        TypeConverterRegistry registry = new FastTypeConverter(statisticsEnabled);
        for (Class<?> loaderClass : loaderClasses) {
            try {
                ((TypeConverterLoader) loaderClass.getConstructor().newInstance()).load(registry);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        StaticAnnotationTypeConverterLoader.getInstance().load(registry, annotatedConverters);
        additionalLoaders.ifPresent(loader -> loader.load(registry));
        return registry;
    }

    public static void bind(Registry registry, List<BeanBinding> beans) {
        for (BeanBinding bean : beans) {
            try {
                registry.bind(bean.name(), bean.type(), bean.type().getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static CamelContext createContext(
            Registry registry,
            TypeConverterRegistry typeConverterRegistry,
            Optional<ModelJAXBContextFactory> contextFactory,
            Optional<ModelToXMLDumper> xmlModelDumper,
            Optional<ModelToYAMLDumper> yamlModelDumper,
            Optional<CamelContextCustomizer> customizer,
            BeanContainer beanContainer,
            Map<String, String> factoryFinderEntries,
            List<String> componentNames,
            Set<Class<?>> packageScanClasses,
            boolean nativeImage,
            String version,
            CamelConfig config) {

        FastCamelContext context = new FastCamelContext(
                version,
                xmlModelDumper.orElseGet(DisabledModelToXMLDumper::new),
                yamlModelDumper.orElseGet(DisabledModelToYAMLDumper::new));

        context.getClock().add(ContextEvents.BOOT, createBootClock(nativeImage));

        FastFactoryFinderResolver.Builder factoryFinderResolver = new FastFactoryFinderResolver.Builder();
        factoryFinderEntries.forEach(factoryFinderResolver::entry);

        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        // Set ClassLoader first as some actions depend on it being available
        ExtendedCamelContext extendedCamelContext = context.getCamelContextExtension();
        context.setApplicationContextClassLoader(tccl);
        extendedCamelContext.addContextPlugin(FactoryFinderResolver.class, factoryFinderResolver.build());
        extendedCamelContext.addContextPlugin(RuntimeCamelCatalog.class, new CamelRuntimeCatalog(config.runtimeCatalog()));
        //variable repository factory depends on factoryFinder and classLoader, therefore has to be initialized afterwards
        extendedCamelContext.addContextPlugin(VariableRepositoryFactory.class, new DefaultVariableRepositoryFactory(context));
        extendedCamelContext.setRegistry(registry);

        context.setModelReifierFactory(new DefaultModelReifierFactory());

        typeConverterRegistry.setInjector(new FastTypeConverterInjector(context));
        context.setTypeConverterRegistry(typeConverterRegistry);
        context.setLoadTypeConverters(false);

        extendedCamelContext.addContextPlugin(
                ModelJAXBContextFactory.class,
                contextFactory.orElseGet(DisabledModelJAXBContextFactory::new));
        extendedCamelContext.addContextPlugin(
                PackageScanClassResolver.class,
                new CamelQuarkusPackageScanClassResolver(packageScanClasses));
        context.build();
        extendedCamelContext.addContextPlugin(
                ComponentNameResolver.class,
                new FastComponentNameResolver(new TreeSet<>(componentNames)));

        // register to the container
        beanContainer.beanInstance(CamelProducers.class).setContext(context);

        customizer.ifPresent(c -> c.configure(context));

        return context;
    }

    public static void registerEventBridges(
            CamelContext context,
            CamelMain main,
            Set<String> observedLifecycleEvents,
            Set<String> observedManagementEvents,
            Set<String> observedMainEvents) {

        if (!observedLifecycleEvents.isEmpty()) {
            context.addLifecycleStrategy(new CamelLifecycleEventBridge(observedLifecycleEvents));
        }

        if (!observedManagementEvents.isEmpty()) {
            context.getManagementStrategy().addEventNotifier(new CamelManagementEventBridge(observedManagementEvents));
        }

        if (!observedMainEvents.isEmpty()) {
            main.addMainListener(new CamelMainEventBridge(observedMainEvents));
        }
    }

    public static void postProcessBeansAndBindToRegistry(
            CamelContext camelContext,
            ArcContainer container,
            List<Class<?>> beanTypes) {

        CamelBeanPostProcessor beanPostProcessor = PluginHelper.getBeanPostProcessor(camelContext);
        for (Class<?> beanType : beanTypes) {
            try {
                Object bean;

                // To enable features like CDI injection to work with @BindToRegistry factory methods
                // try to find an existing bean for the @BindToRegistry host class and use it for postprocessing
                InjectableInstance<?> beanInstance = container.select(beanType);
                if (beanInstance.isResolvable()) {
                    bean = beanInstance.get();
                } else {
                    // No existing CDI bean so fallback to direct instantiation
                    bean = beanType.getDeclaredConstructor().newInstance();
                }

                beanPostProcessor.postProcessBeforeInitialization(bean, bean.getClass().getName());
                beanPostProcessor.postProcessAfterInitialization(bean, bean.getClass().getName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void addConsumeRoutes(CamelContext camelContext, List<ConsumeRoute> consumeRoutes) {
        final RoutesDefinition routes = new RoutesDefinition();
        for (ConsumeRoute route : consumeRoutes) {
            if (route.uri() != null) {
                routes.from(route.uri()).bean(route.beanName(), route.method());
            } else {
                Object uriOrEndpoint = getEndpointUri(camelContext, route.beanName(), route.endpointMethodName());
                if (uriOrEndpoint instanceof Endpoint endpoint) {
                    routes.from(endpoint).bean(route.beanName(), route.method());
                } else {
                    try {
                        final String uriOrRef = camelContext.getTypeConverter().mandatoryConvertTo(String.class,
                                uriOrEndpoint);
                        routes.from(uriOrRef).bean(route.beanName(), route.method());
                    } catch (TypeConversionException | NoTypeConversionAvailableException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        try {
            routes.setCamelContext(camelContext);
            camelContext.getCamelContextExtension().getContextPlugin(Model.class).addRouteDefinitions(routes.getRoutes());
        } catch (Exception e) {
            throw new RuntimeException("Could not add routes to context", e);
        }
    }

    public static CamelMain createCamelMain(
            CamelContext context,
            BeanContainer container,
            List<String> routesBuilderClassNames) {

        CamelMain main = new CamelMain(context);
        main.setRoutesCollector(new CamelMainRoutesCollector());

        // properties are loaded through MicroProfile Config so there's
        // no need to look for sources.
        main.setDefaultPropertyPlaceholderLocation("false");

        // register to the container
        container.beanInstance(CamelProducers.class).setMain(main);

        for (String className : routesBuilderClassNames) {
            try {
                Class<RoutesBuilder> type = context.getClassResolver().resolveClass(className, RoutesBuilder.class);
                RoutesBuilder builder = context.getInjector().newInstance(type, false);
                main.configure().addRoutesBuilder(builder);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return main;
    }

    public static CamelRuntime createRuntime(
            CamelMain main,
            BeanContainer beanContainer,
            CamelMainRunTimeConfig config,
            Optional<CamelContextCustomizer> customizer) {

        // Run the customizer before starting the context to give a last chance
        // to amend the Camel Context setup.
        //
        // Note that the customizer are actually materialized as main listeners
        // and bound to MainListener::afterConfigure so they are be executed as
        // last step in the Camel Context configuration process by Camel Main.
        customizer.ifPresent(c -> main.addMainListener(new MainListenerSupport() {
            @Override
            public void afterConfigure(BaseMainSupport main) {
                c.configure(main.getCamelContext());
            }
        }));

        main.setUnknownArgumentRemedy(config.arguments().onUnknown());
        final CamelRuntime runtime = new CamelMainRuntime(main, config.shutdown().timeout().toMillis());

        // register to the container
        beanContainer.beanInstance(CamelProducers.class).setRuntime(runtime);

        return runtime;
    }

    public static void start(
            CamelRuntime runtime,
            BeanContainer beanContainer,
            CamelRuntimeConfig config,
            String camelQuarkusVersion) {

        if (config.bootstrap().enabled()) {
            try {
                LOGGER.infof("Apache Camel Quarkus %s is starting", camelQuarkusVersion);
                runtime.start(beanContainer.beanInstance(String[].class, CommandLineArgumentsLiteral.INSTANCE));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void stop(CamelRuntime runtime) {
        try {
            runtime.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void customizeDevModeCamelContext(CamelContext camelContext, boolean isModeLineDslAbsent) {
        // If no graceful timeout is set in development mode, graceful shutdown is replaced with NoShutdownStrategy
        Optional<String> shutdownTimeout = ConfigProvider.getConfig().getOptionalValue("camel.main.shutdownTimeout",
                String.class);
        if (shutdownTimeout.isEmpty()) {
            camelContext.setShutdownStrategy(new NoShutdownStrategy());
        }

        if (isModeLineDslAbsent) {
            // Camel dev profile attempts to enable modeline support, so we need to provide a NoOp impl if the modeline extension is not present
            camelContext.getCamelContextExtension().addContextPlugin(ModelineFactory.class, new NoOpModelineFactory());
        }
    }

    public static void customizeDevModeCamelMain(CamelMain main) {
        Optional<String> profile = ConfigProvider.getConfig().getOptionalValue("camel.main.profile", String.class);
        main.getMainConfigurationProperties().setProfile(profile.orElse("dev"));
    }

    static Clock createBootClock(boolean nativeImage) {
        if (nativeImage) {
            // Avoid recording boot times since 'boot' is done during the native image build
            return new Clock() {
                @Override
                public long elapsed() {
                    return 0;
                }

                @Override
                public long getCreated() {
                    return 0;
                }
            };
        }
        return new ResetableClock();
    }

    static Object getEndpointUri(CamelContext camelContext, String beanName, String endpointMethodName) {
        /* Possible improvement: Instead of using reflection, we could generate this method at build time
         * to call the bean method directly */
        Object bean = camelContext.getRegistry().lookupByName(beanName);
        Method method = null;
        try {
            Class<?> cl = bean.getClass();
            do {
                method = Stream.of(cl.getDeclaredMethods())
                        .filter(m -> m.getName().equals(endpointMethodName) && m.getParameterCount() == 0)
                        .findFirst()
                        .orElse(null);
                cl = cl.getSuperclass();
            } while (method == null && cl != Object.class);
            return method.invoke(bean);
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("all")
    static final class CommandLineArgumentsLiteral extends AnnotationLiteral<CommandLineArguments>
            implements CommandLineArguments {
        static final CommandLineArgumentsLiteral INSTANCE = new CommandLineArgumentsLiteral();
    }
}
