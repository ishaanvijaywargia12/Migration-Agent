package io.migrationagent.buildparse;

import java.time.Duration;
import java.util.List;

/**
 * The single structured artifact that a sandbox build/test run produces.
 * Every downstream consumer (triage, agent loop, report generator) reads
 * this record rather than re-parsing raw Maven output.
 */
public record BuildResult(
        boolean compiled,
        boolean testsRan,
        int testsTotal,
        int testsFailed,
        int testsErrored,
        int testsSkipped,
        List<Failure> failures,
        Duration wallClock,
        int mavenExitCode
) {
    public int failureCount() {
        return failures.size();
    }
}
