package io.migrationagent.cli;

import io.migrationagent.buildparse.BuildResult;

import java.time.Instant;

/**
 * What gets written to {@code runs/<run-id>/baseline.json}. Every field
 * here is either an input the user gave us or a number that came out of a
 * real sandbox build — nothing in this record is estimated.
 */
public record BaselineReport(
        String repoUrl,
        String commitSha,
        int jdkMajorVersion,
        Instant startedAt,
        BuildResult result
) {
}
