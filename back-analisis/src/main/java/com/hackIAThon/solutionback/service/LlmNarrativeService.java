package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.entity.Finding;
import com.hackIAThon.solutionback.entity.FindingType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class LlmNarrativeService {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.options.model:models/gemini-3-flash-preview}")
    private String model;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String generateNarrative(List<Finding> findings, int riskScore, String recommendation) {
        long discrepancies = findings.stream().filter(f -> f.getType() == FindingType.PRICE_EXCEEDED).count();
        long duplicates    = findings.stream().filter(f -> f.getType() == FindingType.DUPLICATE).count();
        long unjustified   = findings.stream().filter(f -> f.getType() == FindingType.UNJUSTIFIED).count();

        String fallback = buildFallback(riskScore, recommendation, discrepancies, duplicates, unjustified);

        try {
            String userMsg = String.format(
                    "Eres un auditor de seguros. Genera un resumen ejecutivo en español de máximo 3 oraciones "
                    + "para una auditoría de factura: Score de riesgo: %d/100. Recomendación: %s. "
                    + "Hallazgos: %d discrepancias de precio, %d ítems duplicados, %d ítems no justificados. "
                    + "Sé conciso y directo.",
                    riskScore, recommendation, discrepancies, duplicates, unjustified
            );

            String body = "{\"model\":\"" + model + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":"
                    + "\"" + escapeJson(userMsg) + "\"}],"
                    + "\"max_tokens\":200}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                return extractContent(resp.body(), fallback);
            }
            return fallback;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String buildFallback(long riskScore, String recommendation,
                                  long discrepancies, long duplicates, long unjustified) {
        String rec = "APPROVE".equals(recommendation) ? "APROBADA" : "ESCALADA";
        return String.format(
                "La factura fue auditada con un score de riesgo de %d/100 y recomendación: %s. "
                + "Se detectaron %d discrepancias de precio, %d ítems duplicados y %d ítems no justificados. "
                + "Se recomienda revisión detallada antes de proceder con el pago.",
                riskScore, rec, discrepancies, duplicates, unjustified
        );
    }

    private String extractContent(String json, String fallback) {
        try {
            int idx = json.indexOf("\"content\":");
            if (idx == -1) return fallback;
            int start = json.indexOf("\"", idx + 10) + 1;
            int end   = json.indexOf("\"", start);
            return json.substring(start, end).replace("\\n", " ").replace("\\\"", "\"");
        } catch (Exception e) {
            return fallback;
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
