package com.hackIAThon.solutionback.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClientCustomizer nvidiaEmbeddingCustomizer() {
        ObjectMapper mapper = new ObjectMapper();
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            if (request.getURI().getPath().endsWith("/embeddings")) {
                try {
                    ObjectNode node = (ObjectNode) mapper.readTree(body);
                    if (!node.has("input_type")) {
                        node.put("input_type", "query");
                    }
                    return execution.execute(request, mapper.writeValueAsBytes(node));
                } catch (Exception ignored) {}
            }
            return execution.execute(request, body);
        });
    }
}