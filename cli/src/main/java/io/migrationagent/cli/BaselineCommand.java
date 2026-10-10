package io.migrationagent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.sandbox.RepoCheckout;
import io.migrationagent.sandbox.SandboxResult;
import io.migrationagent.sandbox.SandboxRunner;
import io.migrationagent.sandbox.TestcontainersSandboxRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

/**
 * Phase 1 command: clone a repo at a pinned commit, build and test it on
 * the JDK it declares (or the caller's override), and write an honest
 * report of what happened. No OpenRewrite, no LLM, no guardrails — those
 * are later phases (DESIGN.md section 14).
 */
@Command(name = "baseline", mixinStandardHelpOptions = true,
        description = "Build and test a repo as-is, on the JDK it declares.")
final class BaselineCommand implements Callable<Integer> {

    @Option(names = "--repo-url", required = true, description = "Public Git repository URL")
    String repoUrl;

    @Option(names = "--commit", required = true, description = "Commit SHA to check out")
    String commit;

    @Option(names = "--run-id", description = "Defaults to <repo-name>-<short-sha>-<timestamp>")
    String runId;

    @Option(names = "--jdk", description = "Override JDK major version (8, 11, or 17). Default: detected from pom.xml, falling back to 8.")
    Integer jdkOverride;

    @Option(names = "--build-timeout-seconds", defaultValue = "600")
    long buildTimeoutSeconds;

    @Override
    public Integer call() throws Exception {
        String resolvedRunId = runId != null ? runId : RunIds.defaultRunId(repoUrl, commit);
        Path runDir = Path.of("runs", resolvedRunId);
        Path workspace = runDir.resolve("workspace");
        Files.createDirectories(runDir);

        System.out.println("Cloning " + repoUrl + " @ " + commit + " ...");
        new RepoCheckout().checkout(repoUrl, commit, workspace);

        int jdkMajor = jdkOverride != null ? jdkOverride : JdkDetection.detectOrDefault(workspace);
        System.out.println("Using JDK " + jdkMajor);

        SandboxRunner runner = new TestcontainersSandboxRunner();
        System.out.println("Running `mvn test` inside sandbox (timeout " + buildTimeoutSeconds + "s)...");
        Path buildLogPath = runDir.resolve("build-output.log");
        BuildResult buildResult = BuildAndTestStep.run(
                runner, workspace, jdkMajor, Duration.ofSeconds(buildTimeoutSeconds), buildLogPath);

        printSummary(buildResult, buildLogPath);

        BaselineReport report = new BaselineReport(repoUrl, commit, jdkMajor, Instant.now(), buildResult);
        Path reportPath = runDir.resolve("baseline.json");
        writeReport(reportPath, report);
        System.out.println("Report written to " + reportPath);

        // The CLI's own exit code reflects whether it successfully produced a
        // report, not whether the target repo's build succeeded — that
        // distinction is in the report itself (result.compiled(), etc.).
        return 0;
    }

    private void printSummary(BuildResult buildResult, Path buildLogPath) {
        System.out.println();
        System.out.println("=== Baseline Result ===");
        if (buildResult.mavenExitCode() == SandboxResult.TIMED_OUT) {
            System.out.println("Build timed out after " + buildTimeoutSeconds + "s");
        }
        System.out.println("Maven exit code: " + buildResult.mavenExitCode());
        System.out.println("Compiled:   " + buildResult.compiled());
        System.out.println("Tests ran:  " + buildResult.testsRan());
        System.out.println("Tests:      total=" + buildResult.testsTotal()
                + " failed=" + buildResult.testsFailed()
                + " errored=" + buildResult.testsErrored()
                + " skipped=" + buildResult.testsSkipped());
        System.out.println("Failures:   " + buildResult.failureCount());
        System.out.println("Wall clock: " + buildResult.wallClock().toSeconds() + "s");
        System.out.println("Full build log: " + buildLogPath);
        System.out.println();
    }

    private void writeReport(Path path, BaselineReport report) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(path.toFile(), report);
    }
}
