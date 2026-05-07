package com.hackIAThon.solutionback.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$?([\\d,]+\\.?\\d*)");

    private final VectorStore vectorStore;

    public RagQueryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Consulta el VectorStore para obtener el precio tarifario de un concepto dado.
     * Busca en todos los PDFs de tarifario indexados.
     *
     * @param description descripción del concepto a consultar (case-insensitive)
     * @return precio tarifario como BigDecimal, o null si no se encuentra
     */
    public BigDecimal queryTariffPrice(String description) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(description).topK(TOP_K).build()
            );

            if (results.isEmpty()) {
                log.warn("No tariff data found in VectorStore for description: {}", description);
                return null;
            }

            // Extraer el primer precio numérico encontrado en el fragmento más relevante
            for (Document doc : results) {
                String content = doc.getText();
                Matcher matcher = PRICE_PATTERN.matcher(content);
                if (matcher.find()) {
                    String priceStr = matcher.group(1).replace(",", "");
                    log.debug("Tariff price found for '{}': {} (source: {})",
                        description, priceStr, doc.getMetadata().getOrDefault("source", "unknown"));
                    return new BigDecimal(priceStr);
                }
            }

            log.warn("Tariff fragments found for '{}' but no parseable price in content.", description);
            return null;

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

            Document doc = results.getFirst();
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
