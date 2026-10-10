package io.migrationagent.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Appends one {@link EvalResult} at a time to a Markdown table file, rather
 * than generating the whole table in one shot — ablation runs are
 * expensive (real Docker builds, real free-tier API calls), so results
 * accumulate across separate invocations over time instead of requiring
 * one big batch run.
 */
public final class ResultsTableWriter {

    private static final String HEADER =
            "| Repo | Ablation | Baseline OK | Recipe Exit | Files Changed | Resolved | "
            + "Final Tests (total/failed/errored) | Tests Preserved | Iterations | Model Calls | "
            + "Tokens (in/out) | Wall Clock (s) | Rule Fixes | Final Status |\n"
            + "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n";

    public void appendRow(Path tableFile, EvalResult result) throws IOException {
        if (!Files.exists(tableFile) || Files.size(tableFile) == 0) {
            Files.writeString(tableFile, HEADER);
        }
        Files.writeString(tableFile, formatRow(result) + "\n", StandardOpenOption.APPEND);
    }

    private String formatRow(EvalResult r) {
        return "| " + r.repoName()
                + " | " + r.ablationMode()
                + " | " + r.baselineCompiled() + " (" + r.baselineTestsTotal() + " tests)"
                + " | " + nullableToString(r.recipeExitCode())
                + " | " + nullableToString(r.filesChangedByRecipe())
                + " | " + r.finalResolved()
                + " | " + r.finalTestsTotal() + "/" + r.finalTestsFailed() + "/" + r.finalTestsErrored()
                + " | " + r.testsPreserved()
                + " | " + r.iterations()
                + " | " + r.modelCalls()
                + " | " + r.totalTokensIn() + "/" + r.totalTokensOut()
                + " | " + r.wallClockSeconds()
                + " | " + r.rulesFixedCount()
                + " | " + r.finalStatus()
                + " |";
    }

    private String nullableToString(Integer value) {
        return value == null ? "n/a" : value.toString();
    }
}
