package com.hackIAThon.solutionback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackIAThon.solutionback.dto.nvidia.FacturaData;
import com.hackIAThon.solutionback.dto.nvidia.NvidiaRequest;
import com.hackIAThon.solutionback.dto.nvidia.NvidiaRequest.NvidiaMessage;
import com.hackIAThon.solutionback.dto.nvidia.NvidiaResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class NvidiaExtractionService {

    private static final Logger log = LoggerFactory.getLogger(NvidiaExtractionService.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 5000;

    private static final String PROMPT_TEMPLATE = """
            Eres un extractor de datos de facturas y tickets de compra.
            Analiza el siguiente texto extraído de una factura y devuelve SOLO un JSON válido \
            sin texto adicional ni markdown, con este formato exacto:
            {
              "comercio": "nombre del comercio o empresa emisora",
              "fecha": "fecha de emisión en formato DD/MM/YYYY",
              "total": 0.00,
              "items": [
                {
                  "descripcion": "nombre del producto o servicio",
                  "cantidad": 1,
                  "precioUnitario": 0.00,
                  "subtotal": 0.00
                }
              ]
            }
            Reglas:
            - Si un campo no está disponible usa null.
            - El total y precios son números decimales sin símbolo de moneda.
            - Responde ÚNICAMENTE con el JSON, sin explicaciones.

            Texto de la factura:
            %s
            """;

    @Value("${nvidia.model}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NvidiaExtractionService(@Qualifier("nvidiaRestClient") RestClient restClient,
                                   ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public FacturaData extractFromPdf(MultipartFile file) throws IOException {
        String pdfText = extractText(file);
        log.info("Extracted {} chars from PDF: {}", pdfText.length(), file.getOriginalFilename());

        NvidiaRequest request = NvidiaRequest.builder()
                .model(model)
                .messages(List.of(
                        NvidiaMessage.builder().role("user").content(PROMPT_TEMPLATE.formatted(pdfText)).build()
                ))
                .maxTokens(1024)
                .temperature(0.1)
                .stream(false)
                .build();

        log.info("Sending to NVIDIA NIM: model={}, file={}", model, file.getOriginalFilename());
        NvidiaResponse response = callWithRetry(request, file.getOriginalFilename());

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new IllegalStateException("NVIDIA API returned empty response");
        }

        NvidiaResponse.Message msg = response.getChoices().get(0).getMessage();
        String rawContent = msg.getContent();
        if (rawContent == null || rawContent.isBlank()) {
            rawContent = msg.getReasoningContent();
        }

        log.debug("NVIDIA raw response: {}", rawContent);
        return parseJson(rawContent);
    }

    private NvidiaResponse callWithRetry(NvidiaRequest request, String filename) {
        long backoff = INITIAL_BACKOFF_MS;
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                NvidiaResponse response = restClient.post()
                        .uri("/chat/completions")
                        .body(request)
                        .retrieve()
                        .body(NvidiaResponse.class);
                log.info("NVIDIA response received for {}", filename);
                return response;
            } catch (HttpClientErrorException.TooManyRequests e) {
                lastException = e;
                if (attempt == MAX_RETRIES) {
                    log.error("NVIDIA rate limit: all {} retries exhausted for {}", MAX_RETRIES, filename);
                    throw e;
                }
                log.warn("NVIDIA rate limit on attempt {}/{} for {}. Waiting {}ms...", attempt, MAX_RETRIES, filename, backoff);
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during retry", ie);
                }
                backoff *= 2;
            } catch (HttpClientErrorException e) {
                log.error("NVIDIA API client error {}: {} for file {}", e.getStatusCode(), e.getResponseBodyAsString(), filename);
                throw new IllegalStateException("NVIDIA API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
            } catch (RestClientException e) {
                lastException = e;
                log.error("NVIDIA API connection error on attempt {}/{} for {}: {}", attempt, MAX_RETRIES, filename, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new IllegalStateException("NVIDIA API unreachable after " + MAX_RETRIES + " attempts: " + e.getMessage(), e);
                }
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during retry", ie);
                }
                backoff *= 2;
            }
        }
        throw new IllegalStateException("NVIDIA call failed after retries", lastException);
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(3, doc.getNumberOfPages()));
            return stripper.getText(doc);
        }
    }

    private FacturaData parseJson(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("NVIDIA returned blank content");
        }
        String cleaned = raw.trim();
        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}');
        if (start != -1 && end != -1) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return objectMapper.readValue(cleaned, FacturaData.class);
    }
}
