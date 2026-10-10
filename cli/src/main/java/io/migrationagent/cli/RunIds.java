package io.migrationagent.cli;

import java.time.Instant;

/**
 * Consistent run-id naming across subcommands, so every run's artifacts land
 * under a unique `runs/<run-id>/` and never overwrite a previous run.
 */
final class RunIds {

    private RunIds() {
    }

    static String defaultRunId(String repoUrl, String commit) {
        String repoName = repoUrl.replaceAll("/+$", "");
        repoName = repoName.substring(repoName.lastIndexOf('/') + 1).replaceAll("\\.git$", "");
        String shortSha = commit.length() >= 7 ? commit.substring(0, 7) : commit;
        return repoName + "-" + shortSha + "-" + Instant.now().getEpochSecond();
    }
}
