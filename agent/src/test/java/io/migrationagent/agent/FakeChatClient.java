package io.migrationagent.agent;

import io.migrationagent.llm.ChatClient;
import io.migrationagent.llm.ChatCompletionResponse;
import io.migrationagent.llm.ChatMessage;
import io.migrationagent.llm.ToolDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Returns a fixed, scripted sequence of responses — no network involved. */
final class FakeChatClient implements ChatClient {

    private final Deque<ChatCompletionResponse> scriptedResponses;
    private int callCount = 0;

    FakeChatClient(List<ChatCompletionResponse> scriptedResponses) {
        this.scriptedResponses = new ArrayDeque<>(scriptedResponses);
    }

    @Override
    public ChatCompletionResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        callCount++;
        if (scriptedResponses.isEmpty()) {
            throw new IllegalStateException("FakeChatClient called more times than scripted (call #" + callCount + ")");
        }
        return scriptedResponses.poll();
    }

    int callCount() {
        return callCount;
    }
}
