package com.newsagent.agent;

import com.newsagent.agent.llm.LlmChatRequest;
import com.newsagent.agent.llm.LlmChatResponse;
import com.newsagent.agent.llm.LlmClient;
import com.newsagent.agent.llm.LlmMessage;
import com.newsagent.agent.llm.LlmProperties;
import com.newsagent.agent.llm.LlmToolCall;
import com.newsagent.tool.ToolRegistry;
import com.newsagent.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentRunner {

    private static final String SYSTEM_PROMPT = """
            你是“每日 AI 新闻助手”的 Agent 核心，负责在本地 workspace 中完成用户交给你的任务。
            你可以调用工具查看目录、读取和搜索文件、写入文件，或执行受限命令。

            工作规则：
            1. 工具调用顺序和调用次数由你根据当前任务自行决定，不存在固定流程。
            2. 只在需要信息或需要产出文件时调用工具；能直接回答时直接回答。
            3. 所有路径都相对于 workspace，禁止使用绝对路径和 ..
            4. 工具失败时阅读错误信息并调整，不要编造工具结果。
            5. 任务完成后用简洁中文给出最终回答，不要输出 JSON 包装或额外前后缀。
            """;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final LlmProperties properties;

    public AgentRunner(LlmClient llmClient, ToolRegistry toolRegistry, LlmProperties properties) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public AgentResult run(String task) {
        if (task == null || task.isBlank()) {
            throw new AgentException("任务不能为空");
        }
        if (!llmClient.isConfigured()) {
            throw new AgentException("LLM 未配置，请设置 LLM_BASE_URL、LLM_API_KEY、LLM_MODEL");
        }

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(SYSTEM_PROMPT));
        messages.add(LlmMessage.user("当前日期：" + LocalDate.now() + "\n任务：" + task.trim()));

        List<AgentStep> steps = new ArrayList<>();
        int llmCalls = 0;
        int traceIndex = 1;
        String lastAssistantContent = null;

        for (int round = 1; round <= properties.maxSteps(); round++) {
            llmCalls++;
            LlmChatResponse response = llmClient.chat(new LlmChatRequest(
                    properties.model(),
                    List.copyOf(messages),
                    toolRegistry.definitions(),
                    0.2
            ));
            LlmChatResponse.LlmAssistantMessage assistant = response.firstMessage();
            List<LlmToolCall> toolCalls = assistant.toolCalls();

            if (toolCalls == null || toolCalls.isEmpty()) {
                String answer = assistant.content() == null ? "" : assistant.content().trim();
                if (answer.isEmpty()) {
                    throw new AgentException("模型没有返回最终回答");
                }
                return new AgentResult(answer, steps, AgentStopReason.COMPLETED, llmCalls);
            }

            lastAssistantContent = assistant.content();
            messages.add(LlmMessage.assistant(assistant.content(), toolCalls));

            for (LlmToolCall toolCall : toolCalls) {
                AgentStep step = executeToolCall(traceIndex++, assistant.content(), toolCall);
                steps.add(step);
                String toolMessage = step.success()
                        ? step.toolOutput()
                        : "工具执行失败：" + step.toolOutput();
                messages.add(LlmMessage.tool(toolCall.id(), toolMessage));
            }
        }

        String answer = lastAssistantContent == null || lastAssistantContent.isBlank()
                ? "已达到最大执行步数（" + properties.maxSteps() + "），任务未完成。"
                : lastAssistantContent.trim();
        return new AgentResult(answer, steps, AgentStopReason.MAX_STEPS, llmCalls);
    }

    private AgentStep executeToolCall(int index, String assistantContent, LlmToolCall toolCall) {
        if (toolCall == null || toolCall.id() == null || toolCall.id().isBlank()
                || toolCall.function() == null) {
            throw new AgentException("模型返回了无效的工具调用");
        }

        String name = toolCall.function().name() == null ? "" : toolCall.function().name();
        String arguments = toolCall.function().arguments() == null
                ? "{}" : toolCall.function().arguments();
        long startedAt = System.nanoTime();
        ToolResult result = toolRegistry.execute(name, arguments);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new AgentStep(index, assistantContent, name, arguments,
                result.output(), result.success(), durationMs);
    }
}
