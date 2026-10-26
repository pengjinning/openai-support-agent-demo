package com.example.supportagent.util;

import tools.jackson.databind.ObjectMapper;

/**
 * Small JSON helper backed by Jackson 3 so we can serialize SSE events and tool
 * results without depending on Spring's auto-configured ObjectMapper bean.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
