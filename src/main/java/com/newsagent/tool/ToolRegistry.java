package com.newsagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.agent.llm.LlmToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class ToolRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> toolList, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, AgentTool> indexed = new TreeMap<>();
        for (AgentTool tool : toolList) {
            AgentTool previous = indexed.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("重复的工具名: " + tool.name());
            }
        }
        this.tools = Collections.unmodifiableMap(indexed);
    }

    public List<LlmToolDefinition> definitions() {
        return tools.values().stream()
                .map(tool -> LlmToolDefinition.of(
                        tool.name(), tool.description(), tool.parametersSchema()))
                .toList();
    }

    public Set<String> names() {
        return tools.keySet();
    }

    public ToolResult execute(String name, String argumentsJson) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.error("未知工具: " + name);
        }

        JsonNode arguments;
        try {
            String payload = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
            arguments = objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            return ToolResult.error("工具参数不是合法 JSON: " + exception.getOriginalMessage());
        }

        if (arguments == null || !arguments.isObject()) {
            return ToolResult.error("工具参数必须是 JSON 对象");
        }

        try {
            return tool.execute(arguments);
        } catch (ToolException exception) {
            return ToolResult.error(exception.getMessage());
        } catch (RuntimeException exception) {
            return ToolResult.error("工具执行失败: " + exception.getMessage());
        }
    }
}
