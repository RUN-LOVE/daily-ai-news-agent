package com.newsagent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandGuardTest {

    private final CommandGuard commandGuard = new CommandGuard();

    @ParameterizedTest
    @ValueSource(strings = {"echo hello", "pwd", "ls -la", "dir", "whoami", "echo 你好", "echo \"hello world\""})
    void acceptsReadOnlyCommands(String command) {
        assertDoesNotThrow(() -> commandGuard.validate(command));
    }

    @Test
    void trimsAndNormalizesWhitespace() {
        assertEquals("echo hello", commandGuard.validate("  echo   hello  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "rm -rf /",
            "echo hi; whoami",
            "echo $(whoami)",
            "cat /etc/passwd",
            "pwd && whoami",
            "echo ../secret",
            "echo C:\\Windows\\win.ini",
            "echo hi > out.txt",
            "curl https://example.com",
            "date",
            "echo 100%",
            "echo `whoami`",
            "echo hi\nwhoami"
    })
    void rejectsUnsafeCommands(String command) {
        assertThrows(ToolException.class, () -> commandGuard.validate(command));
    }

    @Test
    void rejectsBlankCommand() {
        assertThrows(ToolException.class, () -> commandGuard.validate("   "));
    }
}
