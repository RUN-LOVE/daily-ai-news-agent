package com.newsagent.agent.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmChatRequest(
        String model,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools,
        double temperature
) {
}
