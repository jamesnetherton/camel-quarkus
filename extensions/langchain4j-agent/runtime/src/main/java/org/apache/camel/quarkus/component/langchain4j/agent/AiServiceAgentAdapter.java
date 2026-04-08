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
package org.apache.camel.quarkus.component.langchain4j.agent;

import java.lang.reflect.Method;

import dev.langchain4j.service.tool.ToolProvider;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.jboss.logging.Logger;

/**
 * Adapter that wraps a Quarkus LangChain4j AiService bean and exposes it as a Camel Agent.
 * <p>
 * This allows AiService beans created with {@code @RegisterAiService} to be used directly
 * in langchain4j-agent endpoints via the {@code agent=#beanName$Agent} parameter.
 */
public class AiServiceAgentAdapter implements Agent {

    private static final Logger LOG = Logger.getLogger(AiServiceAgentAdapter.class);

    private final Object aiServiceBean;
    private final Method chatMethod;
    private final String aiServiceBeanName;

    public AiServiceAgentAdapter(Object aiServiceBean, Method chatMethod, String aiServiceBeanName) {
        this.aiServiceBean = aiServiceBean;
        this.chatMethod = chatMethod;
        this.aiServiceBeanName = aiServiceBeanName;

        // Ensure method is accessible (especially for package-private or private methods)
        this.chatMethod.setAccessible(true);

        LOG.debugf("Created AiServiceAgentAdapter for bean '%s' using method '%s'",
                aiServiceBeanName, chatMethod.getName());
    }

    @Override
    public String chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
        try {
            // Extract user message from the AiAgentBody
            String userMessage = aiAgentBody.getUserMessage();

            LOG.debugf("Processing message with AiService bean '%s': %s", aiServiceBeanName, userMessage);

            // Invoke the AiService method with the user message
            Object result = chatMethod.invoke(aiServiceBean, userMessage);

            if (result == null) {
                LOG.warnf("AiService bean '%s' returned null response", aiServiceBeanName);
                return "";
            }

            String response = result.toString();
            LOG.debugf("AiService bean '%s' returned result: %s", aiServiceBeanName, response);

            return response;

        } catch (Exception e) {
            LOG.errorf(e, "Error processing message with AiService bean '%s'", aiServiceBeanName);

            // Provide helpful error message
            String errorMessage;
            if (e.getCause() != null) {
                errorMessage = String.format(
                        "Error invoking AiService bean '%s' method '%s': %s",
                        aiServiceBeanName,
                        chatMethod.getName(),
                        e.getCause().getMessage());
                throw new RuntimeException(errorMessage, e.getCause());
            } else {
                errorMessage = String.format(
                        "Error processing message with AiService bean '%s': %s",
                        aiServiceBeanName,
                        e.getMessage());
                throw new RuntimeException(errorMessage, e);
            }
        }
    }

    public String getAiServiceBeanName() {
        return aiServiceBeanName;
    }

    public String getChatMethodName() {
        return chatMethod.getName();
    }
}
