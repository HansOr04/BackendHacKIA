package com.hackIAThon.solutionback.repository;

import com.hackIAThon.solutionback.entity.AuditResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditResultRepository extends JpaRepository<AuditResult, Long> {
    Optional<AuditResult> findByInvoiceId(Long invoiceId);
}
