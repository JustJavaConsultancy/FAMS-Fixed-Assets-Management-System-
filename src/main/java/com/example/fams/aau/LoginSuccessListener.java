package com.example.fams.aau;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;

/**
 * Logs (System.out) the group(s) of the user that just logged in via Keycloak/OIDC.
 * Triggered once per interactive login (not on every authenticated request).
 */
@Component
public class LoginSuccessListener {

    @EventListener
    public void onLoginSuccess(InteractiveAuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof DefaultOidcUser oidcUser) {
            Object group = oidcUser.getClaims().get("groups");
            String name = (String) oidcUser.getClaims().get("name");
            System.out.println("=== User logged in: " + name + " | group(s): " + group + " ===");
        } else {
            System.out.println("=== User logged in (non-OIDC principal): " + principal + " ===");
        }
    }
}
