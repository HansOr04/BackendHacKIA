package com.hackIAThon.solutionback.service.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
    public void run(String... args) {
        try {
            runIngestion();
        } catch (Exception e) {
            log.error("Tariff ingestion failed — app will still start, but RAG queries may not work: {}", e.getMessage());
        }
    }

    private void runIngestion() {
        TokenTextSplitter splitter = new TokenTextSplitter();

        List<Resource> pdfResources = List.of(tarifario);
        List<Document> allChunks = new ArrayList<>();

        for (Resource pdf : pdfResources) {
            if (!pdf.exists()) {
                log.warn("PDF resource not found, skipping: {}", pdf.getFilename());
                continue;
            }

            String filename = pdf.getFilename();
            List<Document> existing = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("información")
                            .topK(1)
                            .filterExpression("source == '" + filename + "'")
                            .build()
            );

            if (!existing.isEmpty()) {
                log.info("PDF {} already indexed in VectorStore. Skipping.", filename);
                continue;
            }

            log.info("Indexing PDF: {}", filename);
            var reader = new PagePdfDocumentReader(pdf);
            List<Document> chunks = splitter.apply(reader.get());

            chunks.forEach(doc ->
                doc.getMetadata().put("source", filename)
            );

            allChunks.addAll(chunks);
            log.info("  → {} chunks generated from {}", chunks.size(), filename);
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
