package io.migrationagent.cli;

import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.rewrite.RepoDiff;

import java.time.Instant;

/**
 * What gets written to {@code runs/<run-id>/rewrite-report.json}: the
 * recipe applied, what it changed on disk, and a real build/test
 * measurement both before and after — all measured, nothing estimated.
 *
 * @param afterBuild {@code null} when {@code recipeExitCode != 0} — a failed
 *                   or timed-out recipe run leaves the repo unmigrated (or
 *                   only partially touched), so building it "after" would
 *                   just re-measure the original project and read as a
 *                   false comparison point.
 */
public record RewriteReport(
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
        BuildResult afterBuild,
        Instant startedAt
) {
}
