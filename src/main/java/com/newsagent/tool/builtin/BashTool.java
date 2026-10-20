package com.newsagent.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.newsagent.tool.AgentTool;
import com.newsagent.tool.CommandGuard;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class BashTool implements AgentTool {

    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_BYTES = 8_000;
    private static final JsonNode SCHEMA = ToolSchemas.parse("""
            {
              "type": "object",
              "properties": {
                "command": {
                  "type": "string",
                  "description": "受限的只读命令，例如 echo hello、pwd、ls、dir、whoami"
                }
              },
              "required": ["command"],
              "additionalProperties": false
            }
            """);

    private final PathGuard pathGuard;
    private final CommandGuard commandGuard;

    public BashTool(PathGuard pathGuard, CommandGuard commandGuard) {
        this.pathGuard = pathGuard;
        this.commandGuard = commandGuard;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "在 workspace 内执行受限的只读命令，允许 echo、pwd、ls、dir、whoami；"
                + "不支持管道、重定向、绝对路径或路径穿越。";
    }

    @Override
    public JsonNode parametersSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        String command = commandGuard.validate(JsonArgs.requiredText(arguments, "command"));
        ProcessBuilder builder = createProcessBuilder(command);
        builder.directory(pathGuard.workspace().toFile());
        builder.redirectErrorStream(true);

        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("news-agent-bash-", ".log");
            builder.redirectOutput(outputFile.toFile());
            Process process = builder.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return ToolResult.error("命令执行超时（" + TIMEOUT_SECONDS + " 秒）");
            }

            String output = readLimited(outputFile);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ToolResult.error("命令退出码 " + exitCode + "\n" + output);
            }
            return ToolResult.ok(output.isBlank() ? "（命令无输出）" : output);
        } catch (IOException exception) {
            throw new ToolException("命令启动失败: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolException("命令执行被中断");
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // Temporary output cleanup is best effort.
                }
            }
        }
    }

    private ProcessBuilder createProcessBuilder(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new ProcessBuilder("cmd.exe", "/d", "/s", "/c", command);
        }
        return new ProcessBuilder("/bin/sh", "-c", command);
    }

    private String readLimited(Path outputFile) throws IOException {
        try (InputStream input = Files.newInputStream(outputFile)) {
            byte[] bytes = input.readNBytes(MAX_OUTPUT_BYTES + 1);
            boolean truncated = bytes.length > MAX_OUTPUT_BYTES;
            int length = Math.min(bytes.length, MAX_OUTPUT_BYTES);
            String output = new String(bytes, 0, length, StandardCharsets.UTF_8);
            return truncated ? output + "\n...[输出已截断]" : output;
        }
    }
}
