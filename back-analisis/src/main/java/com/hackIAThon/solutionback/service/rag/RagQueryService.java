package com.hackIAThon.solutionback.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta el VectorStore interno (pgvector) para obtener precios del tarifario.
 *
 * Estrategia de extracción de precio (dos niveles):
 *   1. Regex rápido: busca precio en la misma línea que las palabras clave.
 *   2. Fallback LLM: si el regex falla, pide al LLM que extraiga el precio del contexto.
 *
 * IMPORTANTE: Este service es el ÚNICO punto de acceso al VectorStore.
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);
    private static final int TOP_K = 5;
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\b(\\d{1,6}(?:[.,]\\d{1,2})?)\\b");

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagQueryService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    @Cacheable(value = "tariffPrices", unless = "#result == null")
    public BigDecimal queryTariffPrice(String description) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(description).topK(TOP_K).build()
            );

            if (results == null || results.isEmpty()) {
                log.warn("No tariff data found in VectorStore for: {}", description);
                return null;
            }

            String context = results.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n" + b);

            // Level 1: fast regex extraction
            BigDecimal price = extractPriceByRegex(description, context);

            // Level 2: LLM fallback when regex can't find the price
            if (price == null) {
                log.debug("Regex failed for '{}', trying LLM extraction.", description);
                price = extractPriceWithLlm(description, context);
            }

            if (price != null) {
                log.info("Tariff price resolved for '{}': {}", description, price);
            } else {
                log.warn("No price extractable from tariff chunks for '{}'", description);
            }
            return price;

        } catch (Exception e) {
            log.error("Error querying tariff price for '{}': {}", description, e.getMessage());
            return null;
        }
    }

    public boolean checkDuplicate(String description, String category, Long claimId) {
        try {
            String query = String.format("historial duplicado %s %s", description, category);
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(TOP_K).build()
            );

            if (results == null || results.isEmpty()) return false;

            String descLower = normalize(description);
            for (Document doc : results) {
                String content = normalize(doc.getText());
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
            log.error("Error checking duplicates for '{}': {}", description, e.getMessage());
            return false;
        }
    }

    public String queryTariffReference(String description) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(description).topK(1).build()
            );

            if (results == null || results.isEmpty()) return null;

            Document doc = results.get(0);
            String source = (String) doc.getMetadata().getOrDefault("source", null);
            String page   = doc.getMetadata().getOrDefault("page_number", "").toString();

            if (source != null) {
                return page.isBlank() ? source : source + " — pág. " + page;
            }
            return null;

        } catch (Exception e) {
            log.error("Error querying tariff reference for '{}': {}", description, e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────── PRIVATE ────────────────────────────────────

    /**
     * Regex extraction: looks for a price on the same line as the description keywords.
     * Normalizes accented characters before matching for encoding resilience.
     */
    private BigDecimal extractPriceByRegex(String description, String context) {
        String[] keywords = normalize(description).split("[\\s,./\\-()+]+");
        String[] lines = context.split("\n");

        int bestScore = 0;
        BigDecimal bestPrice = null;

        for (String line : lines) {
            String lineNorm = normalize(line);
            int score = 0;
            for (String kw : keywords) {
                if (kw.length() >= 4 && lineNorm.contains(kw)) score++;
            }
            if (score > 0 && score >= bestScore) {
                Matcher m = PRICE_PATTERN.matcher(line);
                while (m.find()) {
                    try {
                        BigDecimal candidate = new BigDecimal(m.group(1).replace(",", "."));
                        if (candidate.compareTo(BigDecimal.TEN) >= 0) {
                            bestPrice = candidate;
                            bestScore = score;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return bestPrice;
    }

    /**
     * LLM fallback: asks the language model to extract the tariff price from raw chunks.
     * Called only when regex extraction fails.
     */
    private BigDecimal extractPriceWithLlm(String description, String context) {
        try {
            String limitedContext = context.length() > 2500
                ? context.substring(0, 2500)
                : context;

            String prompt = """
                Eres un asistente de auditoría de seguros. Dado el siguiente texto de un tarifario,
                extrae el precio de referencia para el concepto indicado.

                Concepto: "%s"

                Texto del tarifario:
                %s

                Responde ÚNICAMENTE con el número decimal del precio (ejemplo: 45.00).
                Si el concepto no aparece en el tarifario, responde exactamente: null
                Sin explicaciones, sin símbolos de moneda, solo el número o null.
                """.formatted(description, limitedContext);

            String raw = chatClient.prompt().user(prompt).call().content();

            if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("null")) {
                return null;
            }

            Matcher m = PRICE_PATTERN.matcher(raw.trim());
            while (m.find()) {
                try {
                    BigDecimal candidate = new BigDecimal(m.group(1).replace(",", "."));
                    if (candidate.compareTo(BigDecimal.TEN) >= 0) {
                        log.debug("LLM extracted price {} for '{}'", candidate, description);
                        return candidate;
                    }
                } catch (NumberFormatException ignored) {}
            }

        } catch (Exception e) {
            log.warn("LLM price extraction failed for '{}': {}", description, e.getMessage());
        }
        return null;
    }

    /** Strips diacritics and lowercases for encoding-resilient comparisons. */
    private static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
