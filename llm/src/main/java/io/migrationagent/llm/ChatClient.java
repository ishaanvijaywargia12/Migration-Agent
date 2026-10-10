package io.migrationagent.llm;

import java.io.IOException;
import java.util.List;

/**
 * One provider's chat-completions endpoint. This is the only interface the
 * agent loop depends on — it never sees credentials, rate limiting, retry
 * logic, or HTTP details, and Phase 5's cascade wrapper will implement this
 * same interface to swap providers transparently.
 */
public interface ChatClient {

    ChatCompletionResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools)
            throws IOException, InterruptedException;
}
