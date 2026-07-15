package com.example.fams.core.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's annotation-driven caching.
 *
 * <p>The actual cache manager (Caffeine) and its TTL are configured in
 * {@code application.yml} under {@code spring.cache.*}. The TTL is bound to
 * {@code fams.kpi.cache-ttl-seconds} (default 60s), which defines the SLA window
 * for dashboard KPIs and cached reports: cached values are never older than the
 * TTL and are refreshed within it.
 *
 * <p>Caches in use:
 * <ul>
 *     <li>{@code dashboardKpis} — global dashboard KPI payload (admin / asset-manager view)</li>
 *     <li>{@code reports} — role-scoped report results keyed by report + scope + filters</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
