package com.newsagent.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsagent.tool.ToolSchemas;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOpenAiCompatibleRequestShape() throws Exception {
        LlmToolCall toolCall = LlmToolCall.of("call_1", "list_dir", "{\"path\":\".\"}");
        LlmChatRequest request = new LlmChatRequest(
                "test-model",
                List.of(
                        LlmMessage.system("system"),
                        LlmMessage.user("user"),
                        LlmMessage.assistant(null, List.of(toolCall)),
                        LlmMessage.tool("call_1", "result")
                ),
                List.of(LlmToolDefinition.of("list_dir", "desc", ToolSchemas.parse("{}"))),
                0.2
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertEquals("test-model", json.path("model").asText());
        assertEquals("function", json.path("messages").get(2).path("tool_calls").get(0)
                .path("type").asText());
        assertEquals("list_dir", json.path("messages").get(2).path("tool_calls").get(0)
                .path("function").path("name").asText());
        assertEquals("call_1", json.path("messages").get(3).path("tool_call_id").asText());
        assertFalse(json.path("messages").get(0).has("tool_calls"));
        assertFalse(json.path("messages").get(2).has("content"));
        assertEquals("function", json.path("tools").get(0).path("type").asText());
    }

    @Test
    void parsesToolCallsFromResponse() throws Exception {
        String body = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "search_content",
                              "arguments": "{\\"keyword\\":\\"Agent\\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ]
                }
                """;

        LlmChatResponse response = objectMapper.readValue(body, LlmChatResponse.class);
        LlmChatResponse.LlmAssistantMessage message = response.firstMessage();

        assertNull(message.content());
        assertEquals(1, message.toolCalls().size());
        assertEquals("search_content", message.toolCalls().get(0).function().name());
        assertEquals("{\"keyword\":\"Agent\"}", message.toolCalls().get(0).function().arguments());
    }
}
