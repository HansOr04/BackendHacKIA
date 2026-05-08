package com.hackIAThon.solutionback.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class GeminiCompatibilityInterceptor implements ClientHttpRequestInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        String path = request.getURI().getPath();
        if (path.endsWith("/embeddings")) {
            return new PatchedResponse(response, GeminiCompatibilityInterceptor::patchEmbeddings);
        }
        if (path.endsWith("/chat/completions")) {
            return new PatchedResponse(response, GeminiCompatibilityInterceptor::patchChat);
        }
        return response;
    }

    private static String patchEmbeddings(String json) {
        if (!json.contains("\"usage\"")) {
            int lastBrace = json.lastIndexOf('}');
            return json.substring(0, lastBrace) + ",\"usage\":{\"prompt_tokens\":0,\"total_tokens\":0}}";
        }
        return json;
    }

    private static String patchChat(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            removeField(root, "extra_content");
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return json;
        }
    }

    private static void removeField(JsonNode node, String fieldName) {
        if (node.isObject()) {
            ((ObjectNode) node).remove(fieldName);
            node.forEach(child -> removeField(child, fieldName));
        } else if (node.isArray()) {
            node.forEach(child -> removeField(child, fieldName));
        }
    }

    @FunctionalInterface
    private interface Patcher { String patch(String json); }

    private static class PatchedResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] patchedBody;

        PatchedResponse(ClientHttpResponse delegate, Patcher patcher) throws IOException {
            this.delegate = delegate;
            byte[] raw = delegate.getBody().readAllBytes();
            String json = new String(raw, StandardCharsets.UTF_8).trim();
            this.patchedBody = patcher.patch(json).getBytes(StandardCharsets.UTF_8);
        }

        @Override public InputStream getBody() { return new ByteArrayInputStream(patchedBody); }
        @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public void close() { delegate.close(); }
    }
}
