package io.migrationagent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.rewrite.GitDiffSummarizer;
import io.migrationagent.rewrite.RepoDiff;
import io.migrationagent.rewrite.SpringBootMigrationRecipe;
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
 * Phase 2 command: measure the baseline, apply the official OpenRewrite
 * Spring Boot 3 recipe, and measure again — a real before/after comparison,
 * not a claim about what the recipe "should" do. No LLM, no guardrails, no
 * agent loop yet (those are later phases, DESIGN.md section 14).
 */
@Command(name = "rewrite", mixinStandardHelpOptions = true,
        description = "Apply the OpenRewrite Spring Boot 3 recipe and compare build/test results before and after.")
final class RewriteCommand implements Callable<Integer> {

    @Option(names = "--repo-url", required = true, description = "Public Git repository URL")
    String repoUrl;

    @Option(names = "--commit", required = true, description = "Commit SHA to check out")
    String commit;

    @Option(names = "--run-id", description = "Defaults to <repo-name>-<short-sha>-<timestamp>")
    String runId;

    @Option(names = "--jdk", description = "Override the *before* JDK major version. Default: detected from pom.xml, falling back to 8. The *after* build always uses JDK 17, the migration target.")
    Integer jdkOverride;

    @Option(names = "--build-timeout-seconds", defaultValue = "600",
            description = "Timeout for the before/after `mvn test` runs.")
    long buildTimeoutSeconds;

    @Option(names = "--rewrite-timeout-seconds", defaultValue = "1200",
            description = "Timeout for the OpenRewrite recipe step. Higher than the build timeout by default: "
                    + "a cold run has to download the whole OpenRewrite dependency tree and fully parse the "
                    + "target repo's source into its own AST before applying the recipe chain, which is "
                    + "substantially heavier than compiling and running tests.")
    long rewriteTimeoutSeconds;

    @Override
    public Integer call() throws Exception {
        String resolvedRunId = runId != null ? runId : RunIds.defaultRunId(repoUrl, commit);
        Path runDir = Path.of("runs", resolvedRunId);
        Path workspace = runDir.resolve("workspace");
        Files.createDirectories(runDir);

        System.out.println("Cloning " + repoUrl + " @ " + commit + " ...");
        new RepoCheckout().checkout(repoUrl, commit, workspace);

        int beforeJdk = jdkOverride != null ? jdkOverride : JdkDetection.detectOrDefault(workspace);
        Duration buildTimeout = Duration.ofSeconds(buildTimeoutSeconds);
        SandboxRunner runner = new TestcontainersSandboxRunner();

        System.out.println("Running baseline build on JDK " + beforeJdk + " (before)...");
        BuildResult beforeBuild = BuildAndTestStep.run(
                runner, workspace, beforeJdk, buildTimeout, runDir.resolve("before-build-output.log"));

        System.out.println("Applying " + SpringBootMigrationRecipe.ACTIVE_RECIPE
                + " (timeout " + rewriteTimeoutSeconds + "s)...");
        SandboxResult rewriteResult = runner.run(
                workspace,
                SpringBootMigrationRecipe.mavenArgs(),
                SpringBootMigrationRecipe.REQUIRED_JDK_MAJOR_VERSION,
                Duration.ofSeconds(rewriteTimeoutSeconds));
        Files.writeString(runDir.resolve("rewrite-output.log"), rewriteResult.output());
        System.out.println("Recipe exit code: " + rewriteResult.exitCode());

        RepoDiff diff = new GitDiffSummarizer().diff(workspace);

        int afterJdk = SpringBootMigrationRecipe.REQUIRED_JDK_MAJOR_VERSION;
        BuildResult afterBuild = null;
        if (rewriteResult.exitCode() == 0) {
            System.out.println("Running post-rewrite build on JDK " + afterJdk + " (after)...");
            afterBuild = BuildAndTestStep.run(
                    runner, workspace, afterJdk, buildTimeout, runDir.resolve("after-build-output.log"));
        } else {
            // A failed or timed-out recipe run leaves the repo unmigrated (or
            // only partially touched) — building it "after" in that state
            // would just measure the original project again and falsely
            // read as a comparison point. Better to say plainly that the
            // recipe didn't finish than to print a number that looks
            // meaningful but isn't.
            System.out.println("Recipe did not complete successfully — skipping the after-build measurement.");
        }

        printSummary(beforeJdk, beforeBuild, rewriteResult, diff, afterJdk, afterBuild);

        RewriteReport report = new RewriteReport(
                repoUrl, commit,
                SpringBootMigrationRecipe.PLUGIN_VERSION,
                SpringBootMigrationRecipe.RECIPE_ARTIFACT_COORDINATES,
                SpringBootMigrationRecipe.ACTIVE_RECIPE,
                beforeJdk, beforeBuild,
                rewriteResult.exitCode(),
                diff,
                afterJdk, afterBuild,
                Instant.now());
        Path reportPath = runDir.resolve("rewrite-report.json");
        writeReport(reportPath, report);
        System.out.println("Report written to " + reportPath);

        return 0;
    }

    private void printSummary(
            int beforeJdk, BuildResult beforeBuild,
            SandboxResult rewriteResult, RepoDiff diff,
            int afterJdk, BuildResult afterBuild) {
        System.out.println();
        System.out.println("=== Rewrite Result ===");
        System.out.println("Before (JDK " + beforeJdk + "): compiled=" + beforeBuild.compiled()
                + " tests=" + beforeBuild.testsTotal()
                + " failed=" + beforeBuild.testsFailed()
                + " errored=" + beforeBuild.testsErrored());
        System.out.println("Recipe exit code: " + rewriteResult.exitCode()
                + (rewriteResult.timedOut() ? " (TIMED OUT)" : ""));
        System.out.println("Files changed: " + diff.fileCount());
        for (RepoDiff.ChangedFile file : diff.changedFiles()) {
            System.out.println("  " + file.changeType() + "  " + file.path());
        }
        if (afterBuild != null) {
            System.out.println("After (JDK " + afterJdk + "):  compiled=" + afterBuild.compiled()
                    + " tests=" + afterBuild.testsTotal()
                    + " failed=" + afterBuild.testsFailed()
                    + " errored=" + afterBuild.testsErrored());
        } else {
            System.out.println("After (JDK " + afterJdk + "):  not measured — recipe did not complete");
        }
        System.out.println();
    }

    private void writeReport(Path path, RewriteReport report) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(path.toFile(), report);
    }
}
