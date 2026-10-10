package io.migrationagent.guardrails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoTestWeakeningGuardrailTest {

    private final NoTestWeakeningGuardrail guardrail = new NoTestWeakeningGuardrail();

    @Test
    void acceptsAPatchThatKeepsTheSameNumberOfAssertions(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,2 +1,2 @@
                -assertEquals(1, x);
                +assertEquals(2, x);
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }

    @Test
    void rejectsAddingDisabledAnnotationInATestFile(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,2 +1,3 @@
                 @Test
                +@Disabled("flaky after migration")
                 void works() {}
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_TEST_WEAKENING");
    }

    @Test
    void rejectsRemovingAnAssertionWithoutReplacingIt(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,3 +1,1 @@
                 void works() {
                -  assertTrue(service.isHealthy());
                -  assertEquals(42, service.answer());
                +  // checks removed, assumed fine
                 }
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_TEST_WEAKENING");
    }

    @Test
    void ignoresNonTestFiles(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/main/java/Foo.java
                +++ b/src/main/java/Foo.java
                @@ -1,2 +1,1 @@
                -assertEquals(1, x);
                +// removed
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }
}
