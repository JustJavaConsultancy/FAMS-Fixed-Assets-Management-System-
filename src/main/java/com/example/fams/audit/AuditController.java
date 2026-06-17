package com.example.fams.audit;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;

@Controller
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping({"/audit", "/auditor/assets", "/auditor/compliance"})
    public String auditManagement(@RequestParam(required = false) Long sessionId, Model model) {
        AuditSession activeSession = auditService.findSession(sessionId);
        model.addAttribute("sessions", auditService.findSessionSummaries());
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("verificationStatuses", AuditVerificationStatus.values());
        model.addAttribute("defaultAuditTitle", "Periodic Asset Audit - " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        if (activeSession != null) {
            model.addAttribute("results", auditService.resultsForSession(activeSession.getId()));
            model.addAttribute("discrepancyReport", auditService.discrepancyReport(activeSession.getId()));
        }
        model.addAttribute("historyReport", auditService.historyReport(LocalDate.now().minusMonths(1), LocalDate.now()));
        return "audit/audit-management";
    }

    @GetMapping("/auditor/audits/new")
    public String newAuditorAudit() {
        return "redirect:/audit";
    }

    @PostMapping("/audit/sessions")
    public String startSession(@RequestParam String title,
                               @RequestParam(required = false) String scopeLocation,
                               @RequestParam(required = false) String notes,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            AuditSession session = auditService.startSession(title, scopeLocation, displayName(authentication), notes);
            redirectAttributes.addFlashAttribute("successMessage", "Audit session started.");
            return "redirect:/audit?sessionId=" + session.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/audit";
        }
    }

    @PostMapping("/audit/sessions/{sessionId}/complete")
    public String completeSession(@PathVariable Long sessionId, RedirectAttributes redirectAttributes) {
        try {
            auditService.completeSession(sessionId);
            redirectAttributes.addFlashAttribute("successMessage", "Audit session completed.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/audit";
    }

    @GetMapping("/api/audits/assets/lookup")
    @ResponseBody
    public AuditAssetLookup lookupAsset(@RequestParam String code) {
        return auditService.lookupAsset(code);
    }

    @GetMapping("/api/audits/sessions/{sessionId}/results")
    @ResponseBody
    public java.util.List<AuditResultView> sessionResults(@PathVariable Long sessionId) {
        return auditService.resultsForSession(sessionId);
    }

    @PostMapping("/api/audits/sessions/{sessionId}/results")
    @ResponseBody
    public AuditResultView recordVerification(@PathVariable Long sessionId,
                                              @RequestBody AuditVerificationRequest request) {
        return auditService.recordVerification(sessionId, request);
    }

    @GetMapping("/api/audits/sessions/{sessionId}/discrepancies")
    @ResponseBody
    public AuditDiscrepancyReport discrepancyReport(@PathVariable Long sessionId) {
        return auditService.discrepancyReport(sessionId);
    }

    @GetMapping("/api/audits/history")
    @ResponseBody
    public AuditHistoryReport historyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return auditService.historyReport(from, to);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", ex.getMessage()));
    }

    private String displayName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "Auditor";
        }
        return authentication.getName();
    }
}
