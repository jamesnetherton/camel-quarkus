package org.apache.camel.quarkus.component.langchain.chat.it;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static java.time.Duration.ofSeconds;

@ApplicationScoped
public class OllamaRoute extends RouteBuilder {

    private static final String OLLAMA_MODEL = "orca-mini";

    @ConfigProperty(name = "ollama.base.url")
    String ollamaBaseUrl;

    @Named
    ChatLanguageModel chatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(OLLAMA_MODEL)
                .temperature(0.3)
                .timeout(ofSeconds(3000))
                .build();
    }

    @Override
    public void configure() throws Exception {

        from("direct:send-simple-message")
                .to("langchain-chat:test1?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE")
                .to("mock:response");

        from("direct:send-message-prompt")
                .to("langchain-chat:test2?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE_WITH_PROMPT")
                .to("mock:response");

        from("direct:send-multiple")
                .to("langchain-chat:test2?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES")
                .to("mock:response");

    }
}
