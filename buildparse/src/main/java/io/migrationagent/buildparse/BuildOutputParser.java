package io.migrationagent.buildparse;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Public entry point for turning one sandbox build/test invocation's raw
 * output into a {@link BuildResult}. This is the only class other modules
 * (sandbox, agent, report) should call into — {@link SurefireReportParser}
 * and {@link MavenConsoleParser} are implementation details.
 */
public final class BuildOutputParser {

    private final SurefireReportParser surefireReportParser = new SurefireReportParser();

    public BuildResult parse(String mavenConsoleOutput, Path repoRoot, Duration wallClock, int mavenExitCode)
            throws IOException {
        List<Failure> compileFailures = MavenConsoleParser.parseCompileErrors(mavenConsoleOutput);
        SurefireReportParser.Result surefireResult = surefireReportParser.parse(repoRoot);

        boolean testsRan = surefireResult.filesParsed() > 0;
        // "No javac-style [ERROR] line" alone isn't proof of a successful
        // compile — a failure earlier in the lifecycle (a plugin exception
        // during `validate`, a dependency resolution failure, etc.) produces
        // no compile-error lines either, since compilation was never
        // attempted. testsRan is positive proof (Maven can't reach the test
        // phase without a successful compile); otherwise fall back to the
        // process exit code rather than silently assuming success.
        boolean compiled = testsRan || mavenExitCode == 0;

        List<Failure> allFailures = new ArrayList<>(compileFailures);
        allFailures.addAll(surefireResult.failures());

        return new BuildResult(
                compiled,
                testsRan,
                surefireResult.total(),
                surefireResult.failed(),
                surefireResult.errored(),
                surefireResult.skipped(),
                allFailures,
                wallClock,
                mavenExitCode
        );
    }
}
