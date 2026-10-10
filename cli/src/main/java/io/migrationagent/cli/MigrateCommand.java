package io.migrationagent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.migrationagent.agent.AgentLoop;
import io.migrationagent.agent.AgentTool;
import io.migrationagent.agent.GetDependencyTreeTool;
import io.migrationagent.agent.LookupMigrationNoteTool;
import io.migrationagent.agent.ModelCascade;
import io.migrationagent.agent.ProposePatchTool;
import io.migrationagent.agent.ReadFileTool;
import io.migrationagent.agent.RunBuildTool;
import io.migrationagent.agent.RunTestsTool;
import io.migrationagent.agent.SearchCodeTool;
import io.migrationagent.agent.TraceWriter;
import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.guardrails.PostSuccessVerifier;
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
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Phase 1 + 2 + 3 + 4 + 5 end to end: baseline, apply the OpenRewrite
 * recipe, run the hand-written agent loop (deterministic triage fixes
 * first, then the model cascade, full {@code GuardrailEngine} validation on
 * every proposed patch) against whatever the recipe didn't already fix,
 * then run {@link PostSuccessVerifier} as a final backstop against the
 * original baseline before ever reporting success.
 */
@Command(name = "migrate", mixinStandardHelpOptions = true,
        description = "Baseline, apply the OpenRewrite recipe, then run the agent loop on whatever still fails.")
final class MigrateCommand implements Callable<Integer> {

    @Option(names = "--repo-url", required = true, description = "Public Git repository URL")
    String repoUrl;

    @Option(names = "--commit", required = true, description = "Commit SHA to check out")
    String commit;

    @Option(names = "--run-id", description = "Defaults to <repo-name>-<short-sha>-<timestamp>")
    String runId;

    @Option(names = "--jdk", description = "Override the *before* JDK major version. Default: detected from pom.xml.")
    Integer jdkOverride;

    @Option(names = "--build-timeout-seconds", defaultValue = "600")
    long buildTimeoutSeconds;

    @Option(names = "--rewrite-timeout-seconds", defaultValue = "1200")
    long rewriteTimeoutSeconds;

    @Option(names = "--provider", description = "Use only this one provider instead of the full cascade in config/providers.yaml.")
    String provider;

    @Option(names = "--budget-steps", defaultValue = "10", description = "Max agent loop iterations.")
    int budgetSteps;

    @Option(names = "--env-file", defaultValue = ".env",
            description = "Path to a .env file with your own provider API keys (bring your own key).")
    String envFile;

    @Option(names = "--providers-config", defaultValue = "config/providers.yaml",
            description = "Path to a providers.yaml config — override to use your own providers/models.")
    String providersConfig;

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

        if (rewriteResult.exitCode() != 0) {
            System.out.println("Recipe did not complete successfully — stopping before the agent loop.");
            writeReportAndExit(runDir, beforeJdk, beforeBuild, rewriteResult.exitCode(), diff,
                    afterJdk, null, false, List.of(), beforeBuild, List.of());
            return 0;
        }

        System.out.println("Running post-rewrite build on JDK " + afterJdk + " (after)...");
        BuildResult afterRewriteBuild = BuildAndTestStep.run(
                runner, workspace, afterJdk, buildTimeout, runDir.resolve("after-build-output.log"));

        BuildResult finalBuild = afterRewriteBuild;
        boolean agentLoopUsed = false;
        List<String> cascadeProviders = List.of();

        if (AgentLoop.isResolved(afterRewriteBuild)) {
            System.out.println("Recipe alone already resolved everything — no agent loop needed.");
        } else {
            ModelCascade cascade = provider != null
                    ? singleProviderCascade(provider)
                    : ProviderBootstrap.loadCascade(Path.of(providersConfig), Path.of(envFile));
            cascadeProviders = cascade.allProviderNames();
            System.out.println("Starting agent loop (cascade=" + cascadeProviders + ", budget=" + budgetSteps + " steps)...");

            List<AgentTool> tools = List.of(
                    new ReadFileTool(workspace),
                    new SearchCodeTool(workspace),
                    new ProposePatchTool(workspace),
                    new RunBuildTool(runner, workspace, afterJdk, buildTimeout),
                    new RunTestsTool(runner, workspace, afterJdk, buildTimeout),
                    new GetDependencyTreeTool(runner, workspace, afterJdk, buildTimeout),
                    new LookupMigrationNoteTool(Path.of("docs", "migration-notes")));

            try (TraceWriter trace = new TraceWriter(runDir.resolve("trace.jsonl"))) {
                AgentLoop loop = new AgentLoop(
                        cascade, tools, runner, workspace,
                        afterJdk, buildTimeout, trace, runDir.resolve("patches"), budgetSteps);
                finalBuild = loop.run(afterRewriteBuild);
            }
            agentLoopUsed = true;
        }

        List<String> postSuccessViolations = List.of();
        if (AgentLoop.isResolved(finalBuild)) {
            PostSuccessVerifier.PostSuccessVerdict verdict =
                    new PostSuccessVerifier().verify(beforeBuild, finalBuild);
            postSuccessViolations = verdict.violations();
            if (!verdict.passed()) {
                System.out.println("WARNING: reported success failed post-success verification: "
                        + postSuccessViolations);
            }
        }

        printSummary(beforeBuild, rewriteResult, diff, afterRewriteBuild, agentLoopUsed, finalBuild, postSuccessViolations);
        writeReportAndExit(runDir, beforeJdk, beforeBuild, rewriteResult.exitCode(), diff,
                afterJdk, afterRewriteBuild, agentLoopUsed, cascadeProviders, finalBuild, postSuccessViolations);
        return 0;
    }

    private ModelCascade singleProviderCascade(String providerName) throws IOException {
        ProviderBootstrap.Bootstrapped bootstrapped =
                ProviderBootstrap.load(providerName, Path.of(providersConfig), Path.of(envFile));
        return ModelCascade.singleTier(bootstrapped.chatClient(), bootstrapped.providerName(), bootstrapped.model());
    }

    private void printSummary(
            BuildResult beforeBuild, SandboxResult rewriteResult, RepoDiff diff,
            BuildResult afterRewriteBuild, boolean agentLoopUsed, BuildResult finalBuild,
            List<String> postSuccessViolations) {
        System.out.println();
        System.out.println("=== Migrate Result ===");
        System.out.println("Before:        compiled=" + beforeBuild.compiled() + " tests=" + beforeBuild.testsTotal());
        System.out.println("Recipe:        exit=" + rewriteResult.exitCode() + " filesChanged=" + diff.fileCount());
        System.out.println("After rewrite: compiled=" + afterRewriteBuild.compiled()
                + " tests=" + afterRewriteBuild.testsTotal()
                + " failed=" + afterRewriteBuild.testsFailed());
        System.out.println("Agent loop used: " + agentLoopUsed);
        System.out.println("Final:         resolved=" + AgentLoop.isResolved(finalBuild)
                + " compiled=" + finalBuild.compiled()
                + " tests=" + finalBuild.testsTotal()
                + " failed=" + finalBuild.testsFailed()
                + " errored=" + finalBuild.testsErrored());
        System.out.println("Post-success verification: " + (postSuccessViolations.isEmpty() ? "passed" : "FAILED " + postSuccessViolations));
        System.out.println();
    }

    private void writeReportAndExit(
            Path runDir, int beforeJdk, BuildResult beforeBuild, int recipeExitCode, RepoDiff diff,
            int afterJdk, BuildResult afterRewriteBuild, boolean agentLoopUsed,
            List<String> cascadeProviders, BuildResult finalBuild,
            List<String> postSuccessViolations) throws IOException {
        MigrateReport report = new MigrateReport(
                repoUrl, commit,
                SpringBootMigrationRecipe.PLUGIN_VERSION,
                SpringBootMigrationRecipe.RECIPE_ARTIFACT_COORDINATES,
                SpringBootMigrationRecipe.ACTIVE_RECIPE,
                beforeJdk, beforeBuild, recipeExitCode, diff,
                afterJdk, afterRewriteBuild, agentLoopUsed, cascadeProviders,
                budgetSteps, finalBuild, postSuccessViolations, Instant.now());
        Path reportPath = runDir.resolve("migrate-report.json");
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(reportPath.toFile(), report);
        System.out.println("Report written to " + reportPath);
    }
}
