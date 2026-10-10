package io.migrationagent.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * The one {@link ObjectMapper} configuration every DTO in this package
 * relies on: snake_case field names (matching the OpenAI wire format
 * exactly, so no per-field {@code @JsonProperty} annotations are needed)
 * and null fields omitted from serialized requests (a {@code ChatMessage}
 * with a null {@code toolCalls} shouldn't send a literal {@code "tool_calls":
 * null} to the provider).
 *
 * <p>Unknown properties on deserialization are ignored rather than failing
 * the whole call — confirmed necessary in practice, not speculative: a real
 * Groq model response included a {@code "reasoning"} field (a visible
 * chain-of-thought trace some reasoning-capable models return alongside
 * {@code content}) that crashed every model call until this was added.
 * "OpenAI-compatible" has never meant "identical wire format down to the
 * last field" across providers.
 */
final class ChatJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ChatJson() {
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }
}
