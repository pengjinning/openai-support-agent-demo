package com.example.supportagent.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.supportagent.tools.SupportTools;

/**
 * Registers the {@code @Tool}-annotated {@link SupportTools} as a global
 * {@link ToolCallbackProvider} so the model's tool-calling manager can resolve
 * tool names during streaming.
 */
@Configuration
public class AiConfig {

    @Bean
    public ToolCallbackProvider supportToolCallbacks(SupportTools supportTools) {
        return MethodToolCallbackProvider.builder().toolObjects(supportTools).build();
    }
}
