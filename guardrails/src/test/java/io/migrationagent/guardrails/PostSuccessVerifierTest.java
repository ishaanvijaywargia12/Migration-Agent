package io.migrationagent.guardrails;

import io.migrationagent.buildparse.BuildResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostSuccessVerifierTest {

    private final PostSuccessVerifier verifier = new PostSuccessVerifier();

    @Test
    void passesWhenTestCountAndSkipsAreUnchanged() {
        BuildResult baseline = result(41, 0, 1);
        BuildResult finalResult = result(41, 0, 1);

        var verdict = verifier.verify(baseline, finalResult);

        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.violations()).isEmpty();
    }

    @Test
    void failsWhenTheFinalTestCountIsLowerThanBaseline() {
        BuildResult baseline = result(41, 0, 1);
        BuildResult finalResult = result(38, 0, 1);

        var verdict = verifier.verify(baseline, finalResult);

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.violations()).anyMatch(v -> v.contains("test count decreased"));
    }

    @Test
    void failsWhenMoreTestsAreSkippedThanBefore() {
        BuildResult baseline = result(41, 0, 1);
        BuildResult finalResult = result(41, 0, 5);

        var verdict = verifier.verify(baseline, finalResult);

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.violations()).anyMatch(v -> v.contains("skipped test count increased"));
    }

    private BuildResult result(int total, int failed, int skipped) {
        return new BuildResult(true, true, total, failed, 0, skipped, List.of(), Duration.ZERO, 0);
    }
}
