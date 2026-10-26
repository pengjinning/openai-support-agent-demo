package com.example.supportagent.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.supportagent.service.KnowledgeBaseService;

/**
 * Knowledge base endpoints (original {@code /api/kb_search} and
 * {@code /api/list_files}).
 */
@RestController
@RequestMapping("/api")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/kb_search")
    public Map<String, Object> kbSearch(@RequestBody Map<String, Object> body) {
        Object rawQuery = body.get("query");
        String query = rawQuery == null ? "" : String.valueOf(rawQuery);
        List<Map<String, Object>> results = knowledgeBaseService.search(query, 6).stream()
                .map(result -> result.toMap())
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return response;
    }

    @GetMapping("/list_files")
    public List<String> listFiles(@RequestParam("folder") String folder) {
        return knowledgeBaseService.listFilenames(folder);
    }
}
