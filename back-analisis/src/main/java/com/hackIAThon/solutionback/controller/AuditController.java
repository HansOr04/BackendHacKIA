package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.dto.AuditResultResponse;
import com.hackIAThon.solutionback.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/invoice/{invoiceId}")
    public ResponseEntity<AuditResultResponse> auditInvoice(@PathVariable Long invoiceId) {
        try {
            return ResponseEntity.ok(auditService.auditInvoice(invoiceId));
        } catch (Exception e) {
            log.error("Audit failed for invoiceId={}: {}", invoiceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}