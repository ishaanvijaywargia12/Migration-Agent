package io.migrationagent.rewrite;

import java.util.List;

/**
 * Summary of what the recipe actually changed on disk, computed from git's
 * working-tree state (the recipe modifies files but never commits) rather
 * than by parsing the recipe's own log output.
 */
public record RepoDiff(List<ChangedFile> changedFiles) {

    public enum ChangeType { ADDED, MODIFIED, DELETED }

    public record ChangedFile(String path, ChangeType changeType) {
    }

    public int fileCount() {
        return changedFiles.size();
    }
}
