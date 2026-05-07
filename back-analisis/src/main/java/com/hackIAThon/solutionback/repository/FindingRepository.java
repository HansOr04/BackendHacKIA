package com.hackIAThon.solutionback.repository;

import com.hackIAThon.solutionback.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FindingRepository extends JpaRepository<Finding, Long> {
}