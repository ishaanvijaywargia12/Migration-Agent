package io.migrationagent.rewrite;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Diffs a repo's working tree against its index. Since the OpenRewrite step
 * modifies files without staging or committing anything, and nothing else
 * touches this checkout between clone and rewrite, the index still matches
 * HEAD — so this is effectively "what did the recipe change."
 */
public final class GitDiffSummarizer {

    public RepoDiff diff(Path repoDir) throws IOException {
        try (Git git = Git.open(repoDir.toFile())) {
            List<DiffEntry> entries = git.diff().call();
            List<RepoDiff.ChangedFile> changedFiles = new ArrayList<>();
            Set<String> pathsAlreadySeen = new HashSet<>();
            for (DiffEntry entry : entries) {
                String path = pathOf(entry);
                changedFiles.add(new RepoDiff.ChangedFile(path, categorize(entry)));
                pathsAlreadySeen.add(path);
            }

            // Confirmed empirically (see GitDiffSummarizerTest): unlike
            // native `git diff`, JGit's diff command already reports
            // untracked new files as ADD entries above. This second pass
            // only catches anything JGit's status/diff commands classify
            // differently (e.g. untracked files JGit's diff walk might miss
            // in edge cases) — deduplicated by path so a file already
            // reported above is never double-counted.
            Status status = git.status().call();
            for (String untrackedPath : status.getUntracked()) {
                if (pathsAlreadySeen.add(untrackedPath)) {
                    changedFiles.add(new RepoDiff.ChangedFile(untrackedPath, RepoDiff.ChangeType.ADDED));
                }
            }

            return new RepoDiff(changedFiles);
        } catch (GitAPIException e) {
            throw new IOException("Failed to diff repo at " + repoDir, e);
        }
    }

    private String pathOf(DiffEntry entry) {
        return entry.getChangeType() == DiffEntry.ChangeType.DELETE
                ? entry.getOldPath()
                : entry.getNewPath();
    }

    private RepoDiff.ChangeType categorize(DiffEntry entry) {
        return switch (entry.getChangeType()) {
            case ADD -> RepoDiff.ChangeType.ADDED;
            case DELETE -> RepoDiff.ChangeType.DELETED;
            // RENAME/COPY are rare for what a recipe does and are treated as
            // a modification for MVP purposes — the report cares about
            // "which files did the recipe touch," not renames specifically.
            case MODIFY, RENAME, COPY -> RepoDiff.ChangeType.MODIFIED;
        };
    }
}
