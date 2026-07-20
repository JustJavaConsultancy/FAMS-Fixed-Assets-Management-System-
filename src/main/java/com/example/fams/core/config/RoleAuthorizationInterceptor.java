package com.example.fams.core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * URL-level authorization guard.
 *
 * <p>Resolves the currently authenticated user's Keycloak groups and, for any request
 * that matches a {@link AccessRule}, verifies the user belongs to one of the rule's
 * allowed groups. Users without the required group are redirected to the
 * {@code /access-denied} page (instead of receiving a raw 403) so the experience stays
 * consistent with the app's server-rendered, redirect-based UX.
 *
 * <p>The interceptor only BLOCKS; it never grants. Because {@code Oauth2SecurityConfig}
 * already requires authentication for every request, an unauthenticated caller never
 * reaches this point.
 */
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    /** Paths the interceptor must never redirect away from (prevents redirect loops). */
    private static final String ACCESS_DENIED_PATH = "/access-denied";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // Never intercept the denied page itself.
        if (ACCESS_DENIED_PATH.equals(path)) {
            return true;
        }

        AccessRule rule = AccessRule.match(path);
        if (rule == null || rule.getAllowedGroups().isEmpty()) {
            // No rule, or a shared area open to any authenticated user.
            return true;
        }

        List<String> userGroups = currentUserGroups();
        if (hasAnyAllowedGroup(userGroups, rule.getAllowedGroups())) {
            return true;
        }

        // Blocked: send the user to the friendly access-denied page, remembering where they came from.
        String from = request.getParameter("from");
        String target = ACCESS_DENIED_PATH + "?from=" + java.net.URLEncoder.encode(
                (from != null && !from.isBlank()) ? from : path, java.nio.charset.StandardCharsets.UTF_8);
        response.sendRedirect(target);
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> currentUserGroups() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser oidc)) {
            return List.of();
        }
        Object groups = oidc.getClaims().get("groups");
        if (groups instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private boolean hasAnyAllowedGroup(List<String> userGroups, List<String> allowedGroups) {
        for (String allowed : allowedGroups) {
            // Match both "admin" and "/admin" forms (Keycloak can return either).
            if (userGroups.contains(allowed) || userGroups.contains("/" + allowed)) {
                return true;
            }
        }
        return false;
    }
}
