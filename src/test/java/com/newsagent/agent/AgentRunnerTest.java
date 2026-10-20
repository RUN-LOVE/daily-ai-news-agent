package com.newsagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.agent.llm.LlmChatRequest;
import com.newsagent.agent.llm.LlmChatResponse;
import com.newsagent.agent.llm.LlmClient;
import com.newsagent.agent.llm.LlmProperties;
import com.newsagent.agent.llm.LlmToolCall;
import com.newsagent.config.AppProperties;
import com.newsagent.tool.AgentTool;
import com.newsagent.tool.CommandGuard;
import com.newsagent.tool.PathGuard;
import com.newsagent.tool.ToolRegistry;
import com.newsagent.tool.builtin.BashTool;
import com.newsagent.tool.builtin.ListDirTool;
import com.newsagent.tool.builtin.ReadFileTool;
import com.newsagent.tool.builtin.SearchContentTool;
import com.newsagent.tool.builtin.WriteFileTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunnerTest {

    @TempDir
    Path workspace;

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(workspace.resolve("notes.txt"), "hello agent");
        PathGuard pathGuard = new PathGuard(new AppProperties(workspace.toString(), "unused"));
        List<AgentTool> tools = List.of(
                new ListDirTool(pathGuard),
                new ReadFileTool(pathGuard),
                new SearchContentTool(pathGuard),
                new WriteFileTool(pathGuard),
                new BashTool(pathGuard, new CommandGuard())
        );
        toolRegistry = new ToolRegistry(tools, new ObjectMapper());
    }

    @Test
    void followsToolOrderChosenByModelScenarioA() {
        ScriptedLlmClient client = new ScriptedLlmClient(true,
                toolCall("call_1", "list_dir", "{\"path\":\".\"}"),
                toolCall("call_2", "read_file", "{\"path\":\"notes.txt\"}"),
                finalAnswer("完成"));

        AgentResult result = runner(client, 6).run("查看目录并读取 notes.txt");

        assertEquals(AgentStopReason.COMPLETED, result.stopReason());
        assertEquals(3, result.llmCalls());
        assertEquals(List.of("list_dir", "read_file"),
                result.steps().stream().map(AgentStep::toolName).toList());
        assertTrue(result.steps().get(1).toolOutput().contains("hello agent"));
        assertTrue(client.requests.get(1).messages().stream()
                .anyMatch(message -> "tool".equals(message.role())
                        && "call_1".equals(message.toolCallId())));
        assertEquals(5, client.requests.get(0).tools().size());
    }

    @Test
    void followsToolOrderChosenByModelScenarioB() throws IOException {
        ScriptedLlmClient client = new ScriptedLlmClient(true,
                toolCall("call_1", "search_content", "{\"keyword\":\"hello\",\"dir\":\".\"}"),
                toolCall("call_2", "write_file",
                        "{\"path\":\"reports/summary.md\",\"content\":\"摘要\"}"),
                finalAnswer("完成"));

        AgentResult result = runner(client, 6).run("搜索 hello 并把摘要写入文件");

        assertEquals(List.of("search_content", "write_file"),
                result.steps().stream().map(AgentStep::toolName).toList());
        assertEquals("摘要", Files.readString(workspace.resolve("reports/summary.md")));
        assertEquals(AgentStopReason.COMPLETED, result.stopReason());
    }

    @Test
    void stopsAtMaximumSteps() {
        ScriptedLlmClient client = new ScriptedLlmClient(true,
                toolCall("call_1", "list_dir", "{\"path\":\".\"}"),
                toolCall("call_2", "list_dir", "{\"path\":\".\"}"),
                finalAnswer("不应执行到这里"));

        AgentResult result = runner(client, 2).run("一直查看目录");

        assertEquals(AgentStopReason.MAX_STEPS, result.stopReason());
        assertEquals(2, result.llmCalls());
        assertEquals(2, result.steps().size());
    }

    @Test
    void feedsToolErrorBackToModel() {
        ScriptedLlmClient client = new ScriptedLlmClient(true,
                toolCall("call_1", "read_file", "{\"path\":\"missing.txt\"}"),
                finalAnswer("已根据错误调整"));

        AgentResult result = runner(client, 6).run("读取文件");

        assertFalse(result.steps().get(0).success());
        assertTrue(result.steps().get(0).toolOutput().contains("文件不存在"));
        assertEquals("已根据错误调整", result.finalAnswer());
        assertTrue(client.requests.get(1).messages().stream()
                .anyMatch(message -> "tool".equals(message.role())
                        && message.content().contains("工具执行失败")));
    }

    @Test
    void rejectsRunWhenLlmIsNotConfigured() {
        ScriptedLlmClient client = new ScriptedLlmClient(false);

        assertThrows(AgentException.class, () -> runner(client, 6).run("任务"));
    }

    private AgentRunner runner(LlmClient client, int maxSteps) {
        LlmProperties properties = new LlmProperties(
                "https://example.com/v1", "test-key", "test-model", 30, maxSteps);
        return new AgentRunner(client, toolRegistry, properties);
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

    private static final class ScriptedLlmClient implements LlmClient {

        private final boolean configured;
        private final Deque<LlmChatResponse> responses = new ArrayDeque<>();
        private final List<LlmChatRequest> requests = new ArrayList<>();

        private ScriptedLlmClient(boolean configured, LlmChatResponse... responses) {
            this.configured = configured;
            this.responses.addAll(List.of(responses));
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new IllegalStateException("没有可用的脚本响应");
            }
            return responses.removeFirst();
        }
    }
}
