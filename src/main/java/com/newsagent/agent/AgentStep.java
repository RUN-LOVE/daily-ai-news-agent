package com.newsagent.agent;

public record AgentStep(
        int index,
        String assistantContent,
        String toolName,
        String toolArguments,
        String toolOutput,
        boolean success,
        long durationMs
) {
}
