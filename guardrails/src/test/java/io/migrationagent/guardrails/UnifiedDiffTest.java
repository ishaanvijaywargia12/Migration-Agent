package io.migrationagent.guardrails;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDiffTest {

    @Test
    void parsesOldAndNewPathsAndContentLines() {
        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,3 @@
                 line1
                -line2
                +line2-changed
                 line3
                """;

        List<UnifiedDiff.FileDiff> files = UnifiedDiff.parse(diff);

        assertThat(files).hasSize(1);
        UnifiedDiff.FileDiff file = files.get(0);
        assertThat(file.oldPath()).isEqualTo("Foo.java");
        assertThat(file.newPath()).isEqualTo("Foo.java");
        assertThat(file.removedLines()).containsExactly("line2");
        assertThat(file.addedLines()).containsExactly("line2-changed");
    }

    @Test
    void recognizesFullFileDeletionViaDevNull() {
        String diff = """
                --- a/Obsolete.java
                +++ /dev/null
                @@ -1,1 +0,0 @@
                -class Obsolete {}
                """;

        UnifiedDiff.FileDiff file = UnifiedDiff.parse(diff).get(0);

        assertThat(file.isFullFileDeletion()).isTrue();
    }

    @Test
    void handlesMultipleFilesInOneDiff() {
        String diff = """
                --- a/A.java
                +++ b/A.java
                @@ -1,1 +1,1 @@
                -old
                +new
                --- a/B.java
                +++ b/B.java
                @@ -1,1 +1,1 @@
                -foo
                +bar
                """;

        List<UnifiedDiff.FileDiff> files = UnifiedDiff.parse(diff);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).oldPath()).isEqualTo("A.java");
        assertThat(files.get(1).oldPath()).isEqualTo("B.java");
    }
}
