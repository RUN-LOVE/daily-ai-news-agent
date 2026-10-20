package com.newsagent.agent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmChatResponse(List<Choice> choices) {

    public LlmAssistantMessage firstMessage() {
        if (choices == null || choices.isEmpty() || choices.get(0) == null || choices.get(0).message() == null) {
            throw new LlmException("LLM 返回中没有消息");
        }
        return choices.get(0).message();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            LlmAssistantMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmAssistantMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<LlmToolCall> toolCalls
    ) {
    }
}
