package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsARequestedLineRange(@TempDir Path repoRoot) throws Exception {
        Files.writeString(repoRoot.resolve("Foo.java"), "line1\nline2\nline3\nline4\n");
        ReadFileTool tool = new ReadFileTool(repoRoot);

        String result = tool.execute(args("""
                {"path": "Foo.java", "start_line": 2, "end_line": 3}
                """));

        assertThat(result).isEqualTo("2: line2\n3: line3\n");
    }

    @Test
    void readsFromTheStartWhenNoRangeIsGiven(@TempDir Path repoRoot) throws Exception {
        Files.writeString(repoRoot.resolve("Foo.java"), "a\nb\nc\n");
        ReadFileTool tool = new ReadFileTool(repoRoot);

        String result = tool.execute(args("""
                {"path": "Foo.java"}
                """));

        assertThat(result).isEqualTo("1: a\n2: b\n3: c\n");
    }

    @Test
    void reportsAMissingFileInsteadOfThrowing(@TempDir Path repoRoot) throws Exception {
        ReadFileTool tool = new ReadFileTool(repoRoot);

        String result = tool.execute(args("""
                {"path": "DoesNotExist.java"}
                """));

        assertThat(result).contains("No such file");
    }

    @Test
    void rejectsAPathEscapingTheRepo(@TempDir Path repoRoot) {
        ReadFileTool tool = new ReadFileTool(repoRoot);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                tool.execute(args("""
                        {"path": "../../etc/passwd"}
                        """)));
    }

    private JsonNode args(String json) throws Exception {
        return mapper.readTree(json);
    }
}
