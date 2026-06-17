package com.example.fams.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditVerificationResultRepository extends JpaRepository<AuditVerificationResult, Long> {

    List<AuditVerificationResult> findBySessionIdOrderByVerifiedAtDesc(Long sessionId);

    Optional<AuditVerificationResult> findBySessionIdAndAssetId(Long sessionId, Long assetId);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndResultStatus(Long sessionId, AuditVerificationStatus resultStatus);

    long countBySessionIdAndDiscrepancyTypeIsNotNull(Long sessionId);
}
