package com.newsagent.briefing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.agent.llm.LlmChatRequest;
import com.newsagent.agent.llm.LlmChatResponse;
import com.newsagent.agent.llm.LlmClient;
import com.newsagent.agent.llm.LlmToolCall;
import com.newsagent.news.NewsFetcher;
import com.newsagent.news.NewsItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.db-path=target/test-data/briefing-api.db",
        "app.workspace=target/test-workspace",
        "app.scheduler-enabled=false"
})
@AutoConfigureMockMvc
class BriefingApiIntegrationTest {

    private static final Path WORKSPACE = Path.of("target/test-workspace").toAbsolutePath().normalize();
    private static final String DATE = LocalDate.now().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BriefingService briefingService;

    @MockitoBean
    private LlmClient llmClient;

    @MockitoBean
    private NewsFetcher newsFetcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        reset(llmClient, newsFetcher);
        jdbcTemplate.update("DELETE FROM briefing");
        jdbcTemplate.update("DELETE FROM profile");

        Files.createDirectories(WORKSPACE.resolve("sources"));
        Files.writeString(WORKSPACE.resolve("sources/feeds.json"), """
                [
                  {"id":"test-feed","name":"测试源","url":"https://example.com/rss",
                   "type":"rss","language":"zh","focus":"AI"}
                ]
                """);
        Files.createDirectories(WORKSPACE.resolve("reports"));
        Files.deleteIfExists(WORKSPACE.resolve("reports").resolve(DATE + ".md"));

        when(llmClient.isConfigured()).thenReturn(true);
        when(newsFetcher.fetch(any(), anyInt())).thenReturn(List.of(new NewsItem(
                "test-feed", "测试源", "Agent 新进展",
                "https://example.com/a", "2026-10-21T08:00:00Z", "这是一条测试摘要")));
        when(llmClient.chat(any(LlmChatRequest.class))).thenReturn(
                toolCall("call_1", "fetch_news",
                        "{\"source_ids\":[\"test-feed\"],\"limit\":5}"),
                toolCall("call_2", "write_file", writeArguments()),
                finalAnswer("已生成 1 条简报"));
    }

    @Test
    void runsAgentAndPersistsBriefingWithTrace() throws Exception {
        mockMvc.perform(post("/api/agent/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DATE))
                .andExpect(jsonPath("$.content", containsString("测试新闻")))
                .andExpect(jsonPath("$.filePath").value("reports/" + DATE + ".md"))
                .andExpect(jsonPath("$.stopReason").value("COMPLETED"))
                .andExpect(jsonPath("$.llmCalls").value(3))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].toolName").value("fetch_news"))
                .andExpect(jsonPath("$.steps[0].success").value(true))
                .andExpect(jsonPath("$.steps[1].toolName").value("write_file"));

        mockMvc.perform(get("/api/briefings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value(DATE));

        mockMvc.perform(get("/api/briefings/" + DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", containsString("AI 日报")))
                .andExpect(jsonPath("$.steps", hasSize(2)));

        verify(llmClient, times(3)).chat(any(LlmChatRequest.class));
        verify(newsFetcher, times(1)).fetch(any(), anyInt());

        Briefing repeated = briefingService.generate(LocalDate.now(), false);
        assertEquals("reports/" + DATE + ".md", repeated.filePath());
        verify(llmClient, times(3)).chat(any(LlmChatRequest.class));
    }

    @Test
    void returnsServiceUnavailableWhenLlmIsNotConfigured() throws Exception {
        when(llmClient.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/agent/run"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", containsString("LLM 未配置")));
    }

    @Test
    void returnsNotFoundForUnknownBriefing() throws Exception {
        mockMvc.perform(get("/api/briefings/2020-01-01"))
                .andExpect(status().isNotFound());
    }

    private String writeArguments() throws IOException {
        String content = "# AI 每日简报 · " + DATE + "\n\n## 测试新闻\n\n- 摘要";
        return objectMapper.writeValueAsString(Map.of(
                "path", "reports/" + DATE + ".md",
                "content", content));
    }

    private static LlmChatResponse toolCall(String id, String name, String arguments) {
        return new LlmChatResponse(List.of(new LlmChatResponse.Choice(
                new LlmChatResponse.LlmAssistantMessage(
                        "assistant",
                        "先调用工具",
                        List.of(LlmToolCall.of(id, name, arguments))),
                "tool_calls")));
    }

    private static LlmChatResponse finalAnswer(String content) {
        return new LlmChatResponse(List.of(new LlmChatResponse.Choice(
                new LlmChatResponse.LlmAssistantMessage("assistant", content, null),
                "stop")));
    }
}
