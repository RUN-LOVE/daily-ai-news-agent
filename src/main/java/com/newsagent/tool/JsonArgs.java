package com.newsagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public final class JsonArgs {

    private JsonArgs() {
    }

    public static String requiredText(JsonNode arguments, String field) {
        JsonNode node = arguments.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new ToolException("缺少参数: " + field);
        }
        return node.asText();
    }

    public static String textAllowBlank(JsonNode arguments, String field) {
        JsonNode node = arguments.get(field);
        if (node == null || !node.isTextual()) {
            throw new ToolException("缺少参数: " + field);
        }
        return node.asText();
    }

    public static String optionalText(JsonNode arguments, String field, String fallback) {
        JsonNode node = arguments.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return fallback;
        }
        return node.asText();
    }
}
