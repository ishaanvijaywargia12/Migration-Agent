package io.migrationagent.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultsTableWriterTest {

    private final ResultsTableWriter writer = new ResultsTableWriter();

    @Test
    void writesAHeaderOnlyOnceAndAppendsSubsequentRows(@TempDir Path tempDir) throws Exception {
        Path table = tempDir.resolve("results.md");

        writer.appendRow(table, sampleResult("repo-a", AblationMode.HYBRID));
        writer.appendRow(table, sampleResult("repo-b", AblationMode.OPENREWRITE_ONLY));

        List<String> lines = Files.readAllLines(table);
        assertThat(lines.get(0)).startsWith("| Repo |");
        assertThat(lines).hasSize(4); // header + separator + 2 rows
        assertThat(lines.get(2)).contains("repo-a").contains("HYBRID");
        assertThat(lines.get(3)).contains("repo-b").contains("OPENREWRITE_ONLY");
    }

    @Test
    void rendersNullRecipeFieldsAsNotApplicableForLlmOnlyRuns(@TempDir Path tempDir) throws Exception {
        Path table = tempDir.resolve("results.md");
        EvalResult llmOnly = new EvalResult(
                "repo-a", AblationMode.LLM_ONLY, true, 10,
                null, null,
                false, false, 10, 2, 0, true,
                3, 5, 100, 50, 120, 0, Map.of(), "unresolved");

        writer.appendRow(table, llmOnly);

        String content = Files.readString(table);
        assertThat(content).contains("n/a");
    }

    private EvalResult sampleResult(String repoName, AblationMode mode) {
        return new EvalResult(
                repoName, mode, true, 41,
                0, 23,
                true, true, 41, 0, 0, true,
                1, 0, 0, 0, 180, 0, Map.of(), "success");
    }
}
