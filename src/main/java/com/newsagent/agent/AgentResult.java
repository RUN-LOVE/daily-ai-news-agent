package com.newsagent.agent;

import java.util.List;

public record AgentResult(
        String finalAnswer,
        List<AgentStep> steps,
        AgentStopReason stopReason,
        int llmCalls
) {

    public AgentResult {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
