package com.newsagent.briefing;

public record BriefingSummary(
        String date,
        String title,
        int llmCalls,
        String stopReason,
        String createdAt
) {
}
