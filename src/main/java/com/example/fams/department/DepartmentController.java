package com.example.fams.department;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DepartmentController {
    @GetMapping("/department-head/dashboard")
    public String departmentHeadDashboard() {
        return "department-head/dashboard";
    }
}
