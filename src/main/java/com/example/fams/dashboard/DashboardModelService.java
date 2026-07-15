package com.example.fams.dashboard;

import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

/**
 * Thin adapter that populates a Spring MVC {@link Model} with the dashboard KPI
 * snapshot produced (and cached) by {@link DashboardKpiService}.
 *
 * <p>The heavy KPI computation lives in {@link DashboardKpiService} so that Spring's
 * caching proxy applies (KPIs refresh within the configured SLA window rather than being
 * recomputed on every request). Keeping this as a separate bean also means the existing
 * controllers ({@code PageController}, {@code AdminController}) need no changes.
 */
@Service
public class DashboardModelService {

    private final DashboardKpiService dashboardKpiService;

    public DashboardModelService(DashboardKpiService dashboardKpiService) {
        this.dashboardKpiService = dashboardKpiService;
    }

    public void addDashboardModel(Model model) {
        model.addAllAttributes(dashboardKpiService.getGlobalDashboard());
    }
}
