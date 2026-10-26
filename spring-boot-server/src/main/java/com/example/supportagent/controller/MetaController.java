package com.example.supportagent.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supportagent.config.AppConfig;
import com.example.supportagent.model.demo.DemoData;

/**
 * Exposes the client-side boot data (agent identity, initial message, customer
 * details, default articles and the default recommended action) that the
 * original NextJS app kept in {@code config/demoData.ts}.
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final AppConfig appConfig;

    public MetaController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("agentName", appConfig.agentName());
        meta.put("initialMessage", appConfig.initialMessage());
        meta.put("customerDetails", DemoData.CUSTOMER_DETAILS);
        meta.put("defaultArticles", DemoData.DEFAULT_ARTICLES);
        meta.put("defaultAction", appConfig.defaultAction());
        return meta;
    }
}
