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
package org.apache.camel.quarkus.component.support.langchain4j.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.SystemPropertyBuildItem;
import io.quarkus.runtime.RuntimeValue;
import jakarta.inject.Singleton;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.quarkus.component.support.langchain4j.QuarkusLangchain4jRecorder;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;

import static io.quarkus.arc.deployment.UnremovableBeanBuildItem.beanClassNames;

/**
 * Build steps required only when Quarkus LangChain4j is detected.
 */
@BuildSteps(onlyIf = QuarkusLangchain4jPresent.class)
class SupportQuarkusLangchain4jProcessor {

    public static final DotName REGISTER_AI_SERVICES_DOTNAME = DotName
            .createSimple("io.quarkiverse.langchain4j.RegisterAiService");

    private static final Logger LOG = Logger.getLogger(SupportQuarkusLangchain4jProcessor.class);

    @BuildStep
    SystemPropertyBuildItem enforceJaxRsHttpClient() {
        return new SystemPropertyBuildItem("langchain4j.http.clientBuilderFactory",
                "io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilderFactory");
    }

    @SuppressWarnings("unchecked")
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerLangChain4jAiServiceTypesForReflection(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            QuarkusLangchain4jRecorder recorder) {
        IndexView index = combinedIndex.getIndex();
        // Guardrails are instantiated dynamically
        Set<DotName> guardrailTypes = index.getAllKnownImplementations(InputGuardrail.class)
                .stream()
                .map(ClassInfo::name)
                .collect(Collectors.toSet());

        index.getAllKnownImplementations(OutputGuardrail.class)
                .stream()
                .map(ClassInfo::name)
                .forEach(guardrailTypes::add);

        guardrailTypes.stream()
                .filter(s -> !s.toString().equals("dev.langchain4j.guardrail.JsonExtractorOutputGuardrail"))
                .forEach(s -> {
                    try {
                        Class<Guardrail<?, ?>> guardrailClass;
                        guardrailClass = (Class<Guardrail<?, ?>>) Thread.currentThread()
                                .getContextClassLoader()
                                .loadClass(s.toString());
                        syntheticBeans
                                .produce(SyntheticBeanBuildItem.configure(s)
                                        .scope(Singleton.class)
                                        .named("GuardrailSynthetic" + s.local())
                                        .runtimeValue(recorder.instantiateGuardrails(guardrailClass))
                                        .done());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @BuildStep
    void markAiServicesAsUnremovable(
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        LOG.debug("Discovering classes annotated with @RegisterAiService to mark implementation beans as unremovable");

        for (AnnotationInstance instance : indexBuildItem.getIndex().getAnnotations(REGISTER_AI_SERVICES_DOTNAME)) {
            if (instance.target().kind() == AnnotationTarget.Kind.CLASS) {
                String declarativeAiServiceClassName = instance.target().asClass().name().toString();
                LOG.debugf("Marking Quarkus Ai service implementation class for %s as unremovable",
                        declarativeAiServiceClassName);
                unremovableBeans.produce(beanClassNames(declarativeAiServiceClassName + "$$QuarkusImpl"));
            }
        }
    }

    /**
     * Discover AiService beans and produce build items for them.
     * This step scans for @RegisterAiService annotations and creates metadata about each discovered bean.
     */
    @BuildStep
    void discoverAiServiceBeans(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<AiServiceBeanBuildItem> aiServiceBeans) {
        LOG.debug("Discovering @RegisterAiService interfaces for Camel Agent adapter creation");

        IndexView index = combinedIndex.getIndex();

        for (AnnotationInstance instance : index.getAnnotations(REGISTER_AI_SERVICES_DOTNAME)) {
            if (instance.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }

            ClassInfo aiServiceInterface = instance.target().asClass();
            String interfaceName = aiServiceInterface.name().toString();

            // Determine CDI bean name - check for @Named annotation first
            String beanName = determineBeanName(aiServiceInterface, index);

            // Find suitable chat method
            MethodInfo chatMethod = findSuitableChatMethod(aiServiceInterface);
            if (chatMethod == null) {
                LOG.warnf("AiService interface '%s' has no suitable chat method. "
                        + "Methods must accept a single String parameter. Skipping.", interfaceName);
                continue;
            }

            String methodName = chatMethod.name();
            String parameterType = chatMethod.parameterTypes().get(0).name().toString();

            AiServiceBeanInfo beanInfo = new AiServiceBeanInfo(
                    interfaceName,
                    beanName,
                    methodName,
                    parameterType);

            LOG.debugf("Discovered AiService bean: %s", beanInfo);

            aiServiceBeans.produce(new AiServiceBeanBuildItem(beanInfo));
        }
    }

    /**
     * Create synthetic CDI beans for AiService Agent adapters.
     * Combines RuntimeValue creation and synthetic bean registration in a single RUNTIME_INIT step.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createAiServiceAgentBeans(
            List<AiServiceBeanBuildItem> aiServiceBeans,
            QuarkusLangchain4jRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        LOG.debugf("Creating and registering %d AiService Agent adapters", aiServiceBeans.size());

        for (AiServiceBeanBuildItem buildItem : aiServiceBeans) {
            AiServiceBeanInfo beanInfo = buildItem.getBeanInfo();

            LOG.debugf("Creating synthetic Agent bean '%s' for AiService '%s'",
                    beanInfo.getAdapterBeanName(), beanInfo.getBeanName());

            // Create the RuntimeValue for the adapter
            RuntimeValue<Object> runtimeValue = recorder.createAiServiceAdapter(
                    beanInfo.getBeanName(),
                    beanInfo.getChatMethodName(),
                    beanInfo.getParameterTypeName());

            // Register as a synthetic CDI bean immediately
            syntheticBeans.produce(SyntheticBeanBuildItem
                    .configure(Agent.class)
                    .scope(Singleton.class)
                    .named(beanInfo.getAdapterBeanName())
                    .runtimeValue(runtimeValue)
                    .setRuntimeInit()
                    .done());
        }
    }

    /**
     * Determine the CDI bean name for an AiService interface.
     * First checks for @Named annotation, then falls back to default naming convention.
     */
    private String determineBeanName(ClassInfo aiServiceInterface, IndexView index) {
        DotName namedDotName = DotName.createSimple("jakarta.inject.Named");

        // Check if @Named annotation is present
        AnnotationInstance namedAnnotation = aiServiceInterface.declaredAnnotation(namedDotName);
        if (namedAnnotation != null) {
            AnnotationValue value = namedAnnotation.value();
            if (value != null && !value.asString().isEmpty()) {
                return value.asString();
            }
        }

        // Fall back to default naming: interface simple name with lowercase first letter
        String simpleName = aiServiceInterface.simpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * Find a suitable chat method in the AiService interface.
     * Looks for methods accepting a single String parameter.
     */
    private MethodInfo findSuitableChatMethod(ClassInfo aiServiceInterface) {
        List<MethodInfo> candidates = new ArrayList<>();

        for (MethodInfo method : aiServiceInterface.methods()) {
            // Skip static methods, default methods, and synthetic methods
            if (method.isSynthetic()) {
                continue;
            }

            // Check if method has exactly one parameter
            if (method.parametersCount() != 1) {
                continue;
            }

            // Check if parameter is String
            String paramType = method.parameterTypes().get(0).name().toString();
            if ("java.lang.String".equals(paramType)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Priority 1: Method annotated with @Handler (Camel convention)
        DotName handlerDotName = DotName.createSimple("org.apache.camel.Handler");
        for (MethodInfo method : candidates) {
            if (method.hasAnnotation(handlerDotName)) {
                return method;
            }
        }

        // Priority 2: Method annotated with @UserMessage (LangChain4j convention)
        DotName userMessageDotName = DotName.createSimple("dev.langchain4j.service.UserMessage");
        for (MethodInfo method : candidates) {
            if (method.hasAnnotation(userMessageDotName)) {
                return method;
            }
        }

        // Priority 3: If only one candidate, use it
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Priority 4: Method named "chat"
        for (MethodInfo method : candidates) {
            if ("chat".equals(method.name())) {
                return method;
            }
        }

        // If multiple ambiguous methods, warn and return null
        LOG.warnf("AiService interface '%s' has multiple candidate methods. "
                + "Please annotate the intended method with @Handler or @UserMessage.",
                aiServiceInterface.name());
        return null;
    }
}
