package io.migrationagent.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvFileTest {

    @Test
    void parsesKeyValuePairsIgnoringBlankLinesAndComments(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                # a comment
                GROQ_API_KEY=gsk_abc123

                OPENROUTER_API_KEY=sk-or-xyz789
                """);

        Map<String, String> values = EnvFile.load(envFile);

        assertThat(values).containsEntry("GROQ_API_KEY", "gsk_abc123");
        assertThat(values).containsEntry("OPENROUTER_API_KEY", "sk-or-xyz789");
        assertThat(values).hasSize(2);
    }

    @Test
    void stripsSurroundingQuotes(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "KEY=\"quoted value\"\n");

        Map<String, String> values = EnvFile.load(envFile);

        assertThat(values).containsEntry("KEY", "quoted value");
    }

    @Test
    void returnsEmptyMapWhenFileDoesNotExist(@TempDir Path tempDir) throws Exception {
        Map<String, String> values = EnvFile.load(tempDir.resolve("missing.env"));

        assertThat(values).isEmpty();
    }
}
