package io.migrationagent.llm;

import java.util.List;

/**
 * One message in an OpenAI-compatible chat completion request/response.
 * Exactly one of {@code content} or {@code toolCalls} is meaningful
 * depending on {@code role}: an assistant message requesting tool calls has
 * {@code toolCalls} populated (often with null content); a {@code tool}
 * message answering one has {@code toolCallId} set and {@code content} as
 * the result text.
 */
public record ChatMessage(
        String role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId
) {
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls, null);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, toolCallId);
    }
}
