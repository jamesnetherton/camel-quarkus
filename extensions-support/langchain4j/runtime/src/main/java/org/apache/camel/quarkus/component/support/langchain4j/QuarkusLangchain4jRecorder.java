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
package org.apache.camel.quarkus.component.support.langchain4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dev.langchain4j.guardrail.Guardrail;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.jboss.logging.Logger;

@Recorder
public class QuarkusLangchain4jRecorder {

    private static final Logger LOG = Logger.getLogger(QuarkusLangchain4jRecorder.class);

    public RuntimeValue<Guardrail<?, ?>> instantiateGuardrails(Class<Guardrail<?, ?>> guardrailClass) {
        try {
            return new RuntimeValue<>(guardrailClass.getConstructor().newInstance());
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
                | IllegalAccessException e) {
            Logger.getLogger(QuarkusLangchain4jRecorder.class).debugf(e,
                    "Can not instantiate guardrail of class %s", guardrailClass.getName());
            return null;
        }
    }

    /**
     * Create an AiServiceAgentAdapter that wraps a Quarkus AiService bean.
     *
     * @param  aiServiceBeanName the CDI bean name of the AiService
     * @param  chatMethodName    the name of the method to invoke
     * @param  parameterTypeName the fully qualified name of the parameter type
     * @return                   a RuntimeValue containing the AiServiceAgentAdapter
     */
    public RuntimeValue<Object> createAiServiceAdapter(
            String aiServiceBeanName,
            String chatMethodName,
            String parameterTypeName) {

        try {
            // Look up the AiService bean from the CDI container
            InstanceHandle<?> handle = Arc.container().instance(aiServiceBeanName);

            if (!handle.isAvailable()) {
                String errorMessage = String.format(
                        "Unable to resolve AiService bean '%s' for Camel Agent adapter. "
                                + "Possible causes:%n"
                                + "1. Bean '%s' not found in CDI container%n"
                                + "2. Bean is not a @RegisterAiService interface%n"
                                + "3. Quarkus LangChain4j extension not on classpath%n%n"
                                + "Suggestion: Verify that the AiService interface is annotated with "
                                + "@RegisterAiService and the bean name matches.",
                        aiServiceBeanName, aiServiceBeanName);
                throw new IllegalStateException(errorMessage);
            }

            Object aiServiceBean = handle.get();

            // Get the chat method via reflection
            Class<?> aiServiceClass = aiServiceBean.getClass();
            Class<?> parameterType = Class.forName(parameterTypeName);

            Method chatMethod = findMethodRecursively(aiServiceClass, chatMethodName, parameterType);

            if (chatMethod == null) {
                throw new NoSuchMethodException(String.format(
                        "Method '%s(%s)' not found in AiService bean '%s' (class: %s)",
                        chatMethodName,
                        parameterType.getSimpleName(),
                        aiServiceBeanName,
                        aiServiceClass.getName()));
            }

            // Create and return the adapter using reflection
            // (AiServiceAgentAdapter is in langchain4j-agent runtime module)
            // Use thread context classloader to avoid classloader mismatch in Quarkus
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> adapterClass = Class.forName(
                    "org.apache.camel.quarkus.component.langchain4j.agent.AiServiceAgentAdapter",
                    true,
                    classLoader);

            Object adapter = adapterClass
                    .getConstructor(Object.class, Method.class, String.class)
                    .newInstance(aiServiceBean, chatMethod, aiServiceBeanName);

            LOG.infof("Created AiService Agent adapter '%s$Agent' for bean '%s'",
                    aiServiceBeanName, aiServiceBeanName);

            return new RuntimeValue<>(adapter);

        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(String.format(
                    "Parameter type '%s' not found for AiService method '%s'",
                    parameterTypeName, chatMethodName), e);
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                    "Failed to create AiService Agent adapter for bean '%s': %s",
                    aiServiceBeanName, e.getMessage()), e);
        }
    }

    /**
     * Find a method in the class hierarchy, searching through interfaces and superclasses.
     */
    private Method findMethodRecursively(Class<?> clazz, String methodName, Class<?> parameterType) {
        // Try to find the method in the current class
        try {
            return clazz.getMethod(methodName, parameterType);
        } catch (NoSuchMethodException e) {
            // Method not found in this class, continue searching
        }

        // Search in declared methods (including private/protected)
        try {
            return clazz.getDeclaredMethod(methodName, parameterType);
        } catch (NoSuchMethodException e) {
            // Method not found in declared methods
        }

        // Search in interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                return iface.getMethod(methodName, parameterType);
            } catch (NoSuchMethodException e) {
                // Continue to next interface
            }
        }

        // Search in superclass
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return findMethodRecursively(superclass, methodName, parameterType);
        }

        return null;
    }
}
