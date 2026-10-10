package io.migrationagent.llm;

public record ToolCall(String id, String type, FunctionCall function) {
}
