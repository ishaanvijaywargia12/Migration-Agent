package io.migrationagent.rewrite;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitDiffSummarizerTest {

    private final GitDiffSummarizer summarizer = new GitDiffSummarizer();

    @Test
    void detectsModifiedAddedAndDeletedFiles(@TempDir Path repoDir) throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve("pom.xml"), "<project>v1</project>");
            Files.writeString(repoDir.resolve("ToDelete.java"), "class ToDelete {}");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").setSign(false).call();

            // Simulate what the recipe does: modify one file, create a new
            // one, delete another — all without staging or committing.
            Files.writeString(repoDir.resolve("pom.xml"), "<project>v2</project>");
            Files.writeString(repoDir.resolve("NewFile.java"), "class NewFile {}");
            Files.delete(repoDir.resolve("ToDelete.java"));

            RepoDiff diff = summarizer.diff(repoDir);

            assertThat(diff.fileCount()).isEqualTo(3);
            assertThat(diff.changedFiles())
                    .contains(new RepoDiff.ChangedFile("pom.xml", RepoDiff.ChangeType.MODIFIED))
                    .contains(new RepoDiff.ChangedFile("NewFile.java", RepoDiff.ChangeType.ADDED))
                    .contains(new RepoDiff.ChangedFile("ToDelete.java", RepoDiff.ChangeType.DELETED));
        }
    }

    @Test
    void reportsNoChangesForAnUntouchedRepo(@TempDir Path repoDir) throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve("pom.xml"), "<project/>");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").setSign(false).call();

            RepoDiff diff = summarizer.diff(repoDir);

            assertThat(diff.fileCount()).isZero();
        }
    }
}
