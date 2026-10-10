package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProposePatchToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void appliesAValidUnifiedDiffToATrackedFile(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            Files.writeString(repoRoot.resolve("Foo.java"), "line1\nline2\nline3\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setSign(false).call();
        }

        ProposePatchTool tool = new ProposePatchTool(repoRoot);
        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,3 @@
                 line1
                -line2
                +line2-changed
                 line3
                """;

        String result = tool.execute(args(diff, "fix line2"));

        assertThat(result).startsWith("Applied");
        assertThat(Files.readString(repoRoot.resolve("Foo.java"))).contains("line2-changed");
    }

    @Test
    void rejectsADiffTouchingAPathOutsideTheRepoWithoutCallingGitApplyAtAll(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            git.commit().setMessage("empty initial commit").setAllowEmpty(true).setSign(false).call();
        }

        ProposePatchTool tool = new ProposePatchTool(repoRoot);
        String diff = """
                --- a/../../../etc/passwd
                +++ b/../../../etc/passwd
                @@ -1,1 +1,1 @@
                -root:x:0:0
                +pwned
                """;

        String result = tool.execute(args(diff, "malicious"));

        assertThat(result).startsWith("REJECTED");
        assertThat(result).contains("outside the repository");
    }

    @Test
    void rejectsADiffThatDoesNotApplyCleanly(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            Files.writeString(repoRoot.resolve("Foo.java"), "actual content\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setSign(false).call();
        }

        ProposePatchTool tool = new ProposePatchTool(repoRoot);
        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,1 +1,1 @@
                -this context does not match the real file
                +replacement
                """;

        String result = tool.execute(args(diff, "bogus"));

        assertThat(result).startsWith("REJECTED");
    }

    private JsonNode args(String diff, String rationale) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(java.util.Map.of(
                "unified_diff", diff, "rationale", rationale)));
    }
}
