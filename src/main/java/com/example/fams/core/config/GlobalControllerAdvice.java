package com.example.fams.core.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ModelAttribute("currentPath")
    public String getCurrentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("currentUserUsername")
    public String currentUserUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "guest";
        return auth.getName() != null ? auth.getName() : "guest";
    }

    @ModelAttribute("currentUserDisplayName")
    public String currentUserDisplayName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "Guest";
        Object principal = auth.getPrincipal();
        if (principal instanceof DefaultOidcUser oidc) {
            Object name = oidc.getClaims().get("name");
            if (name instanceof String && !((String) name).isBlank()) return (String) name;
            Object pref = oidc.getClaims().get("preferred_username");
            if (pref instanceof String && !((String) pref).isBlank()) return (String) pref;
        }
        return auth.getName() != null ? auth.getName() : "Guest";
    }

    @ModelAttribute("currentUserInitials")
    public String currentUserInitials() {
        String display = currentUserDisplayName();
        if (display != null && !display.isBlank()) {
            String[] parts = display.trim().split("\\s+");
            if (parts.length >= 2) {
                return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
            }
            if (parts[0].length() >= 2) return parts[0].substring(0, 2).toUpperCase();
            return parts[0].substring(0, 1).toUpperCase();
        }
        String uname = currentUserUsername();
        if (uname != null && !uname.isBlank()) {
            String clean = uname.replaceAll("[^A-Za-z]", "");
            if (clean.length() >= 2) return clean.substring(0, 2).toUpperCase();
            if (clean.length() == 1) return clean.substring(0, 1).toUpperCase();
        }
        return "?";
    }

}
