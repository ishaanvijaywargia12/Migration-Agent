package io.migrationagent.guardrails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PathContainmentGuardrailTest {

    private final PathContainmentGuardrail guardrail = new PathContainmentGuardrail();

    @Test
    void acceptsAPatchTouchingOnlyFilesInsideTheRepo(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/main/Foo.java
                +++ b/src/main/Foo.java
                @@ -1,1 +1,1 @@
                -old
                +new
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }

    @Test
    void rejectsADotDotTraversalEscapingTheRepo(@TempDir Path repoRoot) {
        String diff = """
                --- a/../../etc/passwd
                +++ b/../../etc/passwd
                @@ -1,1 +1,1 @@
                -root:x:0:0
                +pwned
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("PATH_CONTAINMENT");
    }
}
