package com.example.fams.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String landingPage() {
        return "landing-page";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login-page";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard.html";
    }
    @GetMapping("/assets/details")
    public String assetDetails() {
        return "redirect:/assets";
    }

    @GetMapping("/assets/assignment")
    public String assetAssignment() {
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
    public String reportsModule() {
        return "reports/reports-module";
    }
}

