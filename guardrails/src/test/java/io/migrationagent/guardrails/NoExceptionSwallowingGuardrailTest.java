package io.migrationagent.guardrails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoExceptionSwallowingGuardrailTest {

    private final NoExceptionSwallowingGuardrail guardrail = new NoExceptionSwallowingGuardrail();

    @Test
    void rejectsAnEmptyCatchBlockAddedInATestFile(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,3 +1,6 @@
                 void works() {
                +  try {
                +    service.doSomethingThatThrows();
                +  } catch (Exception e) {
                +  }
                 }
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_EXCEPTION_SWALLOWING");
    }

    @Test
    void rejectsALogOnlyCatchBlock(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,3 +1,6 @@
                 void works() {
                +  try {
                +    service.doSomethingThatThrows();
                +  } catch (Exception e) {
                +    log.warn("ignoring", e);
                +  }
                 }
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_EXCEPTION_SWALLOWING");
    }

    @Test
    void acceptsACatchBlockThatRethrows(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,3 +1,6 @@
                 void works() {
                +  try {
                +    service.doSomethingThatThrows();
                +  } catch (Exception e) {
                +    throw new RuntimeException(e);
                +  }
                 }
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }

    @Test
    void acceptsACatchBlockThatAssertsOnTheException(@TempDir Path repoRoot) {
        String diff = """
                --- a/src/test/java/FooTest.java
                +++ b/src/test/java/FooTest.java
                @@ -1,3 +1,6 @@
                 void works() {
                +  try {
                +    service.doSomethingThatThrows();
                +  } catch (Exception e) {
                +    assertEquals("expected", e.getMessage());
                +  }
                 }
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }
}
