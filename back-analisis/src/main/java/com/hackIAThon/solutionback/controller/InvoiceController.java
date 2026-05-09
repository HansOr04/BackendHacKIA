package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.dto.InvoiceUploadResponse;
import com.hackIAThon.solutionback.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/invoice")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadInvoice(@RequestPart("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(invoiceService.uploadInvoice(file));
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String causeClass = cause.getClass().getSimpleName();
            if (cause instanceof HttpClientErrorException.TooManyRequests
                    || causeClass.contains("TooManyRequests")
                    || causeClass.contains("RateLimit")) {
                log.warn("API rate limit hit uploading {}", file.getOriginalFilename());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Límite de API alcanzado. Espera 60 segundos e intenta de nuevo.\",\"status\":429}");
            }
            log.error("Invoice upload failed: {} - {}", causeClass, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"message\":\"Error procesando la factura: " + e.getMessage().replace("\"", "'") + "\",\"status\":500}");
        }
    }
}