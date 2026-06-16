package com.example.fams.employee;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmployeeController {
    @GetMapping("/employee/dashboard")
    public String employeeDashboard() {
        return "employee/dashboard";
    }
}
