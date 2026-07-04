package com.chatbi.copilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ChatBI Copilot - a natural-language-to-SQL data analysis platform.
 *
 * <p>Ask questions in plain language; the app builds schema-aware prompts, lets an LLM
 * generate read-only SQL, enforces a safety guard, executes the query and auto-recommends
 * an ECharts visualization.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.chatbi.copilot")
@MapperScan("com.chatbi.copilot.**.mapper")
public class ChatBiCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatBiCopilotApplication.class, args);
    }
}
