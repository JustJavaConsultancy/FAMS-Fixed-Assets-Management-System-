package com.example.fams.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Slf4j
@Configuration
public class Oauth2SecurityConfig implements WebMvcConfigurer {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Bean
    protected SecurityFilterChain configure(HttpSecurity http, HandlerMappingIntrospector introspector,ClientRegistrationRepository repo) throws Exception {
        log.debug("Configuring security");

        http.securityMatcher("/**")
                .anonymous(AnonymousConfigurer::disable)
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer
                                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                )
                .csrf(CsrfConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/oauth2/authorization/keycloak")
                        .authorizationEndpoint(Customizer.withDefaults())
                        .tokenEndpoint(Customizer.withDefaults())
                        .userInfoEndpoint(Customizer.withDefaults())
                        .successHandler(authenticationSuccessHandler())
                        )
                .authorizeHttpRequests(
                        authorize -> {
                           authorize.requestMatchers(new AntPathRequestMatcher("/")).permitAll();
                            authorize.anyRequest().authenticated();
                        }
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(repo))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutUrl("/users/logout")
                );
        return http.build();
    }



    private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository repository) {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(repository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return logoutSuccessHandler;
    }

    /**
     * Registers the URL-level role authorization guard. It runs on every dispatched
     * request and redirects users who try to open a page their Keycloak groups don't
     * permit to the access-denied page.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        HandlerInterceptor guard = new RoleAuthorizationInterceptor();
        registry.addInterceptor(guard).addPathPatterns("/**");
    }

    /**
     * Custom authentication success handler that routes users to the appropriate
     * dashboard based on their group membership in Keycloak.
     *
     * Routes:
     * - admin group -> /admin/dashboard
     * - auditor group -> /auditor/dashboard
     * - assetManager group -> /admin/dashboard
     * - departmentHead group -> /department-head/dashboard
     * - employees group -> /employee/dashboard
     * - fallback -> /dashboard
     */
    private AuthenticationSuccessHandler authenticationSuccessHandler(){
        return  (request, response, authentication) -> {
                String redirectUrl = authenticationManager.getDefaultDashboardUrl();
                log.info("Redirecting authenticated user to: {}", redirectUrl);
                response.sendRedirect(redirectUrl);
        };
    }
}
