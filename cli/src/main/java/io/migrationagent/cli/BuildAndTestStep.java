package io.migrationagent.cli;

import io.migrationagent.buildparse.BuildOutputParser;
import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.sandbox.SandboxResult;
import io.migrationagent.sandbox.SandboxRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The "run `mvn test` in the sandbox, save the raw log, parse the result"
 * sequence shared by every command that needs a build/test measurement
 * (baseline's single run, rewrite's before-and-after runs).
 */
final class BuildAndTestStep {

    // Skips non-functional style/format checks — not tests, don't affect
    // the success criteria (compiles + original tests pass), and old
    // versions of these plugins are prone to failing on toolchain/
    // architecture combinations unrelated to code or test correctness
    // (see CLAUDE.md Phase 1 status for the spring-javaformat incident
    // that motivated this).
    private static final List<String> STYLE_SKIP_ARGS = List.of(
            "-Dspring-javaformat.skip=true", "-Dcheckstyle.skip=true");

    private BuildAndTestStep() {
    }

    static BuildResult run(SandboxRunner runner, Path workspace, int jdkMajorVersion, Duration timeout, Path logPath)
            throws IOException {
        List<String> mavenArgs = new ArrayList<>(STYLE_SKIP_ARGS);
        mavenArgs.add("test");

        SandboxResult sandboxResult = runner.run(workspace, mavenArgs, jdkMajorVersion, timeout);
        Files.writeString(logPath, sandboxResult.output());

        return new BuildOutputParser().parse(
                sandboxResult.output(), workspace, sandboxResult.wallClock(), sandboxResult.exitCode());
    }
}
