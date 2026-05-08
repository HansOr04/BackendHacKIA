package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.dto.JustificationResultResponse;
import com.hackIAThon.solutionback.exception.ResourceNotFoundException;
import com.hackIAThon.solutionback.service.JustificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/audit")
public class JustificationController {

    private final JustificationService justificationService;

    public JustificationController(JustificationService justificationService) {
        this.justificationService = justificationService;
    }

    @PostMapping("/invoice/{invoiceId}/justify")
    public ResponseEntity<JustificationResultResponse> justifyInvoice(@PathVariable Long invoiceId) {
        try {
            JustificationResultResponse result = justificationService.analyzeJustification(invoiceId);
            return ResponseEntity.ok(result);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
