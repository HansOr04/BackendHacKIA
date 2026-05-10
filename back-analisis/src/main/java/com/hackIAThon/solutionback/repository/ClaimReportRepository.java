package com.hackIAThon.solutionback.repository;

import com.hackIAThon.solutionback.entity.ClaimReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClaimReportRepository extends JpaRepository<ClaimReport, Long> {
    Optional<ClaimReport> findByClaimId(Long claimId);
}
