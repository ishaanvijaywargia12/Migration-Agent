package io.migrationagent.llm;

import java.util.List;

public record ChatCompletionResponse(List<Choice> choices, Usage usage) {

    public ChatMessage firstMessage() {
        return choices.get(0).message();
    }
}
