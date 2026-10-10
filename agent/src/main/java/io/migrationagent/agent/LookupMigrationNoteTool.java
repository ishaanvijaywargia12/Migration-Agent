package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.migrationagent.llm.FunctionDefinition;
import io.migrationagent.llm.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Retrieval over the hand-written knowledge base in
 * {@code docs/migration-notes/} (DESIGN.md section 8) — not a vector
 * search, just a case-insensitive filename match, since the whole point is
 * a small set of specific, hand-curated notes rather than something that
 * needs semantic search over volume.
 */
public final class LookupMigrationNoteTool implements AgentTool {

    private final Path migrationNotesDir;

    public LookupMigrationNoteTool(Path migrationNotesDir) {
        this.migrationNotesDir = migrationNotesDir;
    }

    @Override
    public String name() {
        return "lookup_migration_note";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(new FunctionDefinition(
                name(),
                "Retrieve a hand-written migration note by topic from the local knowledge base.",
                JsonSchemas.parse("""
                        {
                          "type": "object",
                          "properties": {"topic": {"type": "string"}},
                          "required": ["topic"]
                        }
                        """)));
    }

    @Override
    public String execute(JsonNode arguments) throws IOException {
        String topic = arguments.get("topic").asText();
        if (!Files.isDirectory(migrationNotesDir)) {
            return "No migration notes knowledge base found.";
        }

        Optional<Path> match;
        try (Stream<Path> files = Files.list(migrationNotesDir)) {
            match = files
                    .filter(Files::isRegularFile)
                    .filter(file -> matchesTopic(file, topic))
                    .findFirst();
        }

        if (match.isEmpty()) {
            return "No migration note found for topic '" + topic + "'.";
        }
        return Files.readString(match.get());
    }

    private boolean matchesTopic(Path file, String topic) {
        String fileName = file.getFileName().toString();
        String normalizedFileName = stripExtension(fileName).toLowerCase();
        String normalizedTopic = topic.toLowerCase().replace(' ', '-').replace('_', '-');
        return normalizedFileName.equals(normalizedTopic);
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }
}
