package io.migrationagent.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @param parameters a raw JSON Schema object describing the tool's
 *                   arguments, exactly as sent in the {@code tools} array
 *                   of a chat completion request.
 */
public record FunctionDefinition(String name, String description, JsonNode parameters) {
}
