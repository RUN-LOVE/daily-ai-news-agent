package com.newsagent.agent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        Integer timeoutSeconds,
        Integer maxSteps
) {

    public LlmProperties {
        baseUrl = trim(baseUrl);
        apiKey = trim(apiKey);
        model = trim(model);
        timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 180 : timeoutSeconds;
        maxSteps = maxSteps == null || maxSteps <= 0 ? 12 : Math.min(maxSteps, 30);
    }

    public boolean configured() {
        return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
