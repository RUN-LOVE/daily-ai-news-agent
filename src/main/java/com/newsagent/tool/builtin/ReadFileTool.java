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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ReadFileTool implements AgentTool {

    private static final int MAX_BYTES = 64 * 1024;
    private static final JsonNode SCHEMA = ToolSchemas.parse("""
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "相对于 workspace 的文件路径"
                }
              },
              "required": ["path"],
              "additionalProperties": false
            }
            """);

    private final PathGuard pathGuard;

    public ReadFileTool(PathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取 workspace 内的 UTF-8 文本文件，最多显示 64KB；路径必须相对于 workspace。";
    }

    @Override
    public JsonNode parametersSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        String requestedPath = JsonArgs.requiredText(arguments, "path");
        Path file = pathGuard.requireFile(requestedPath);

        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            boolean truncated = bytes.length > MAX_BYTES;
            int length = Math.min(bytes.length, MAX_BYTES);

            for (int index = 0; index < length; index++) {
                if (bytes[index] == 0) {
                    return ToolResult.error("不支持二进制文件: " + requestedPath);
                }
            }

            String content = new String(bytes, 0, length, StandardCharsets.UTF_8);
            StringBuilder output = new StringBuilder()
                    .append("文件: ").append(relative(file)).append('\n')
                    .append("---\n")
                    .append(content.isEmpty() ? "(空文件)" : content);
            if (truncated) {
                output.append("\n...[文件内容已截断，最多显示 64KB]");
            }
            return ToolResult.ok(output.toString());
        } catch (IOException exception) {
            throw new ToolException("读取文件失败: " + exception.getMessage());
        }
    }

    private String relative(Path path) {
        return pathGuard.workspace().relativize(path).toString().replace('\\', '/');
    }
}
