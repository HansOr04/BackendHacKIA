package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.dto.AuditResultResponse;
import com.hackIAThon.solutionback.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/invoice/{invoiceId}")
    public ResponseEntity<AuditResultResponse> auditInvoice(@PathVariable Long invoiceId) {
        AuditResultResponse result = auditService.auditInvoice(invoiceId);
        return ResponseEntity.ok(result);
    }
}