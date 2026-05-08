package com.hackIAThon.solutionback.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Consulta el VectorStore interno (pgvector) para obtener precios del tarifario
 * y verificar duplicados en el historial de peritaje.
 *
 * IMPORTANTE: Este service es el ÚNICO punto de acceso al VectorStore.
 * No llamar al VectorStore desde ningún otro service.
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);
    private static final int TOP_K = 3;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagQueryService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    /**
     * Consulta el VectorStore para obtener el precio tarifario de un concepto dado.
     * Busca en todos los PDFs de tarifario indexados.
     *
     * @param description descripción del concepto a consultar (case-insensitive)
     * @return precio tarifario como BigDecimal, o null si no se encuentra
     */
    @Cacheable(value = "tariffPrices", unless = "#result == null")
    public BigDecimal queryTariffPrice(String description) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(description).topK(TOP_K).build()
            );

            if (results.isEmpty()) {
                log.warn("No tariff data found in VectorStore for description: {}", description);
                return null;
            }

            // Unir el contenido de los fragmentos recuperados
            String context = results.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n" + b);

            String prompt = """
            Eres un experto analizando tarifarios de talleres automotrices.
            En el siguiente texto se encuentra una tabla de precios.
            Encuentra el precio exacto correspondiente al concepto: '%s'.
            Responde ÚNICAMENTE con el número decimal (ej. 120.00), sin texto adicional, sin símbolos de moneda.
            Si el concepto no existe en el texto, responde 'NO_ENCONTRADO'.
            
            Texto del tarifario:
            %s
            """.formatted(description, context);

            String response = chatClient.prompt().user(prompt).call().content().trim();
            
            if ("NO_ENCONTRADO".equalsIgnoreCase(response)) {
                 log.warn("Tariff fragments found for '{}' but LLM could not find the exact price.", description);
                 return null;
            }

            try {
                // Limpiar posibles símbolos que la IA haya agregado por error
                response = response.replaceAll("[^\\d.]", "");
                BigDecimal price = new BigDecimal(response);
                log.debug("Tariff price found for '{}': {}", description, price);
                return price;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse price returned by LLM: '{}' para '{}'", response, description);
                return null;
            }

        } catch (Exception e) {
            log.error("Error querying VectorStore for tariff price of '{}': {}", description, e.getMessage());
            return null;
        }
    }

    /**
     * Verifica si existe un concepto duplicado en el historial de peritaje
     * indexado en el VectorStore.
     *
     * @param description descripción del concepto (case-insensitive)
     * @param category    categoría del ítem (PART / LABOR)
     * @param claimId     ID del siniestro actual (para contexto de búsqueda)
     * @return true si se detecta duplicidad en el historial
     */
    public boolean checkDuplicate(String description, String category, Long claimId) {
        try {
            String query = String.format("historial duplicado %s %s siniestro", description, category);
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(TOP_K).build()
            );

            if (results.isEmpty()) {
                return false;
            }

            // Determinar si algún fragmento del historial menciona el concepto en otro siniestro
            String descLower = description.toLowerCase();
            for (Document doc : results) {
                String content = doc.getText().toLowerCase();
                Object docClaimId = doc.getMetadata().get("claim_id");

                boolean contentMatches = content.contains(descLower);
                boolean differentClaim = docClaimId == null || !docClaimId.toString().equals(claimId.toString());

                if (contentMatches && differentClaim) {
                    log.info("Duplicate detected for '{}' (category: {}) in a previous claim.", description, category);
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Error checking duplicates in VectorStore for '{}': {}", description, e.getMessage());
            return false;
        }
    }

    /**
     * Retorna la referencia exacta del tarifario (nombre del documento y sección)
     * para un concepto dado.
     *
     * @param description descripción del concepto
     * @return referencia textual del documento fuente, o null si no aplica
     */
    public String queryTariffReference(String description) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(description).topK(1).build()
            );

            if (results.isEmpty()) return null;

            Document doc = results.get(0);
            String source = (String) doc.getMetadata().getOrDefault("source", null);
            String page   = doc.getMetadata().getOrDefault("page_number", "").toString();

            if (source != null) {
                return page.isBlank()
                    ? source
                    : source + " — pág. " + page;
            }
            return null;

        } catch (Exception e) {
            log.error("Error querying tariff reference for '{}': {}", description, e.getMessage());
            return null;
        }
    }
}
