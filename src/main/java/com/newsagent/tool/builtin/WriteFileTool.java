package com.newsagent.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.newsagent.tool.AgentTool;
import com.newsagent.tool.JsonArgs;
import com.newsagent.tool.PathGuard;
import com.newsagent.tool.ToolException;
import com.newsagent.tool.ToolResult;
import com.newsagent.tool.ToolSchemas;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class WriteFileTool implements AgentTool {

    private static final int MAX_CONTENT_CHARS = 100_000;
    private static final JsonNode SCHEMA = ToolSchemas.parse("""
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "相对于 workspace 的文件路径"
                },
                "content": {
                  "type": "string",
                  "description": "要写入的 UTF-8 文本内容"
                }
              },
              "required": ["path", "content"],
              "additionalProperties": false
            }
            """);

    private final PathGuard pathGuard;

    public WriteFileTool(PathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "在 workspace 内写入 UTF-8 文本文件，自动创建父目录；内容最多 100KB。";
    }

    @Override
    public JsonNode parametersSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        String requestedPath = JsonArgs.requiredText(arguments, "path");
        String content = JsonArgs.textAllowBlank(arguments, "content");
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new ToolException("内容过长（最多 " + MAX_CONTENT_CHARS + " 个字符）");
        }

        Path file = pathGuard.prepareForWrite(requestedPath);
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ToolException("写入文件失败: " + exception.getMessage());
        }

        return ToolResult.ok("已写入 " + pathGuard.workspace().relativize(file).toString()
                .replace('\\', '/') + "（" + content.length() + " 个字符）");
    }
}
