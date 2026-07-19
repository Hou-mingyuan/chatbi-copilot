package com.chatbi.copilot.config;

import com.chatbi.copilot.llm.LlmClient;
import com.chatbi.copilot.llm.MockLlmChatClient;
import com.chatbi.copilot.llm.OpenAiChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientConfig {

    @Bean
    public LlmClient llmClient(LlmProperties props) {
        if (isMockProvider(props)) {
            return new MockLlmChatClient();
        }
        return new OpenAiChatClient(props);
    }

    static boolean isMockProvider(LlmProperties props) {
        return props != null
                && props.getProvider() != null
                && "mock".equalsIgnoreCase(props.getProvider().trim());
    }
}
