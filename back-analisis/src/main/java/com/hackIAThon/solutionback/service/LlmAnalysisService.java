package com.hackIAThon.solutionback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlmAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmAnalysisService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public JustificationAnalysis analyzeJustification(String lineDescription, String claimReport) {
        try {
            String prompt = """
            Eres un analista de siniestros. Compara un concepto facturado con el reporte de siniestro.
            Devuelve SOLO un JSON válido con este formato exacto:
            {
              "status": "APPROVED" | "UNJUSTIFIED",
              "claimExcerpt": "...",
              "narrativeAnalysis": "..."
            }
            Reglas:
            - Usa "APPROVED" cuando el concepto facturado tiene relación clara con el daño reportado.
            - Usa "UNJUSTIFIED" cuando no existe evidencia de relación o el concepto no está sustentado.
            - claimExcerpt debe ser un fragmento exacto del reporte que justifica o refuta el concepto.
            - narrativeAnalysis debe explicar en una frase la decisión.
            
            Concepto facturado:
            %s
            
            Reporte de siniestro:
            %s
            """.formatted(lineDescription, claimReport);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("LLM returned empty response");
            }

            JustificationAnalysis analysis = objectMapper.readValue(response.trim(), JustificationAnalysis.class);
            if (analysis.status() == null || analysis.status().isBlank()) {
                throw new IllegalStateException("Invalid justification status");
            }

            return analysis;
        } catch (Exception e) {
            log.warn("LLM justification analysis failed: {}", e.getMessage());
            return new JustificationAnalysis(
                    "UNJUSTIFIED",
                    null,
                    "No se pudo obtener una justificación confiable del LLM; se marca como UNJUSTIFIED."
            );
        }
    }

    public record JustificationAnalysis(
            String status,
            String claimExcerpt,
            String narrativeAnalysis
    ) {}
}
