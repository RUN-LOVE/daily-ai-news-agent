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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class SearchContentTool implements AgentTool {

    private static final int MAX_FILES = 500;
    private static final int MAX_MATCHES = 100;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_LINE_LENGTH = 300;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final JsonNode SCHEMA = ToolSchemas.parse("""
            {
              "type": "object",
              "properties": {
                "keyword": {
                  "type": "string",
                  "description": "要搜索的关键词（不区分大小写）"
                },
                "dir": {
                  "type": "string",
                  "description": "相对于 workspace 的目录路径，默认为 ."
                }
              },
              "required": ["keyword"],
              "additionalProperties": false
            }
            """);

    private final PathGuard pathGuard;

    public SearchContentTool(PathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    @Override
    public String name() {
        return "search_content";
    }

    @Override
    public String description() {
        return "在 workspace 内递归搜索包含关键词的文本行，返回相对路径、行号和内容；最多返回 100 条。";
    }

    @Override
    public JsonNode parametersSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        String keyword = JsonArgs.requiredText(arguments, "keyword").trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new ToolException("关键词过长（最多 " + MAX_KEYWORD_LENGTH + " 个字符）");
        }
        String requestedDirectory = JsonArgs.optionalText(arguments, "dir", ".");
        Path directory = pathGuard.requireDirectory(requestedDirectory);

        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory, MAX_DEPTH)) {
            files = stream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .limit(MAX_FILES)
                    .toList();
        } catch (IOException exception) {
            throw new ToolException("遍历目录失败: " + exception.getMessage());
        } catch (UncheckedIOException exception) {
            throw new ToolException("遍历目录失败: " + exception.getMessage());
        }

        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        StringBuilder matches = new StringBuilder();
        int matchCount = 0;

        for (Path file : files) {
            if (matchCount >= MAX_MATCHES) {
                break;
            }
            try {
                if (Files.size(file) > MAX_FILE_BYTES) {
                    continue;
                }
                try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                    Iterator<String> iterator = lines.iterator();
                    int lineNumber = 0;
                    while (iterator.hasNext() && matchCount < MAX_MATCHES) {
                        String line = iterator.next();
                        lineNumber++;
                        if (!line.toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
                            continue;
                        }
                        String preview = line.strip();
                        if (preview.length() > MAX_LINE_LENGTH) {
                            preview = preview.substring(0, MAX_LINE_LENGTH) + "...";
                        }
                        matches.append(relative(file)).append(':').append(lineNumber)
                                .append(": ").append(preview).append('\n');
                        matchCount++;
                    }
                }
            } catch (IOException | UncheckedIOException ignored) {
                // Skip unreadable or binary files and continue searching the rest.
            }
        }

        String scope = relative(directory);
        if (matchCount == 0) {
            return ToolResult.ok("未找到包含 \"" + keyword + "\" 的文本行（目录: " + scope
                    + "，扫描 " + files.size() + " 个文件）。");
        }
        return ToolResult.ok("在目录 " + scope + " 中找到 " + matchCount + " 条匹配（扫描 "
                + files.size() + " 个文件）：\n" + matches);
    }

    private String relative(Path path) {
        String value = pathGuard.workspace().relativize(path).toString().replace('\\', '/');
        return value.isEmpty() ? "." : value;
    }
}
