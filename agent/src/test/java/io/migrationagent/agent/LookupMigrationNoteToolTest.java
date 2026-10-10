package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LookupMigrationNoteToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void findsANoteByExactTopicName(@TempDir Path notesDir) throws Exception {
        Files.writeString(notesDir.resolve("javax-jakarta.md"), "Replace javax.* imports with jakarta.*");
        LookupMigrationNoteTool tool = new LookupMigrationNoteTool(notesDir);

        String result = tool.execute(args("""
                {"topic": "javax-jakarta"}
                """));

        assertThat(result).isEqualTo("Replace javax.* imports with jakarta.*");
    }

    @Test
    void matchesCaseInsensitivelyAndNormalizesSeparators(@TempDir Path notesDir) throws Exception {
        Files.writeString(notesDir.resolve("spring-security-config.md"), "note content");
        LookupMigrationNoteTool tool = new LookupMigrationNoteTool(notesDir);

        String result = tool.execute(args("""
                {"topic": "Spring_Security_Config"}
                """));

        assertThat(result).isEqualTo("note content");
    }

    @Test
    void reportsNoNoteFoundRatherThanFailing(@TempDir Path notesDir) throws Exception {
        LookupMigrationNoteTool tool = new LookupMigrationNoteTool(notesDir);

        String result = tool.execute(args("""
                {"topic": "nonexistent-topic"}
                """));

        assertThat(result).contains("No migration note found");
    }

    private JsonNode args(String json) throws Exception {
        return mapper.readTree(json);
    }
}
