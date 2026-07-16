package com.example.fams.controller;

import com.example.fams.dashboard.DashboardModelService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final DashboardModelService dashboardModelService;

    public PageController(DashboardModelService dashboardModelService) {
        this.dashboardModelService = dashboardModelService;
    }

    @GetMapping("/")
    public String landingPage() {
        return "landing-page";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login-page";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        dashboardModelService.addDashboardModel(model);
        return "dashboard.html";
    }

    @GetMapping("/assets/details")
    public String assetDetails() {
        return "redirect:/assets";
    }

    @GetMapping("/assets/transfer")
    public String assetTransfer() {
        return "redirect:/assets";
    }

    @GetMapping("/assets/disposal")
    public String assetDisposal() {
        return "redirect:/assets";
    }

    @GetMapping("/depreciation")
    public String depreciationManagement() {
        return "assets/depreciation-management";
    }

    @GetMapping("/depreciation/configure")
    public String depreciationConfigure() {
        return "assets/depreciation-configure";
    }

    @GetMapping("/depreciation/history")
    public String depreciationHistory() {
        return "assets/depreciation-history";
    }

    @GetMapping("/depreciation/run")
    public String depreciationRun() {
        return "assets/depreciation-run";
    }

    @GetMapping("/notifications")
    public String notificationCenter() {
        return "notifications/notification-center";
    }

    @GetMapping("/reports")
    public String reportsModule(Model model) {
        // Add initial data for reports page
        model.addAttribute("pageTitle", "Reports & Analytics");
        model.addAttribute("defaultReport", "assetRegister");
        return "reports/reports-module";
    }
}
