package com.newsagent.agent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<LlmToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null);
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls, null);
    }

    public static LlmMessage tool(String toolCallId, String content) {
        return new LlmMessage("tool", content, null, toolCallId);
    }
}
