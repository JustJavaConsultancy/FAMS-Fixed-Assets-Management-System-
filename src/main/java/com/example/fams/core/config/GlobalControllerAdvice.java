package com.example.fams.core.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ModelAttribute("currentPath")
    public String getCurrentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
 }
