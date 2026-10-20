package com.newsagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.config.AppProperties;
import com.newsagent.tool.builtin.BashTool;
import com.newsagent.tool.builtin.ListDirTool;
import com.newsagent.tool.builtin.ReadFileTool;
import com.newsagent.tool.builtin.SearchContentTool;
import com.newsagent.tool.builtin.WriteFileTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @TempDir
    Path workspace;

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        PathGuard pathGuard = new PathGuard(new AppProperties(workspace.toString(), "unused"));
        registry = new ToolRegistry(List.of(
                new ListDirTool(pathGuard),
                new ReadFileTool(pathGuard),
                new SearchContentTool(pathGuard),
                new WriteFileTool(pathGuard),
                new BashTool(pathGuard, new CommandGuard())
        ), new ObjectMapper());
    }

    @Test
    void exposesExactlyTheFiveRequiredTools() {
        assertEquals(
                Set.of("list_dir", "read_file", "search_content", "write_file", "bash"),
                registry.names());
        assertEquals(5, registry.definitions().size());
        registry.definitions().forEach(definition -> {
            assertEquals("function", definition.type());
            assertNotNull(definition.function().parameters());
        });
    }

    @Test
    void writesReadsAndSearchesFiles() {
        ToolResult write = registry.execute("write_file",
                "{\"path\":\"reports/demo.md\",\"content\":\"hello agent\"}");
        assertTrue(write.success(), write.output());

        ToolResult read = registry.execute("read_file", "{\"path\":\"reports/demo.md\"}");
        assertTrue(read.success(), read.output());
        assertTrue(read.output().contains("hello agent"));

        ToolResult list = registry.execute("list_dir", "{\"path\":\".\"}");
        assertTrue(list.success(), list.output());
        assertTrue(list.output().contains("reports"));

        ToolResult search = registry.execute("search_content",
                "{\"keyword\":\"hello\",\"dir\":\".\"}");
        assertTrue(search.success(), search.output());
        assertTrue(search.output().contains("reports/demo.md:1"));
    }

    @Test
    void runsAllowlistedBashCommand() {
        ToolResult result = registry.execute("bash", "{\"command\":\"echo hello\"}");

        assertTrue(result.success(), result.output());
        assertTrue(result.output().contains("hello"));
    }

    @Test
    void returnsErrorForUnknownTool() {
        ToolResult result = registry.execute("not_a_tool", "{}");

        assertFalse(result.success());
        assertTrue(result.output().contains("未知工具"));
    }

    @Test
    void returnsErrorForInvalidArguments() {
        ToolResult result = registry.execute("read_file", "not-json");

        assertFalse(result.success());
        assertTrue(result.output().contains("不是合法 JSON"));
    }

    @Test
    void blocksPathTraversal() {
        ToolResult result = registry.execute("read_file", "{\"path\":\"../secret.txt\"}");

        assertFalse(result.success());
        assertTrue(result.output().contains("路径超出工作区"));
    }

    @Test
    void blocksDangerousBashCommand() {
        ToolResult result = registry.execute("bash", "{\"command\":\"rm -rf /\"}");

        assertFalse(result.success());
        assertTrue(result.output().contains("白名单"));
    }

    @Test
    void writesIntoPreparedDirectory() throws Exception {
        ToolResult result = registry.execute("write_file",
                "{\"path\":\"reports/nested/note.txt\",\"content\":\"abc\"}");

        assertTrue(result.success(), result.output());
        assertEquals("abc", Files.readString(workspace.resolve("reports/nested/note.txt")));
    }
}
