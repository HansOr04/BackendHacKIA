package com.hackIAThon.solutionback.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class AuditConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public RestClientCustomizer nvidiaCompatibilityCustomizer() {
        ObjectMapper mapper = new ObjectMapper();
        return builder -> builder.requestInterceptor((request, originalBody, execution) -> {
            byte[] bodyToSend = originalBody;

            // Patch request: inject input_type and truncate required by Nvidia asymmetric embedding models
            if (request.getURI().getPath().endsWith("/embeddings")) {
                try {
                    ObjectNode node = (ObjectNode) mapper.readTree(originalBody);
                    if (!node.has("input_type")) {
                        node.put("input_type", "query");
                    }
                    if (!node.has("truncate")) {
                        node.put("truncate", "END");
                    }
                    bodyToSend = mapper.writeValueAsBytes(node);
                } catch (Exception ignored) {}
            }

            ClientHttpResponse response = execution.execute(request, bodyToSend);

            // Patch response: remove reasoning_content added by DeepSeek/Nvidia thinking models
            if (request.getURI().getPath().endsWith("/chat/completions")) {
                try {
                    byte[] raw = response.getBody().readAllBytes();
                    JsonNode root = mapper.readTree(new String(raw, StandardCharsets.UTF_8));
                    removeField(root, "reasoning_content");
                    removeField(root, "annotations");
                    byte[] patched = mapper.writeValueAsBytes(root);
                    ClientHttpResponse delegate = response;
                    return new ClientHttpResponse() {
                        @Override public InputStream getBody() { return new ByteArrayInputStream(patched); }
                        @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
                        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
                        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
                        @Override public void close() { delegate.close(); }
                    };
                } catch (Exception ignored) {}
            }

            return response;
        });
    }

    private static void removeField(JsonNode node, String fieldName) {
        if (node.isObject()) {
            ((ObjectNode) node).remove(fieldName);
            node.forEach(child -> removeField(child, fieldName));
        } else if (node.isArray()) {
            node.forEach(child -> removeField(child, fieldName));
        }
    }
}