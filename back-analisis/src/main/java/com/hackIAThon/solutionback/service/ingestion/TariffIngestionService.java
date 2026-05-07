package com.hackIAThon.solutionback.service.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de inicialización que carga los PDFs del tarifario en el VectorStore
 * al arrancar la aplicación.
 *
 * Flujo:
 *   1. Lee cada PDF desde src/main/resources/docs/
 *   2. Fragmenta el contenido en chunks semánticamente coherentes (TokenTextSplitter)
 *   3. Persiste los chunks en el VectorStore (pgvector) para búsquedas RAG
 *
 * IMPORTANTE:
 *   - Los PDFs deben ubicarse en src/main/resources/docs/
 *   - Siempre fragmentar con TokenTextSplitter antes de insertar — nunca insertar el PDF completo
 *   - Este service es el ÚNICO que puede escribir al VectorStore
 */
@Component
public class TariffIngestionService implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TariffIngestionService.class);
    private final VectorStore vectorStore;
    @Value("classpath:/docs/tarifario_siniestros_automotriz.pdf")
    private Resource tarifario;

    public TariffIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {
        TokenTextSplitter splitter = new TokenTextSplitter();

        List<Resource> pdfResources = List.of(tarifario);
        List<Document> allChunks = new ArrayList<>();

        for (Resource pdf : pdfResources) {
            if (!pdf.exists()) {
                log.warn("PDF resource not found, skipping: {}", pdf.getFilename());
                continue;
            }

            log.info("Indexing PDF: {}", pdf.getFilename());
            var reader = new PagePdfDocumentReader(pdf);
            List<Document> chunks = splitter.apply(reader.get());

            // Enriquecer metadata con nombre del archivo fuente para trazabilidad
            chunks.forEach(doc ->
                doc.getMetadata().put("source", pdf.getFilename())
            );

            allChunks.addAll(chunks);
            log.info("  → {} chunks generated from {}", chunks.size(), pdf.getFilename());
        }

        if (!allChunks.isEmpty()) {
            vectorStore.accept(allChunks);
            log.info("VectorStore loaded successfully with {} total chunks from {} PDFs.",
                allChunks.size(), pdfResources.size());
        } else {
            log.warn("No PDF documents were indexed. Ensure PDFs exist in src/main/resources/docs/");
        }
    }
}
