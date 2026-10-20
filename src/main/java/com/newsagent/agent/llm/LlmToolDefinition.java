package com.newsagent.agent.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmToolDefinition(String type, Function function) {

    public static LlmToolDefinition of(String name, String description, JsonNode parameters) {
        return new LlmToolDefinition("function", new Function(name, description, parameters));
    }

    public record Function(String name, String description, JsonNode parameters) {
    }
}
