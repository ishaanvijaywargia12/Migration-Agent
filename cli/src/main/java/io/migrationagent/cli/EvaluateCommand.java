package io.migrationagent.cli;

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
import io.migrationagent.eval.AblationMode;
import io.migrationagent.eval.BenchmarkManifest;
import io.migrationagent.eval.BenchmarkRepo;
import io.migrationagent.eval.EvalResult;
import io.migrationagent.eval.ResultsTableWriter;
import io.migrationagent.eval.TraceAnalyzer;
import io.migrationagent.rewrite.GitDiffSummarizer;
import io.migrationagent.rewrite.RepoDiff;
import io.migrationagent.rewrite.SpringBootMigrationRecipe;
import io.migrationagent.rewrite.SpringBootVersionDetector;
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
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Phase 6: runs one (repo, {@link AblationMode}) pair from
 * {@code benchmark/manifest.yaml} and appends one row to a Markdown results
 * table. Deliberately one pair per invocation rather than looping over the
 * whole ablation matrix automatically — each run is a real Docker build and,
 * for every mode except {@code OPENREWRITE_ONLY}, real free-tier API spend,
 * so which combinations actually get run is a decision left to whoever's
 * paying (in time, if nothing else) for them.
 *
 * <p>This necessarily duplicates a fair amount of {@link MigrateCommand}'s
 * orchestration (baseline → recipe → agent loop) rather than sharing a
 * common pipeline class — a deliberate scope call made late in the project
 * rather than risk refactoring already-verified-live orchestration code.
 * Worth unifying in a future pass if this file keeps growing.
 */
@Command(name = "evaluate", mixinStandardHelpOptions = true,
        description = "Run one ablation mode against one benchmark repo and append a row to the results table.")
final class EvaluateCommand implements Callable<Integer> {

    @Option(names = "--manifest", defaultValue = "benchmark/manifest.yaml")
    String manifestPath;

    @Option(names = "--repo", required = true, description = "Repo name from the manifest, e.g. spring-petclinic")
    String repoName;

    @Option(names = "--ablation", required = true, description = "One of: ${COMPLETION-CANDIDATES}")
    AblationMode ablation;

    @Option(names = "--results-file", defaultValue = "runs/eval-results.md")
    String resultsFile;

    @Option(names = "--run-id")
    String runId;

    @Option(names = "--build-timeout-seconds", defaultValue = "600")
    long buildTimeoutSeconds;

    @Option(names = "--rewrite-timeout-seconds", defaultValue = "1200")
    long rewriteTimeoutSeconds;

    @Option(names = "--budget-steps", defaultValue = "10")
    int budgetSteps;

    @Option(names = "--env-file", defaultValue = ".env",
            description = "Path to a .env file with your own provider API keys (bring your own key).")
    String envFile;

    @Option(names = "--providers-config", defaultValue = "config/providers.yaml",
            description = "Path to a providers.yaml config — override to use your own providers/models.")
    String providersConfig;

    private static final int TARGET_JDK = SpringBootMigrationRecipe.REQUIRED_JDK_MAJOR_VERSION;

    @Override
    public Integer call() throws Exception {
        BenchmarkManifest manifest = BenchmarkManifest.load(Path.of(manifestPath));
        BenchmarkRepo repo = manifest.get(repoName);

        String resolvedRunId = runId != null ? runId : RunIds.defaultRunId(repo.repoUrl(), repo.commitSha())
                + "-" + ablation.name().toLowerCase();
        Path runDir = Path.of("runs", resolvedRunId);
        Path workspace = runDir.resolve("workspace");
        Files.createDirectories(runDir);

        Instant wallClockStart = Instant.now();
        System.out.println("Cloning " + repo.repoUrl() + " @ " + repo.commitSha() + " for ablation " + ablation + "...");
        new RepoCheckout().checkout(repo.repoUrl(), repo.commitSha(), workspace);

        int beforeJdk = JdkDetection.detectOrDefault(workspace);
        Duration buildTimeout = Duration.ofSeconds(buildTimeoutSeconds);
        SandboxRunner runner = new TestcontainersSandboxRunner();

        System.out.println("Running baseline build on JDK " + beforeJdk + "...");
        BuildResult baselineBuild = BuildAndTestStep.run(
                runner, workspace, beforeJdk, buildTimeout, runDir.resolve("baseline-build-output.log"));

        Integer recipeExitCode = null;
        Integer filesChanged = null;
        BuildResult preAgentBuild = baselineBuild;

        if (ablation != AblationMode.LLM_ONLY) {
            System.out.println("Applying " + SpringBootMigrationRecipe.ACTIVE_RECIPE + "...");
            SandboxResult rewriteResult = runner.run(
                    workspace, SpringBootMigrationRecipe.mavenArgs(), TARGET_JDK,
                    Duration.ofSeconds(rewriteTimeoutSeconds));
            Files.writeString(runDir.resolve("rewrite-output.log"), rewriteResult.output());
            recipeExitCode = rewriteResult.exitCode();

            RepoDiff diff = new GitDiffSummarizer().diff(workspace);
            filesChanged = diff.fileCount();

            if (rewriteResult.exitCode() == 0) {
                System.out.println("Running post-rewrite build on JDK " + TARGET_JDK + "...");
                preAgentBuild = BuildAndTestStep.run(
                        runner, workspace, TARGET_JDK, buildTimeout, runDir.resolve("after-rewrite-build-output.log"));
            } else {
                System.out.println("Recipe did not complete successfully.");
                preAgentBuild = baselineBuild;
            }
        } else {
            // No recipe is applied in LLM_ONLY, but the agent loop still targets
            // TARGET_JDK — reusing the JDK-8 baselineBuild here would let a repo
            // whose untouched code already compiles/passes look "already resolved"
            // before the loop ever runs, without ever having attempted JDK 17 or a
            // Boot 3 dependency. Building on TARGET_JDK first forces a real signal.
            System.out.println("Running pre-agent build on JDK " + TARGET_JDK + " (no recipe applied, LLM_ONLY)...");
            preAgentBuild = BuildAndTestStep.run(
                    runner, workspace, TARGET_JDK, buildTimeout, runDir.resolve("after-rewrite-build-output.log"));
        }

        BuildResult finalBuild = preAgentBuild;
        TraceAnalyzer.TraceSummary traceSummary = new TraceAnalyzer.TraceSummary(
                0, 0, Map.of(), 0, 0, Map.of(), "n/a", 0);

        boolean runsAgentLoop = ablation == AblationMode.LLM_ONLY
                || (ablation != AblationMode.OPENREWRITE_ONLY && recipeExitCode != null && recipeExitCode == 0
                    && !AgentLoop.isResolved(preAgentBuild));

        if (runsAgentLoop) {
            System.out.println("Running agent loop for ablation " + ablation + "...");
            ModelCascade cascade = ablation == AblationMode.HYBRID_NO_CASCADE
                    ? singleTierOnly(ProviderBootstrap.loadCascade(Path.of(providersConfig), Path.of(envFile)))
                    : ProviderBootstrap.loadCascade(Path.of(providersConfig), Path.of(envFile));
            boolean triageEnabled = ablation != AblationMode.HYBRID_NO_TRIAGE;

            List<AgentTool> tools = List.of(
                    new ReadFileTool(workspace),
                    new SearchCodeTool(workspace),
                    new ProposePatchTool(workspace),
                    new RunBuildTool(runner, workspace, TARGET_JDK, buildTimeout),
                    new RunTestsTool(runner, workspace, TARGET_JDK, buildTimeout),
                    new GetDependencyTreeTool(runner, workspace, TARGET_JDK, buildTimeout),
                    new LookupMigrationNoteTool(Path.of("docs", "migration-notes")));

            Path traceFile = runDir.resolve("trace.jsonl");
            try (TraceWriter trace = new TraceWriter(traceFile)) {
                AgentLoop loop = new AgentLoop(
                        cascade, tools, runner, workspace, TARGET_JDK, buildTimeout,
                        trace, runDir.resolve("patches"), budgetSteps, triageEnabled);
                finalBuild = loop.run(preAgentBuild);
            }
            traceSummary = new TraceAnalyzer().analyze(traceFile);
        }

        long wallClockSeconds = Duration.between(wallClockStart, Instant.now()).toSeconds();
        boolean testsPreserved = finalBuild.testsTotal() >= baselineBuild.testsTotal()
                && finalBuild.testsSkipped() <= baselineBuild.testsSkipped();

        // A clean build/test pass alone can't distinguish "migrated to Boot 3"
        // from "old Boot 2.7 code happened to still compile on JDK 17 without
        // migrating at all" — only matters for ablations that can skip the
        // OpenRewrite recipe entirely (LLM_ONLY), but checked unconditionally
        // for consistency; every other mode already bumps this via the recipe.
        String finalPomXml = Files.readString(workspace.resolve("pom.xml"));
        boolean actuallyOnBoot3 = new SpringBootVersionDetector().isBoot3OrHigher(finalPomXml);
        boolean finalResolved = AgentLoop.isResolved(finalBuild) && actuallyOnBoot3;

        EvalResult result = new EvalResult(
                repoName, ablation,
                baselineBuild.compiled(), baselineBuild.testsTotal(),
                recipeExitCode, filesChanged,
                finalResolved, finalBuild.compiled(),
                finalBuild.testsTotal(), finalBuild.testsFailed(), finalBuild.testsErrored(),
                testsPreserved,
                traceSummary.iterationsCompleted(), traceSummary.totalModelCalls(),
                traceSummary.totalTokensIn(), traceSummary.totalTokensOut(),
                wallClockSeconds, traceSummary.ruleFixCount(),
                traceSummary.iterationsImprovedByTier(), traceSummary.finalStatus());

        new ResultsTableWriter().appendRow(Path.of(resultsFile), result);
        System.out.println();
        System.out.println("=== Eval Result (" + repoName + " / " + ablation + ") ===");
        System.out.println("Resolved: " + result.finalResolved() + "  Tests preserved: " + result.testsPreserved());
        System.out.println("Appended to " + resultsFile);
        return 0;
    }

    private ModelCascade singleTierOnly(ModelCascade fullCascade) {
        ModelCascade.Tier firstTier = fullCascade.current();
        return ModelCascade.singleTier(firstTier.chatClient(), firstTier.providerName(), firstTier.model());
    }
}
