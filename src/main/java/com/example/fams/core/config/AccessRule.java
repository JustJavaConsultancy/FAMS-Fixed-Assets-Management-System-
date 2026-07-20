package com.example.fams.core.config;

import java.util.List;

/**
 * Declarative URL-to-group access rules used by {@link RoleAuthorizationInterceptor}.
 *
 * <p>Each rule binds a URL path pattern (matched as an Ant-style prefix) to the set of
 * Keycloak groups that are allowed to open pages under it. The rules are checked by
 * longest-pattern-first so that, for example, a {@code /admin/**} rule wins over a
 * catch-all shared-page rule.
 *
 * <p>Group names are the same tokens resolved by {@link AuthenticationManager} from the
 * user's Keycloak {@code groups} claim (e.g. {@code admin}, {@code auditor},
 * {@code departmentHead}, {@code employees}). A rule with an empty group list means the
 * page is open to any authenticated user.
 */
public enum AccessRule {

    // Role-restricted areas
    ADMIN("admin", "/admin", List.of("admin")),
    SUPER_ADMIN("superadmin", "/superadmin", List.of("admin")),
    AUDITOR("auditor", "/auditor", List.of("auditor")),
    DEPARTMENT_HEAD("department-head", "/department-head", List.of("departmentHead")),
    EMPLOYEE("employee", "/employee", List.of("employees")),

    // Shared areas — any authenticated user may view
    ASSETS("assets", "/assets", List.of()),
    DEPRECIATION("depreciation", "/depreciation", List.of()),
    REPORTS("reports", "/reports", List.of()),
    AUDIT("audit", "/audit", List.of()),
    MAINTENANCE("maintenance", "/maintenance", List.of()),
    NOTIFICATIONS("notifications", "/notifications", List.of()),
    DASHBOARD("dashboard", "/dashboard", List.of()),
    ROOT("root", "/", List.of());

    private final String name;
    private final String pattern;
    private final List<String> allowedGroups;

    AccessRule(String name, String pattern, List<String> allowedGroups) {
        this.name = name;
        this.pattern = pattern;
        this.allowedGroups = allowedGroups;
    }

    public String getName() {
        return name;
    }

    public String getPattern() {
        return pattern;
    }

    public List<String> getAllowedGroups() {
        return allowedGroups;
    }

    /**
     * Find the most specific rule that matches the given request path.
     * Returns {@code null} when nothing matches (treated as open / not intercepted).
     */
    public static AccessRule match(String path) {
        if (path == null) {
            return null;
        }
        AccessRule best = null;
        for (AccessRule rule : values()) {
            if (pathMatches(path, rule.pattern)) {
                if (best == null || rule.pattern.length() > best.pattern.length()) {
                    best = rule;
                }
            }
        }
        return best;
    }

    private static boolean pathMatches(String path, String pattern) {
        if ("/".equals(pattern)) {
            // Root rule only matches the exact root path.
            return path.equals("/") || path.isEmpty();
        }
        return path.equals(pattern) || path.startsWith(pattern + "/");
    }
}
