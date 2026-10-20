package com.newsagent.agent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmToolCall(String id, String type, Function function) {

    public static LlmToolCall of(String id, String name, String arguments) {
        return new LlmToolCall(id, "function", new Function(name, arguments));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Function(String name, String arguments) {
    }
}
