package com.hackIAThon.solutionback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackIAThon.solutionback.dto.ExtractedInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;


/**
 * Extrae líneas de factura estructuradas desde un PDF usando ChatClient (Ollama).
 *
 * IMPORTANTE: Este service es el ÚNICO que puede llamar al ChatClient para extracción.
 * No llamar al ChatClient desde ningún otro service salvo LlmAnalysisService.
 */
@Service
public class LlmExtractionService {

    private static final Logger log = LoggerFactory.getLogger(LlmExtractionService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmExtractionService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Lee el PDF de la factura y extrae sus líneas como JSON estructurado.
     *
     * @param pdfResource PDF de la factura subido por el usuario
     * @return lista de líneas extraídas con descripción, categoría y precio unitario
     */
    public ExtractedInvoice extractInvoice(Resource pdfResource) {
        try {
            var reader = new PagePdfDocumentReader(pdfResource);
            String pdfContent = reader.get().stream()
                    .map(Document::getText)
                    .reduce("", (a, b) -> a + "\n" + b);

            String prompt = """
            Eres un extractor de facturas de talleres automotrices.
            Analiza el siguiente contenido de una factura y extrae los datos.
            
            Retorna SOLO un JSON con este formato exacto, sin explicaciones:
            {
              "claimId": 0,
              "workshopName": "nombre del taller",
              "claimReport": "texto del reporte de siniestro",
              "lines": [
                {
                  "description": "descripción del concepto",
                  "category": "PART o LABOR",
                  "quantity": 0.00,
                  "unitPrice": 0.00
                }
              ]
            }
            
            Reglas:
            - claimId es el número de siniestro encontrado en la factura
            - category es PART si es repuesto o material, LABOR si es mano de obra
            - quantity es la cantidad extraida de la factura (1 si no se especifica)
            - unitPrice es número decimal sin símbolo de moneda
            - claimReport es el texto del reporte de siniestro asociado al reclamo
            
            Contenido de la factura:
            %s
            """.formatted(pdfContent);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String cleanedResponse = response.trim();
            int startIndex = cleanedResponse.indexOf('{');
            int endIndex = cleanedResponse.lastIndexOf('}');
            if (startIndex != -1 && endIndex != -1) {
                cleanedResponse = cleanedResponse.substring(startIndex, endIndex + 1);
            }

            ExtractedInvoice extracted = objectMapper.readValue(cleanedResponse, ExtractedInvoice.class);
            log.info("LLM extracted invoice: claimId={}, workshop={}, lines={}",
                    extracted.claimId(), extracted.workshopName(), extracted.lines().size());

            return extracted;

        } catch (Exception e) {
            log.error("Error extracting invoice from PDF: {}", e.getMessage());
            throw new RuntimeException("Error extracting invoice data from PDF: " + e.getMessage());
        }
    }
}