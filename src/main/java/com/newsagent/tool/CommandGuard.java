package com.newsagent.tool;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CommandGuard {

    private static final Set<String> ALLOWED_COMMANDS = Set.of("echo", "pwd", "ls", "dir", "whoami");
    private static final String FORBIDDEN_CHARS = ";&|><`$%!^(){}[]*?~\\";
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:.*");
    private static final int MAX_LENGTH = 500;

    public String validate(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            throw new ToolException("命令不能为空");
        }

        String raw = rawCommand.trim();
        if (raw.length() > MAX_LENGTH) {
            throw new ToolException("命令过长（最多 " + MAX_LENGTH + " 个字符）");
        }

        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (character == '\n' || character == '\r' || character == '\0'
                    || FORBIDDEN_CHARS.indexOf(character) >= 0) {
                throw new ToolException("命令包含不允许的字符: " + character);
            }
        }

        String command = raw.replaceAll("[\\t ]+", " ");
        String[] tokens = command.split(" ");
        String executable = tokens[0];
        if (!ALLOWED_COMMANDS.contains(executable)) {
            throw new ToolException("命令不在白名单内: " + executable
                    + "（允许: echo、pwd、ls、dir、whoami）");
        }

        for (String token : tokens) {
            String value = token.replace("\"", "").replace("'", "");
            if (value.startsWith("/") || value.startsWith("\\")
                    || WINDOWS_ABSOLUTE.matcher(value).matches()
                    || value.contains("..")) {
                throw new ToolException("命令参数不允许使用绝对路径或路径穿越");
            }
        }
        return command;
    }
}
