package io.migrationagent.llm;

public record Usage(int promptTokens, int completionTokens, int totalTokens) {
}
