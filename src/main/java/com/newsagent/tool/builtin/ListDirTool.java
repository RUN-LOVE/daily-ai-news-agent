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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class ListDirTool implements AgentTool {

    private static final int MAX_ENTRIES = 200;
    private static final JsonNode SCHEMA = ToolSchemas.parse("""
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "相对于 workspace 的目录路径，例如 . 或 sources"
                }
              },
              "required": ["path"],
              "additionalProperties": false
            }
            """);

    private final PathGuard pathGuard;

    public ListDirTool(PathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    @Override
    public String name() {
        return "list_dir";
    }

    @Override
    public String description() {
        return "列出 workspace 内指定目录的直接子项（不递归），路径必须相对于 workspace。";
    }

    @Override
    public JsonNode parametersSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        String requestedPath = JsonArgs.optionalText(arguments, "path", ".");
        Path directory = pathGuard.requireDirectory(requestedPath);

        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> entries = stream
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();

            String relativeDirectory = relative(directory);
            if (entries.isEmpty()) {
                return ToolResult.ok("目录为空: " + relativeDirectory);
            }

            StringBuilder output = new StringBuilder("目录: ").append(relativeDirectory).append('\n');
            int shown = Math.min(entries.size(), MAX_ENTRIES);
            for (int index = 0; index < shown; index++) {
                Path entry = entries.get(index);
                output.append(Files.isDirectory(entry) ? "[DIR]  " : "[FILE] ")
                        .append(relative(entry));
                if (Files.isRegularFile(entry)) {
                    try {
                        output.append(" (").append(Files.size(entry)).append(" bytes)");
                    } catch (IOException ignored) {
                        // Size is optional metadata; ignore unreadable entries.
                    }
                }
                output.append('\n');
            }
            if (entries.size() > MAX_ENTRIES) {
                output.append("... 其余 ").append(entries.size() - MAX_ENTRIES).append(" 项已省略\n");
            }
            return ToolResult.ok(output.toString());
        } catch (IOException exception) {
            throw new ToolException("读取目录失败: " + exception.getMessage());
        }
    }

    private String relative(Path path) {
        String value = pathGuard.workspace().relativize(path).toString().replace('\\', '/');
        return value.isEmpty() ? "." : value;
    }
}
