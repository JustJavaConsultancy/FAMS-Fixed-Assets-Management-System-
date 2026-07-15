package com.example.fams.core.config;

import com.example.fams.organization.DepartmentHead;
import com.example.fams.organization.DepartmentHeadRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the data-visibility scope of the currently authenticated user for
 * reports and exports (RBAC-controlled visibility).
 *
 * <p>Scope rules:
 * <ul>
 *     <li>admin / auditor / assetManager (and any unrecognised authenticated role) → {@link ScopeType#GLOBAL}</li>
 *     <li>department head → {@link ScopeType#DEPARTMENT} limited to the department(s) they head</li>
 *     <li>employee → {@link ScopeType#CUSTODIAN} limited to assets in their custody</li>
 * </ul>
 *
 * <p>Role precedence is checked highest-privilege first so a user who is both an
 * asset manager and (incidentally) an employee still gets the broader global scope.
 * Candidate/department resolution mirrors the logic already used by
 * {@code EmployeeController} and {@code DepartmentController} so scoping stays consistent
 * with the existing role dashboards (replicated deliberately to avoid refactoring those
 * working controllers).
 */
@Service
public class SecurityScopeService {

    private final AuthenticationManager authenticationManager;
    private final DepartmentHeadRepository departmentHeadRepository;

    public SecurityScopeService(AuthenticationManager authenticationManager,
                                DepartmentHeadRepository departmentHeadRepository) {
        this.authenticationManager = authenticationManager;
        this.departmentHeadRepository = departmentHeadRepository;
    }

    public enum ScopeType {
        GLOBAL,
        DEPARTMENT,
        CUSTODIAN
    }

    /**
     * The current user's report/export scope.
     *
     * @param type        the kind of restriction
     * @param departments department names (for {@link ScopeType#DEPARTMENT}); empty otherwise
     * @param custodians  lower-cased custodian identifiers (for {@link ScopeType#CUSTODIAN}); empty otherwise
     */
    public record ReportScope(ScopeType type, Set<String> departments, Set<String> custodians) {

        public boolean isGlobal() {
            return type == ScopeType.GLOBAL;
        }

        public boolean isDepartment() {
            return type == ScopeType.DEPARTMENT;
        }

        public boolean isCustodian() {
            return type == ScopeType.CUSTODIAN;
        }

        /**
         * True when the restriction resolves to an empty set (department head with no
         * assigned department, or employee with no identity claims). Such users must see
         * no rows rather than everything.
         */
        public boolean isEmptyRestriction() {
            return (type == ScopeType.DEPARTMENT && departments.isEmpty())
                    || (type == ScopeType.CUSTODIAN && custodians.isEmpty());
        }

        /** Stable string used as part of the cache key. */
        public String cacheKey() {
            return switch (type) {
                case GLOBAL -> "GLOBAL";
                case DEPARTMENT -> "DEPT:" + String.join(",", departments);
                case CUSTODIAN -> "CUST:" + String.join(",", custodians);
            };
        }
    }

    /**
     * Compute the scope for the currently authenticated principal.
     */
    public ReportScope getScope() {
        // Highest privilege first — these roles legitimately see everything.
        if (authenticationManager.isAdmin()
                || authenticationManager.isAuditor()
                || authenticationManager.isAssetManager()) {
            return new ReportScope(ScopeType.GLOBAL, Set.of(), Set.of());
        }

        if (authenticationManager.isDepartmentHead()) {
            Set<String> departments = resolveDepartments();
            return new ReportScope(ScopeType.DEPARTMENT, departments, Set.of());
        }

        if (authenticationManager.isEmployee()) {
            Set<String> custodians = new LinkedHashSet<>();
            for (String candidate : userLookupCandidates()) {
                custodians.add(candidate.toLowerCase());
            }
            return new ReportScope(ScopeType.CUSTODIAN, Set.of(), custodians);
        }

        // Any other authenticated role (e.g. legacy HR / financial officers): keep the
        // prior global behaviour so we don't regress access for existing users.
        return new ReportScope(ScopeType.GLOBAL, Set.of(), Set.of());
    }

    private Set<String> resolveDepartments() {
        Set<Long> seenHeadIds = new LinkedHashSet<>();
        Set<String> departments = new LinkedHashSet<>();
        for (String candidate : userLookupCandidates()) {
            collectDepartments(departmentHeadRepository.findByUserIdAndIsActiveTrueOrderByAssignedAtDesc(candidate), seenHeadIds, departments);
            collectDepartments(departmentHeadRepository.findByUserNameIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(candidate), seenHeadIds, departments);
            collectDepartments(departmentHeadRepository.findByUserEmailIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(candidate), seenHeadIds, departments);
        }
        return departments;
    }

    private void collectDepartments(List<DepartmentHead> heads, Set<Long> seenHeadIds, Set<String> departments) {
        for (DepartmentHead head : heads) {
            if (!seenHeadIds.add(head.getId())) {
                continue;
            }
            if (head.getDepartment() != null) {
                String name = head.getDepartment().getName();
                if (name != null && !name.isBlank()) {
                    departments.add(name.trim());
                }
            }
        }
    }

    /**
     * Possible identifiers for the current user (subject id, preferred_username, email,
     * display name). Mirrors EmployeeController / DepartmentController so custodian and
     * department-head matches behave identically.
     */
    private List<String> userLookupCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            candidates.add(auth.getName());
        }
        if (auth != null && auth.getPrincipal() instanceof DefaultOidcUser oidc) {
            addClaim(candidates, oidc.getSubject());
            addClaim(candidates, oidc.getClaims().get("preferred_username"));
            addClaim(candidates, oidc.getClaims().get("email"));
            addClaim(candidates, oidc.getClaims().get("name"));
        }
        return candidates.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private void addClaim(Set<String> candidates, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            candidates.add(text);
        }
    }
}
