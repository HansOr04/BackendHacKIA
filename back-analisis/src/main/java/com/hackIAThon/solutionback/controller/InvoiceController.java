package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.dto.InvoiceUploadResponse;
import com.hackIAThon.solutionback.service.InvoiceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/invoice")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceUploadResponse> uploadInvoice(
            @RequestPart("file") MultipartFile file) {
        InvoiceUploadResponse response = invoiceService.uploadInvoice(file);
        return ResponseEntity.ok(response);
    }
}