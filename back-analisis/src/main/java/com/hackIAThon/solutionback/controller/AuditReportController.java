package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.dto.AuditReportResponse;
import com.hackIAThon.solutionback.service.AuditReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/audit")
public class AuditReportController {

    private final AuditReportService auditReportService;

    public AuditReportController(AuditReportService auditReportService) {
        this.auditReportService = auditReportService;
    }

    @PostMapping("/invoice/{invoiceId}/report")
    public ResponseEntity<AuditReportResponse> generateReport(@PathVariable Long invoiceId) {
        try {
            return ResponseEntity.ok(auditReportService.generateReport(invoiceId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/invoice/{invoiceId}/report")
    public ResponseEntity<AuditReportResponse> getReport(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(auditReportService.getReport(invoiceId));
    }
}
