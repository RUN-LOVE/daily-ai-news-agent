package com.newsagent.tool;

public record ToolResult(boolean success, String output) {

    private static final int MAX_OUTPUT_CHARS = 12_000;

    public ToolResult {
        output = normalize(output);
    }

    public static ToolResult ok(String output) {
        return new ToolResult(true, output);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message);
    }

    private static String normalize(String value) {
        String text = value == null ? "" : value;
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS) + "\n...[输出已截断]";
    }
}
