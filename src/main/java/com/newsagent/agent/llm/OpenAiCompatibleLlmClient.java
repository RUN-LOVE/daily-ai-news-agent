package com.newsagent.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return properties.configured();
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        if (!isConfigured()) {
            throw new LlmException("LLM 未配置，请设置 LLM_BASE_URL、LLM_API_KEY、LLM_MODEL");
        }

        long startedAt = System.nanoTime();
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(endpoint())
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("User-Agent", "daily-ai-news-agent")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException("LLM 请求失败（HTTP " + response.statusCode() + "）："
                        + abbreviate(response.body()));
            }

            LlmChatResponse parsed = objectMapper.readValue(response.body(), LlmChatResponse.class);
            parsed.firstMessage();
            log.info("LLM 调用完成: model={}, status={}, elapsedMs={}",
                    properties.model(), response.statusCode(), elapsedMs);
            return parsed;
        } catch (LlmException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 请求被中断", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new LlmException("LLM 请求失败：" + exception.getMessage(), exception);
        }
    }

    private URI endpoint() {
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        String url = baseUrl.endsWith("/chat/completions")
                ? baseUrl
                : baseUrl + "/chat/completions";
        return URI.create(url);
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "(空响应)";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= MAX_ERROR_BODY_LENGTH
                ? singleLine
                : singleLine.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
