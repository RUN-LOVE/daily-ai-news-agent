package com.newsagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.config.AppProperties;
import com.newsagent.news.NewsFetchException;
import com.newsagent.news.NewsFetcher;
import com.newsagent.news.NewsItem;
import com.newsagent.source.SourceCatalog;
import com.newsagent.tool.builtin.FetchNewsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchNewsToolTest {

    @TempDir
    Path workspace;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ToolRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        Path sources = workspace.resolve("sources");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("feeds.json"), """
                [
                  {"id":"good","name":"Good Feed","url":"https://example.com/rss","type":"rss","language":"en","focus":"AI"},
                  {"id":"bad","name":"Bad Feed","url":"https://example.org/rss","type":"rss","language":"en","focus":"AI"}
                ]
                """);
        SourceCatalog catalog = new SourceCatalog(
                new AppProperties(workspace.toString(), "unused"), objectMapper);
        NewsFetcher fetcher = (source, limit) -> {
            if ("bad".equals(source.id())) {
                throw new NewsFetchException("连接超时");
            }
            return List.of(
                    new NewsItem(source.id(), source.name(), "Agent 新进展",
                            "https://example.com/a", "2026-10-21T08:00:00Z", "关于 Agent 的摘要"),
                    new NewsItem(source.id(), source.name(), "其他新闻",
                            "https://example.com/b", "2026-10-21T07:00:00Z", "其他摘要")
            );
        };
        registry = new ToolRegistry(List.of(
                new FetchNewsTool(catalog, fetcher, objectMapper)), objectMapper);
    }

    @Test
    void fetchesAndFiltersItems() throws Exception {
        ToolResult result = execute("{\"source_ids\":[\"good\"],\"keyword\":\"agent\",\"limit\":5}");

        assertTrue(result.success(), result.output());
        JsonNode output = objectMapper.readTree(result.output());
        assertEquals(1, output.path("total").asInt());
        assertEquals("Agent 新进展", output.path("items").get(0).path("title").asText());
        assertEquals("good", output.path("items").get(0).path("source_id").asText());
    }

    @Test
    void keepsGoodSourcesWhenAnotherFails() throws Exception {
        ToolResult result = execute("{\"source_ids\":[\"bad\",\"good\"],\"limit\":5}");

        assertTrue(result.success(), result.output());
        JsonNode output = objectMapper.readTree(result.output());
        assertEquals(2, output.path("total").asInt());
        assertTrue(output.path("errors").get(0).asText().contains("连接超时"));
    }

    @Test
    void failsWhenAllSourcesFail() throws Exception {
        ToolResult result = execute("{\"source_ids\":[\"bad\"]}");

        assertFalse(result.success());
        assertTrue(result.output().contains("连接超时"));
    }

    @Test
    void rejectsUnknownSource() throws Exception {
        ToolResult result = execute("{\"source_ids\":[\"missing\"]}");

        assertFalse(result.success());
        assertTrue(result.output().contains("未知新闻源"));
    }

    @Test
    void rejectsTooManySources() throws Exception {
        ToolResult result = execute(
                "{\"source_ids\":[\"s1\",\"s2\",\"s3\",\"s4\",\"s5\",\"s6\",\"s7\"]}");

        assertFalse(result.success());
        assertTrue(result.output().contains("最多抓取"));
    }

    @Test
    void rejectsInvalidSourceList() throws Exception {
        ToolResult result = execute("{\"source_ids\":\"good\"}");

        assertFalse(result.success());
        assertTrue(result.output().contains("source_ids"));
    }

    private ToolResult execute(String arguments) {
        return registry.execute("fetch_news", arguments);
    }
}
