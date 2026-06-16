package com.example.fams.auditor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditorController {
    @GetMapping("/auditor/dashboard")
    public String auditorDashboard() {
        return "auditor/dashboard";
    }
}
