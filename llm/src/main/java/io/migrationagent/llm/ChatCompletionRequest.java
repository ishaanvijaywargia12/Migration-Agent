package io.migrationagent.llm;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        List<ToolDefinition> tools,
        String toolChoice
) {
}
