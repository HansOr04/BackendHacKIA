package com.hackIAThon.solutionback.repository;

import com.hackIAThon.solutionback.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByClaimId(String claimId);
}
