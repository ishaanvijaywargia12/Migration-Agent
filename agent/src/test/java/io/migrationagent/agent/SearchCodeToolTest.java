package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCodeToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void findsMatchingLinesAcrossFiles(@TempDir Path repoRoot) throws Exception {
        Files.writeString(repoRoot.resolve("Foo.java"), "import javax.persistence.Entity;\nclass Foo {}\n");
        Files.writeString(repoRoot.resolve("Bar.java"), "class Bar {}\n");

        SearchCodeTool tool = new SearchCodeTool(repoRoot);
        String result = tool.execute(args("""
                {"pattern": "javax\\\\.persistence"}
                """));

        assertThat(result).contains("Foo.java");
        assertThat(result).doesNotContain("Bar.java");
    }

    @Test
    void filtersByGlobWhenProvided(@TempDir Path repoRoot) throws Exception {
        Files.writeString(repoRoot.resolve("Foo.java"), "TODO\n");
        Files.writeString(repoRoot.resolve("notes.txt"), "TODO\n");

        SearchCodeTool tool = new SearchCodeTool(repoRoot);
        String result = tool.execute(args("""
                {"pattern": "TODO", "glob": "*.java"}
                """));

        assertThat(result).contains("Foo.java");
        assertThat(result).doesNotContain("notes.txt");
    }

    @Test
    void skipsGitAndTargetDirectories(@TempDir Path repoRoot) throws Exception {
        Files.createDirectories(repoRoot.resolve("target/classes"));
        Files.writeString(repoRoot.resolve("target/classes/Generated.txt"), "needle\n");
        Files.writeString(repoRoot.resolve("Real.java"), "needle\n");

        SearchCodeTool tool = new SearchCodeTool(repoRoot);
        String result = tool.execute(args("""
                {"pattern": "needle"}
                """));

        assertThat(result).contains("Real.java");
        assertThat(result).doesNotContain("Generated.txt");
    }

    @Test
    void reportsNoMatchesClearly(@TempDir Path repoRoot) throws Exception {
        Files.writeString(repoRoot.resolve("Foo.java"), "nothing interesting\n");

        SearchCodeTool tool = new SearchCodeTool(repoRoot);
        String result = tool.execute(args("""
                {"pattern": "zzz_not_present"}
                """));

        assertThat(result).contains("No matches");
    }

    private JsonNode args(String json) throws Exception {
        return mapper.readTree(json);
    }
}
