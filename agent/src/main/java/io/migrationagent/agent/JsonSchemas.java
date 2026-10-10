package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses the JSON Schema literal each {@link AgentTool} embeds for its
 * {@code parameters} — keeping the schema as a plain JSON string in each
 * tool class (matching DESIGN.md section 8 exactly) rather than building it
 * up via a JsonNode/ObjectNode builder API keeps the schema readable and
 * easy to diff against the design doc.
 */
final class JsonSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemas() {
    }

    static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid tool parameter schema: " + json, e);
        }
    }
}
