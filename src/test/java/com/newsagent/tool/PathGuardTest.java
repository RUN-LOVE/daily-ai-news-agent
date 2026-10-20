package com.newsagent.tool;

import com.newsagent.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathGuardTest {

    @TempDir
    Path workspace;

    private PathGuard pathGuard;

    @BeforeEach
    void setUp() {
        pathGuard = new PathGuard(new AppProperties(workspace.toString(), "unused"));
    }

    @Test
    void resolvesPathsInsideWorkspace() {
        assertEquals(workspace.resolve("reports/a.md"), pathGuard.resolve("reports/a.md"));
        assertEquals(workspace.resolve("reports/a.md"),
                pathGuard.resolve("sources/../reports/a.md"));
        assertEquals(workspace, pathGuard.requireDirectory("."));
    }

    @Test
    void rejectsPathsOutsideWorkspace() {
        assertThrows(ToolException.class, () -> pathGuard.resolve("../secret.txt"));
        assertThrows(ToolException.class, () -> pathGuard.resolve("reports/../../secret.txt"));
    }

    @Test
    void rejectsAbsolutePaths() {
        assertThrows(ToolException.class, () -> pathGuard.resolve("/etc/passwd"));
        assertThrows(ToolException.class, () -> pathGuard.resolve("C:\\Windows\\win.ini"));
    }

    @Test
    void createsParentDirectoriesBeforeWriting() {
        Path target = pathGuard.prepareForWrite("reports/2026/summary.md");

        assertEquals(workspace.resolve("reports/2026/summary.md"), target);
        assertTrue(Files.isDirectory(workspace.resolve("reports/2026")));
    }
}
