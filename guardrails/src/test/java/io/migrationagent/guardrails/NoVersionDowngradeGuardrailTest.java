package io.migrationagent.guardrails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoVersionDowngradeGuardrailTest {

    private final NoVersionDowngradeGuardrail guardrail = new NoVersionDowngradeGuardrail();

    @Test
    void acceptsAnUpgrade(@TempDir Path repoRoot) {
        String diff = """
                --- a/pom.xml
                +++ b/pom.xml
                @@ -1,1 +1,1 @@
                -<version>3.0.0</version>
                +<version>3.5.0</version>
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }

    @Test
    void rejectsDowngradingSpringBootBelowMajorVersion3(@TempDir Path repoRoot) {
        String diff = """
                --- a/pom.xml
                +++ b/pom.xml
                @@ -1,1 +1,1 @@
                -<version>3.0.0</version>
                +<version>2.7.3</version>
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_VERSION_DOWNGRADE");
    }

    @Test
    void rejectsDowngradingJavaVersionBelow17(@TempDir Path repoRoot) {
        String diff = """
                --- a/pom.xml
                +++ b/pom.xml
                @@ -1,1 +1,1 @@
                -<maven.compiler.release>17</maven.compiler.release>
                +<maven.compiler.release>11</maven.compiler.release>
                """;

        var verdict = guardrail.check(diff, repoRoot);

        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.ruleName()).isEqualTo("NO_VERSION_DOWNGRADE");
    }

    @Test
    void ignoresNonPomFiles(@TempDir Path repoRoot) {
        String diff = """
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,1 @@
                -<version>3.0.0</version>
                +<version>2.7.3</version>
                """;

        assertThat(guardrail.check(diff, repoRoot).accepted()).isTrue();
    }
}
