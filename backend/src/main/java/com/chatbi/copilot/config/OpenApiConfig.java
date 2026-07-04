package com.chatbi.copilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatbiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ChatBI Copilot API")
                .description("Natural language to SQL data analysis platform")
                .version("1.0.0")
                .license(new License().name("MIT License")));
    }
}
