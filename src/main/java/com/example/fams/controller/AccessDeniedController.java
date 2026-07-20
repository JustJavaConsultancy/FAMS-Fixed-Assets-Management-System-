package com.example.fams.controller;

import com.example.fams.core.config.AuthenticationManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AccessDeniedController {

    private final AuthenticationManager authenticationManager;

    public AccessDeniedController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/access-denied")
    public String accessDenied(@RequestParam(required = false) String from, Model model) {
        model.addAttribute("fromPath", (from != null && !from.isBlank()) ? from : "");
        model.addAttribute("homeUrl", authenticationManager.getDefaultDashboardUrl());
        return "access-denied";
    }
}
