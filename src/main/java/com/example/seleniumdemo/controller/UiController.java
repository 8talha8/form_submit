package com.example.seleniumdemo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serves the Thymeleaf page shells. */
@Controller

public class UiController {

    private static final Logger log = LoggerFactory.getLogger(UiController.class);

    @GetMapping({"/", "/login"})
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    /**
     * Local sample form used to test the Selenium flow end-to-end without
     * needing an external website. Its element locators match mapping.csv.
     */
    @GetMapping("/sample-form")
    public String sampleForm() {
        return "sample-form";
    }

    @GetMapping("/sample-form-login")
    public String sampleFormLogin() {
        log.info("Serving sample form login page");
        return "sample-form-login";
    }

    @PostMapping("/sample-form")
    public String sampleFormSubmit(Model model) {
        log.info("Sample form submitted");
        model.addAttribute("submitted", true);
        return "sample-form";
    }
}
