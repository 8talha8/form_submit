package com.example.seleniumdemo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** Serves the Thymeleaf page shells. */
@Controller
public class UiController {

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

    @PostMapping("/sample-form")
    public String sampleFormSubmit(Model model) {
        model.addAttribute("submitted", true);
        return "sample-form";
    }
}
