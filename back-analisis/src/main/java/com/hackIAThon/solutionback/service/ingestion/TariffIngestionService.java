package com.hackIAThon.solutionback.service.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Carga todos los PDFs de tarifario ubicados en src/main/resources/docs/ al arrancar.
 * También expone ingestPdf() para indexar tarifarios adicionales en tiempo de ejecución.
 *
 * Reglas:
 *   - Todos los PDFs en classpath:/docs/ se indexan automáticamente al iniciar.
 *   - La deduplicación se hace por nombre de archivo (metadata "source").
 *   - Este service es el ÚNICO que puede escribir al VectorStore.
 */
@Component
@Order(1)
public class TariffIngestionService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TariffIngestionService.class);

    private final VectorStore vectorStore;

    public TariffIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) {
        try {
            Resource[] pdfs = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:/docs/*.pdf");

            if (pdfs.length == 0) {
                log.warn("No PDF tarifarios found in classpath:/docs/ — RAG queries may not work.");
                return;
            }

            TokenTextSplitter splitter = new TokenTextSplitter(400, 50, 5, 10000, true);
            List<Document> allChunks = new ArrayList<>();

            for (Resource pdf : pdfs) {
                List<Document> chunks = indexIfNeeded(pdf.getFilename(), pdf, splitter);
                allChunks.addAll(chunks);
            }

            if (!allChunks.isEmpty()) {
                vectorStore.accept(allChunks);
                log.info("VectorStore loaded: {} total chunks from {} PDFs.", allChunks.size(), pdfs.length);
            }
        } catch (Exception e) {
            log.error("Tariff ingestion failed — app will still start, but RAG queries may not work: {}", e.getMessage());
        }
    }

    /**
     * Indexa un PDF subido en tiempo de ejecución. Idempotente por nombre de archivo.
     *
     * @param filename nombre del archivo (usado como clave de deduplicación)
     * @param pdfBytes contenido binario del PDF
     * @return número de chunks indexados (0 si ya estaba indexado)
     */
    public int ingestPdf(String filename, byte[] pdfBytes) {
        Resource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        TokenTextSplitter splitter = new TokenTextSplitter(400, 50, 5, 10000, true);
        List<Document> chunks = indexIfNeeded(filename, resource, splitter);

        if (!chunks.isEmpty()) {
            vectorStore.accept(chunks);
            log.info("Runtime tariff ingestion complete: '{}' → {} chunks indexed.", filename, chunks.size());
        }
        return chunks.size();
    }

    private List<Document> indexIfNeeded(String filename, Resource resource, TokenTextSplitter splitter) {
        if (!resource.exists()) {
            log.warn("PDF resource not found, skipping: {}", filename);
            return List.of();
        }

        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("información")
                        .topK(1)
                        .filterExpression("source == '" + filename + "'")
                        .build()
        );

        if (existing != null && !existing.isEmpty()) {
            log.info("PDF '{}' already indexed in VectorStore. Skipping.", filename);
            return List.of();
        }

        log.info("Indexing tariff PDF: {}", filename);
        var reader = new PagePdfDocumentReader(resource);
        List<Document> chunks = splitter.apply(reader.get());
        chunks.forEach(doc -> doc.getMetadata().put("source", filename));
        log.info("  → {} chunks generated from '{}'", chunks.size(), filename);
        return chunks;
    }
}
