package io.migrationagent.cli;

import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.rewrite.RepoDiff;

import java.time.Instant;
import java.util.List;

/**
 * What gets written to {@code runs/<run-id>/migrate-report.json}: the full
 * Phase 1+2+3+4+5 pipeline in one run — baseline, OpenRewrite recipe, (if
 * needed) the agent loop with its model cascade, and
 * {@code PostSuccessVerifier}'s final check.
 *
 * @param agentLoopUsed          whether the agent loop ran at all — false
 *                               means the deterministic recipe alone
 *                               already left the repo compiling with all
 *                               tests passing
 * @param cascadeProviders       the provider names in cascade order at the
 *                               start of the run (empty if the agent loop
 *                               wasn't used) — which tier actually served
 *                               each call is in {@code trace.jsonl}, not
 *                               repeated here
 * @param finalBuild             the last measured state: either the
 *                               post-rewrite build directly (agent loop not
 *                               needed) or the agent loop's own final
 *                               measurement
 * @param postSuccessViolations  empty if {@code finalBuild} wasn't resolved,
 *                               or if it was and passed verification;
 *                               non-empty means {@code isResolved} was true
 *                               but the result shouldn't be trusted anyway
 *                               (e.g. test count dropped) — see
 *                               {@code PostSuccessVerifier}
 */
public record MigrateReport(
        String repoUrl,
        String commit,
        String recipePluginVersion,
        String recipeArtifactCoordinates,
        String activeRecipe,
        int beforeJdkMajorVersion,
        BuildResult beforeBuild,
        int recipeExitCode,
        RepoDiff diff,
        int afterJdkMajorVersion,
        BuildResult afterRewriteBuild,
        boolean agentLoopUsed,
        List<String> cascadeProviders,
        int agentIterationBudget,
        BuildResult finalBuild,
        List<String> postSuccessViolations,
        Instant startedAt
) {
}
