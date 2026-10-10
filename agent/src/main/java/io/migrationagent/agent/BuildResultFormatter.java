package io.migrationagent.agent;

import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.buildparse.Failure;

/**
 * Compresses a {@link BuildResult} into a compact summary for the model —
 * raw Maven output can run to thousands of lines, most of it irrelevant
 * dependency-resolution noise. DESIGN.md section 8's design intent for
 * {@code run_build}/{@code run_tests} is "parsed, truncated output," not
 * the full log.
 */
final class BuildResultFormatter {

    private static final int MAX_FAILURES_SHOWN = 20;

    private BuildResultFormatter() {
    }

    static String summarize(BuildResult result) {
        StringBuilder summary = new StringBuilder();
        summary.append("Maven exit code: ").append(result.mavenExitCode()).append('\n');
        summary.append("Compiled: ").append(result.compiled()).append('\n');
        summary.append("Tests: total=").append(result.testsTotal())
                .append(" failed=").append(result.testsFailed())
                .append(" errored=").append(result.testsErrored())
                .append(" skipped=").append(result.testsSkipped())
                .append('\n');

        if (!result.failures().isEmpty()) {
            summary.append("Failures:\n");
            int shown = 0;
            for (Failure failure : result.failures()) {
                if (shown >= MAX_FAILURES_SHOWN) {
                    summary.append("... and ").append(result.failures().size() - shown).append(" more\n");
                    break;
                }
                summary.append("- [").append(failure.category()).append("] ")
                        .append(failure.file() != null ? failure.file() : "?")
                        .append(failure.line() >= 0 ? ":" + failure.line() : "")
                        .append(" ").append(failure.message()).append('\n');
                shown++;
            }
        }
        return summary.toString();
    }
}
