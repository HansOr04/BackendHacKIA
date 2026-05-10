package com.hackIAThon.solutionback.repository;

import com.hackIAThon.solutionback.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoice_Id(Long invoiceId);

    List<InvoiceLine> findByInvoice_ClaimId(Long claimId);
}