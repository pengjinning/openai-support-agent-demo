package com.example.supportagent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Thymeleaf pages (original {@code /}, {@code /kb} and {@code /faq}).
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/kb")
    public String kb() {
        return "kb";
    }

    @GetMapping("/faq")
    public String faq() {
        return "faq";
    }
}

