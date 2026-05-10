package com.hackIAThon.solutionback.dto.nvidia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NvidiaRequest {
    private String model;
    private List<NvidiaMessage> messages;
    @JsonProperty("max_tokens")
    private int maxTokens;
    private double temperature;
    private boolean stream;

    @Data
    @Builder
    public static class NvidiaMessage {
        private String role;
        private Object content;
    }

    @Data
    @Builder
    public static class ContentPart {
        private String type;
        private String text;
        @JsonProperty("image_url")
        private ImageUrl imageUrl;
    }

    @Data
    @Builder
    public static class ImageUrl {
        private String url;
    }
}
