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
package org.apache.camel.quarkus.component.langchain4j.agent.it;

import java.util.function.Supplier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Test AiService bean that uses a mock ChatModel.
 * This bean should be discoverable via @RegisterAiService and wrapped as a Camel Agent.
 */
@ApplicationScoped
@Named("testAssistant")
@RegisterAiService(chatLanguageModelSupplier = TestAssistant.TestChatModelSupplier.class)
public interface TestAssistant {

    /**
     * Simple chat method that accepts a question and returns a response.
     */
    @UserMessage("Answer this question: {question}")
    String chat(String question);

    /**
     * Mock ChatModel supplier that returns a test response.
     */
    class TestChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    // Return a fixed test response
                    // In a real scenario, this would call an actual AI model
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage("TestAssistant has been resolved and processed the request"))
                            .build();
                }
            };
        }
    }
}
