package io.migrationagent.guardrails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoTestDeletionGuardrailTest {

    private final NoTestDeletionGuardrail guardrail = new NoTestDeletionGuardrail();

    @Test
    void acceptsANormalChangeToATestFile(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,1 +1,1 @@
                -int x = 1;
                +int x = 2;
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }

    @Test
    void rejectsDeletingAWholeTestFile(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ /dev/null
                @@ -1,5 +0,0 @@
                -class FooTest {
                -  @Test
                -  void works() {}
                -}
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_TEST_DELETION");
    }

    @Test
    void rejectsRemovingATestAnnotatedMethodWithoutDeletingTheWholeFile(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,8 +1,4 @@
                 class FooTest {
                -  @Test
                -  void inconvenientlyFailingTest() {
                -    assertTrue(false);
                -  }
                   @Test
                   void anotherTest() {}
                 }
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_TEST_DELETION");
    }

    @Test
    void ignoresFileDeletionsOutsideTheTestRoot(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/main/java/Unused.java
                +++ /dev/null
                @@ -1,1 +0,0 @@
                -class Unused {}
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }
}
