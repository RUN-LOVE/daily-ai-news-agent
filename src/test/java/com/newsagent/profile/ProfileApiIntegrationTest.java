package com.newsagent.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.db-path=target/test-data/profile-api.db",
        "app.scheduler-enabled=false"
})
@AutoConfigureMockMvc
class ProfileApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProfile() {
        jdbcTemplate.update("DELETE FROM profile");
    }

    @Test
    void returnsDefaultProfileOnFirstRead() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("AI 探索者"))
                .andExpect(jsonPath("$.topics", contains("大模型", "AI Agent")))
                .andExpect(jsonPath("$.sources", hasSize(4)))
                .andExpect(jsonPath("$.language").value("zh"))
                .andExpect(jsonPath("$.maxItems").value(8))
                .andExpect(jsonPath("$.deliverTime").value("08:30"))
                .andExpect(jsonPath("$.pushChannel").value("INBOX"));
    }

    @Test
    void savesAndReadsBackProfile() throws Exception {
        String body = """
                {
                  "displayName": "  李雷  ",
                  "role": "算法工程师",
                  "email": "lilei@example.com",
                  "topics": ["大模型", "大模型", " AI Agent "],
                  "keywords": ["推理", " 开源 "],
                  "excludeKeywords": ["招聘"],
                  "sources": ["infoq", "openai-news"],
                  "language": "zh",
                  "maxItems": 6,
                  "deliverTime": "09:15",
                  "pushChannel": "EMAIL"
                }
                """;

        mockMvc.perform(put("/api/profile").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("李雷"))
                .andExpect(jsonPath("$.topics", contains("大模型", "AI Agent")))
                .andExpect(jsonPath("$.keywords", contains("推理", "开源")))
                .andExpect(jsonPath("$.maxItems").value(6))
                .andExpect(jsonPath("$.deliverTime").value("09:15"))
                .andExpect(jsonPath("$.pushChannel").value("EMAIL"));

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("李雷"))
                .andExpect(jsonPath("$.role").value("算法工程师"))
                .andExpect(jsonPath("$.email").value("lilei@example.com"))
                .andExpect(jsonPath("$.sources", contains("infoq", "openai-news")))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void rejectsBlankDisplayName() throws Exception {
        String body = """
                {
                  "displayName": "   ",
                  "topics": [],
                  "keywords": [],
                  "sources": []
                }
                """;

        mockMvc.perform(put("/api/profile").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("称呼不能为空"));
    }

    @Test
    void rejectsEmailPushWithoutAddress() throws Exception {
        String body = """
                {
                  "displayName": "李雷",
                  "email": "",
                  "topics": ["大模型"],
                  "sources": ["infoq"],
                  "pushChannel": "EMAIL"
                }
                """;

        mockMvc.perform(put("/api/profile").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("选择邮件推送时必须填写邮箱"));
    }

    @Test
    void rejectsUnknownSource() throws Exception {
        String body = """
                {
                  "displayName": "李雷",
                  "topics": ["大模型"],
                  "sources": ["not-a-source"]
                }
                """;

        mockMvc.perform(put("/api/profile").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("包含未知的新闻源"));
    }

    @Test
    void listsConfiguredSources() throws Exception {
        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].id").value("infoq"))
                .andExpect(jsonPath("$[0].name").value("InfoQ 中文"))
                .andExpect(jsonPath("$[3].id").value("google-ai"));
    }
}
