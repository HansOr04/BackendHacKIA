package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.dto.nvidia.FacturaData;
import com.hackIAThon.solutionback.service.NvidiaExtractionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/nvidia")
public class NvidiaInvoiceController {

    private final NvidiaExtractionService nvidiaExtractionService;

    public NvidiaInvoiceController(NvidiaExtractionService nvidiaExtractionService) {
        this.nvidiaExtractionService = nvidiaExtractionService;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FacturaData> extract(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            FacturaData result = nvidiaExtractionService.extractFromPdf(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
