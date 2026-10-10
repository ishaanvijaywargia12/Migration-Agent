package io.migrationagent.llm;

/**
 * @param arguments a JSON-encoded string per the OpenAI tool-calling spec
 *                  (not a nested JSON object) — the caller parses this
 *                  itself when dispatching the tool.
 */
public record FunctionCall(String name, String arguments) {
}
