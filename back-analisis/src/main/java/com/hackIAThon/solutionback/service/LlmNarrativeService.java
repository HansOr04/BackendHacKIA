package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.entity.Finding;
import com.hackIAThon.solutionback.entity.FindingType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmNarrativeService {

    private final ChatClient chatClient;

    public LlmNarrativeService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String generateNarrative(List<Finding> findings, int riskScore, String recommendation) {
        long discrepancies = findings.stream().filter(f -> f.getType() == FindingType.PRICE_EXCEEDED).count();
        long duplicates = findings.stream().filter(f -> f.getType() == FindingType.DUPLICATE).count();
        long unjustified = findings.stream().filter(f -> f.getType() == FindingType.UNJUSTIFIED).count();

        String prompt = String.format(
                "Eres un auditor de seguros. Genera un resumen ejecutivo en español de máximo 3 oraciones " +
                "para una auditoría de factura con los siguientes resultados: " +
                "Score de riesgo: %d/100. Recomendación: %s. " +
                "Hallazgos: %d discrepancias de precio, %d ítems duplicados, %d ítems no justificados. " +
                "Sé conciso y directo.",
                riskScore, recommendation, discrepancies, duplicates, unjustified
        );

        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            return String.format(
                    "Auditoría completada. Score: %d. Recomendación: %s. Hallazgos: %d discrepancias, %d duplicados, %d no justificados.",
                    riskScore, recommendation, discrepancies, duplicates, unjustified
            );
        }
    }
}
