package com.example.fams.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditSessionRepository extends JpaRepository<AuditSession, Long> {

    List<AuditSession> findAllByOrderByStartedAtDesc();

    List<AuditSession> findByStatusOrderByStartedAtDesc(AuditSessionStatus status);

    List<AuditSession> findByStatusAndCompletedAtBetweenOrderByCompletedAtDesc(
            AuditSessionStatus status,
            LocalDateTime from,
            LocalDateTime to
    );
}
