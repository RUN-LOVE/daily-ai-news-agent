package com.newsagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ToolSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolSchemas() {
    }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工具 schema 不是合法 JSON", exception);
        }
    }
}
