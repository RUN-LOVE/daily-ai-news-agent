package com.newsagent.briefing;

import com.newsagent.agent.AgentStep;

import java.util.List;

public record Briefing(
        String date,
        String title,
        String content,
        String filePath,
        List<AgentStep> steps,
        int llmCalls,
        String stopReason,
        String createdAt
) {
}
