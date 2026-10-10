package io.migrationagent.llm;

public record Choice(ChatMessage message, String finishReason) {
}
