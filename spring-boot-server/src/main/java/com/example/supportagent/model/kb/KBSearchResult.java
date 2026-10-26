package com.example.supportagent.model.kb;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single knowledge base search result, matching the original
 * {@code KBSearchResult} shape returned by {@code /api/kb_search}.
 */
public record KBSearchResult(String text, double score, String type, String filename, String filepath) {

    public Map<String, Object> toMap() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("type", type);
        attributes.put("filename", filename);
        attributes.put("filepath", filepath);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("score", score);
        result.put("attributes", attributes);
        return result;
    }
}
