package com.newsagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {

    String name();

    String description();

    JsonNode parametersSchema();

    ToolResult execute(JsonNode arguments);
}
