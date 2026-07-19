package com.chatbi.copilot.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {

    @Test
    void remoteProviderRequiresApiKey() {
        LlmProperties props = new LlmProperties();
        props.setProvider("deepseek");
        props.setBaseUrl("https://api.deepseek.com/v1");
        props.setModel("deepseek-chat");
        props.setApiKey("");

        assertThat(props.isConfigured()).isFalse();
    }

    @Test
    void remoteProviderWithApiKeyIsConfigured() {
        LlmProperties props = new LlmProperties();
        props.setProvider("openai");
        props.setBaseUrl("https://api.openai.com/v1");
        props.setModel("gpt-4o-mini");
        props.setApiKey("sk-test");

        assertThat(props.isConfigured()).isTrue();
    }

    @Test
    void localOllamaCanRunWithoutApiKey() {
        LlmProperties props = new LlmProperties();
        props.setProvider("ollama");
        props.setBaseUrl("http://host.docker.internal:11434/v1");
        props.setModel("llama3.1");
        props.setApiKey("");

        assertThat(props.isConfigured()).isTrue();
    }

    @Test
    void mockProviderWorksWithoutApiKey() {
        LlmProperties props = new LlmProperties();
        props.setProvider("mock");
        props.setBaseUrl("");
        props.setModel("mock");
        props.setApiKey("");

        assertThat(props.isConfigured()).isTrue();
    }
}
