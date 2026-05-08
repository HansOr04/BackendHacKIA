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
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof HttpClientErrorException.TooManyRequests) {
                log.warn("Nvidia API rate limit hit uploading {}", file.getOriginalFilename());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"message\":\"API rate limit reached. Please wait 60 seconds and try again.\",\"status\":429}");
            }
            log.error("Invoice upload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"message\":\"" + e.getMessage() + "\",\"status\":503}");
        }
    }
}