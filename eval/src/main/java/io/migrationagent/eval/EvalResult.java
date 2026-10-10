package io.migrationagent.eval;

import java.util.Map;

/**
 * One row of the evaluation results table: one (repo, ablation mode) run.
 * Every field here comes from a real recorded run — DESIGN.md's evaluation
 * section is explicit that nothing here may be estimated or invented.
 *
 * @param recipeExitCode nullable — {@code null} for {@link AblationMode#LLM_ONLY},
 *                        which never runs the recipe at all
 * @param filesChangedByRecipe nullable, same reason
 */
public record EvalResult(
        String repoName,
        AblationMode ablationMode,
        boolean baselineCompiled,
        int baselineTestsTotal,
        Integer recipeExitCode,
        Integer filesChangedByRecipe,
        boolean finalResolved,
        boolean finalCompiled,
        int finalTestsTotal,
        int finalTestsFailed,
        int finalTestsErrored,
        boolean testsPreserved,
        int iterations,
        int modelCalls,
        int totalTokensIn,
        int totalTokensOut,
        long wallClockSeconds,
        int rulesFixedCount,
        Map<Integer, Integer> iterationsImprovedByTier,
        String finalStatus
) {
}
