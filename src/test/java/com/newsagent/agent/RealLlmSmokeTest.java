package com.newsagent.agent;

import com.newsagent.agent.llm.LlmProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.scheduler-enabled=false")
class RealLlmSmokeTest {

    @Autowired
    private AgentRunner agentRunner;

    @Autowired
    private LlmProperties properties;

    @Test
    @EnabledIfSystemProperty(named = "llm.smoke", matches = "true")
    void runsAgainstConfiguredModel() {
        Assumptions.assumeTrue(properties.configured(), "LLM 未配置，跳过真实模型测试");

        AgentResult result = agentRunner.run(
                "请先列出 workspace 根目录，再读取 sources/feeds.json，"
                        + "最后告诉我配置了几个新闻源。必须使用工具。");

        assertFalse(result.steps().isEmpty());
        assertFalse(result.finalAnswer().isBlank());
        assertTrue(result.steps().stream().anyMatch(step -> step.toolName().equals("list_dir")
                || step.toolName().equals("read_file")));
    }
}
