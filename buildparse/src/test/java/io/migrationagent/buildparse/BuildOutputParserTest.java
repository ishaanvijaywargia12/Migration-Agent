package io.migrationagent.buildparse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the facade wires the two parsers together correctly — the
 * parsing details of each are covered by their own dedicated tests.
 */
class BuildOutputParserTest {

    private final BuildOutputParser parser = new BuildOutputParser();

    @Test
    void aCompiledBuildWithFailingTestsReportsCompiledTrueAndCombinesSurefireFailures() throws Exception {
        // Maven exits non-zero when Surefire reports failures, even though
        // compilation itself succeeded — testsRan is what should decide
        // `compiled` here, not the exit code.
        BuildResult result = parser.parse(
                "[INFO] no compile errors here\n", fixtureRepoRoot(), Duration.ofSeconds(12), 1);

        assertThat(result.compiled()).isTrue();
        assertThat(result.testsRan()).isTrue();
        assertThat(result.testsTotal()).isEqualTo(7);
        assertThat(result.testsFailed()).isEqualTo(1);
        assertThat(result.testsErrored()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.wallClock()).isEqualTo(Duration.ofSeconds(12));
        assertThat(result.mavenExitCode()).isEqualTo(1);
    }

    @Test
    void aCompileFailureReportsCompiledFalseAndTestsRanFalseWithNoSurefireReports(@TempDir Path emptyRepoRoot)
            throws Exception {
        BuildResult result = parser.parse(
                readConsoleFixture("compile-error-console.txt"), emptyRepoRoot, Duration.ofSeconds(3), 1);

        assertThat(result.compiled()).isFalse();
        assertThat(result.testsRan()).isFalse();
        assertThat(result.testsTotal()).isZero();
        assertThat(result.failureCount()).isEqualTo(2);
    }

    @Test
    void aFailureBeforeCompilationReportsCompiledFalseEvenWithNoMatchingErrorLines(@TempDir Path emptyRepoRoot)
            throws Exception {
        // Regression case: a plugin exception during an early lifecycle phase
        // (e.g. a `validate`-bound formatter plugin crashing) produces no
        // javac-style [ERROR] line and no surefire reports, but Maven still
        // exits non-zero. `compiled` must not default to true just because
        // the compile-error regex found nothing to match.
        BuildResult result = parser.parse(
                "[ERROR] Failed to execute goal ... PluginContainerException\n",
                emptyRepoRoot, Duration.ofSeconds(1), 1);

        assertThat(result.compiled()).isFalse();
        assertThat(result.testsRan()).isFalse();
    }

    private Path fixtureRepoRoot() throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource("surefire-reports").toURI()).getParent();
    }

    private String readConsoleFixture(String name) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("console/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
