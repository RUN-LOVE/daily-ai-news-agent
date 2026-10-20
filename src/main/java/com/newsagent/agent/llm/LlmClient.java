package com.newsagent.agent.llm;

public interface LlmClient {

    boolean isConfigured();

    LlmChatResponse chat(LlmChatRequest request);
}
