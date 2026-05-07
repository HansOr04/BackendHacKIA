package com.hackIAThon.solutionback.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.context.annotation.Primary;

/**
 * Configuración principal de beans de la aplicación.
 *
 * - ChatClient: acceso al LLM (Ollama) vía Spring AI
 * - ObjectMapper: serialización de scoreBreakdown a JSON
 *
 * NO configura WebClient — toda comunicación con LLM y RAG es interna.
 */
@Configuration
public class AuditConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(OllamaEmbeddingModel ollamaEmbeddingModel) {
        return ollamaEmbeddingModel;
    }
}