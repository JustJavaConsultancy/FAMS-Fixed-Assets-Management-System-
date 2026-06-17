package com.example.fams.audit;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditSessionRepository sessionRepository;
    private final AuditVerificationResultRepository resultRepository;
    private final AssetRepository assetRepository;

    public AuditService(AuditSessionRepository sessionRepository,
                        AuditVerificationResultRepository resultRepository,
                        AssetRepository assetRepository) {
        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditSessionSummary> findSessionSummaries() {
        return sessionRepository.findAllByOrderByStartedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AuditSession findActiveOrLatestSession() {
        List<AuditSession> activeSessions = sessionRepository.findByStatusOrderByStartedAtDesc(AuditSessionStatus.ACTIVE);
        if (!activeSessions.isEmpty()) {
            return activeSessions.getFirst();
        }
        return sessionRepository.findAllByOrderByStartedAtDesc().stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public AuditSession findSession(Long sessionId) {
        if (sessionId == null) {
            return findActiveOrLatestSession();
        }
        return requireSession(sessionId);
    }

    @Transactional
    public AuditSession startSession(String title, String scopeLocation, String auditorName, String notes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Audit title is required.");
        }
        AuditSession session = new AuditSession();
        session.setTitle(title.trim());
        session.setScopeLocation(blankToNull(scopeLocation));
        session.setAuditorName(auditorName == null || auditorName.isBlank() ? "Auditor" : auditorName.trim());
        session.setNotes(blankToNull(notes));
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public AuditAssetLookup lookupAsset(String code) {
        String normalizedCode = normalizeCode(code);
        Asset asset = assetRepository.findByAssetCodeIgnoreCaseOrBarcodeValueIgnoreCase(normalizedCode, normalizedCode)
                .orElseThrow(() -> new NoSuchElementException("No asset found for scanned code " + normalizedCode + "."));
        return toLookup(asset);
    }

    @Transactional
    public AuditResultView recordVerification(Long sessionId, AuditVerificationRequest request) {
        if (request == null || request.getAssetId() == null) {
            throw new IllegalArgumentException("A scanned asset is required before submitting verification.");
        }
        if (request.getResultStatus() == null) {
            throw new IllegalArgumentException("Select a verification result.");
        }
        AuditSession session = requireActiveSession(sessionId);
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new NoSuchElementException("Asset not found."));

        AuditVerificationResult result = resultRepository.findBySessionIdAndAssetId(session.getId(), asset.getId())
                .orElseGet(AuditVerificationResult::new);
        result.setSession(session);
        result.setAsset(asset);
        result.setScannedCode(normalizeCode(
                request.getScannedCode() == null || request.getScannedCode().isBlank()
                        ? asset.getAssetCode()
                        : request.getScannedCode()
        ));
        result.setExpectedLocation(expectedLocation(asset));
        result.setActualLocation(blankToNull(request.getActualLocation()));
        result.setRegisterStatus(asset.getStatus());
        result.setResultStatus(request.getResultStatus());
        result.setConditionNotes(blankToNull(request.getConditionNotes()));

        return toView(resultRepository.save(result));
    }

    @Transactional
    public AuditSession completeSession(Long sessionId) {
        AuditSession session = requireActiveSession(sessionId);
        session.setStatus(AuditSessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<AuditResultView> resultsForSession(Long sessionId) {
        requireSession(sessionId);
        return resultRepository.findBySessionIdOrderByVerifiedAtDesc(sessionId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AuditDiscrepancyReport discrepancyReport(Long sessionId) {
        AuditSession session = requireSession(sessionId);
        List<AuditResultView> discrepancies = resultRepository.findBySessionIdOrderByVerifiedAtDesc(sessionId).stream()
                .filter(result -> result.getDiscrepancyType() != null)
                .map(this::toView)
                .toList();
        LinkedHashMap<String, Long> counts = discrepancies.stream()
                .collect(Collectors.groupingBy(AuditResultView::discrepancyType, LinkedHashMap::new, Collectors.counting()));
        return new AuditDiscrepancyReport(
                session.getId(),
                session.getTitle(),
                resultRepository.countBySessionId(sessionId),
                discrepancies.size(),
                counts,
                discrepancies
        );
    }

    @Transactional(readOnly = true)
    public AuditHistoryReport historyReport(LocalDate from, LocalDate to) {
        LocalDate endDate = to == null ? LocalDate.now() : to;
        LocalDate startDate = from == null ? endDate.minusMonths(1) : from;
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date.");
        }
        List<AuditSessionSummary> audits = sessionRepository
                .findByStatusAndCompletedAtBetweenOrderByCompletedAtDesc(
                        AuditSessionStatus.COMPLETED,
                        startDate.atStartOfDay(),
                        endDate.atTime(LocalTime.MAX)
                )
                .stream()
                .map(this::toSummary)
                .toList();
        return new AuditHistoryReport(startDate, endDate, audits.size(), audits);
    }

    private AuditSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Audit session not found."));
    }

    private AuditSession requireActiveSession(Long sessionId) {
        AuditSession session = requireSession(sessionId);
        if (session.getStatus() != AuditSessionStatus.ACTIVE) {
            throw new IllegalArgumentException("This audit session is already completed.");
        }
        return session;
    }

    private AuditSessionSummary toSummary(AuditSession session) {
        return new AuditSessionSummary(
                session.getId(),
                session.getTitle(),
                session.getAuditorName(),
                session.getScopeLocation(),
                session.getStatus(),
                session.getStartedAt(),
                session.getCompletedAt(),
                resultRepository.countBySessionId(session.getId()),
                resultRepository.countBySessionIdAndResultStatus(session.getId(), AuditVerificationStatus.MISSING),
                resultRepository.countBySessionIdAndDiscrepancyTypeIsNotNull(session.getId())
        );
    }

    private AuditAssetLookup toLookup(Asset asset) {
        return new AuditAssetLookup(
                asset.getId(),
                asset.getAssetCode(),
                asset.getBarcodeValue(),
                asset.getName(),
                asset.getCategory(),
                expectedLocation(asset),
                asset.getStatus(),
                asset.getCustodian(),
                asset.getImageUrl()
        );
    }

    private AuditResultView toView(AuditVerificationResult result) {
        Asset asset = result.getAsset();
        return new AuditResultView(
                result.getId(),
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                result.getExpectedLocation(),
                result.getActualLocation(),
                result.getRegisterStatus(),
                result.getResultStatus(),
                result.getDiscrepancyType(),
                result.getConditionNotes(),
                result.getVerifiedAt()
        );
    }

    private String expectedLocation(Asset asset) {
        return List.of(asset.getBranch(), asset.getDepartment()).stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" / "));
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Enter or scan an asset barcode or QR code.");
        }
        return code.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
