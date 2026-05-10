package com.hackIAThon.solutionback.repository;

import com.hackIAThon.solutionback.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByClaimId(Long claimId);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.lines WHERE i.id = :id")
    Optional<Invoice> findByIdWithLines(@Param("id") Long id);
}
