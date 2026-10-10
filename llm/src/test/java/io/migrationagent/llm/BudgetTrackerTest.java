package io.migrationagent.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetTrackerTest {

    private static final BudgetLimits LIMITS = new BudgetLimits(
            /* perRunRequests */ 3, /* perRunTokens */ 1000,
            /* perDayRequests */ 5, /* perDayTokens */ 2000);

    @Test
    void allowsCallsUntilThePerRunRequestLimitIsReached(@TempDir Path tempDir) throws Exception {
        BudgetTracker tracker = new BudgetTracker(LIMITS, tempDir.resolve("budget.json"), () -> LocalDate.of(2026, 1, 1));

        assertThat(tracker.canProceed(100)).isTrue();
        tracker.record(1, 100);
        assertThat(tracker.canProceed(100)).isTrue();
        tracker.record(1, 100);
        assertThat(tracker.canProceed(100)).isTrue();
        tracker.record(1, 100);

        // Fourth request would exceed perRunRequests=3.
        assertThat(tracker.canProceed(100)).isFalse();
    }

    @Test
    void refusesACallThatWouldExceedThePerRunTokenLimitEvenWithRequestsToSpare(@TempDir Path tempDir) throws Exception {
        BudgetTracker tracker = new BudgetTracker(LIMITS, tempDir.resolve("budget.json"), () -> LocalDate.of(2026, 1, 1));

        assertThat(tracker.canProceed(1001)).isFalse();
    }

    @Test
    void persistsDailyUsageAcrossSeparateTrackerInstances(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("budget.json");
        LocalDate day = LocalDate.of(2026, 1, 1);

        BudgetTracker first = new BudgetTracker(LIMITS, stateFile, () -> day);
        first.record(2, 500);

        BudgetTracker second = new BudgetTracker(LIMITS, stateFile, () -> day);

        assertThat(second.dailyState().requestsUsedToday()).isEqualTo(2);
        assertThat(second.dailyState().tokensUsedToday()).isEqualTo(500);
        // Per-run usage is NOT persisted — a fresh tracker starts at zero
        // even though the day's cumulative usage carried over.
        assertThat(second.runRequestsUsed()).isZero();
    }

    @Test
    void resetsDailyUsageWhenTheDateRollsOver(@TempDir Path tempDir) throws Exception {
        // Generous per-run limits here on purpose — the point of this test
        // is the *daily* counter resetting, which would be masked if the
        // per-run limit (which never resets mid-run) also happened to be
        // exceeded by the same record() call.
        BudgetLimits generousRunLimits = new BudgetLimits(100, 100_000, 5, 2000);
        Path stateFile = tempDir.resolve("budget.json");
        java.util.concurrent.atomic.AtomicReference<LocalDate> day =
                new java.util.concurrent.atomic.AtomicReference<>(LocalDate.of(2026, 1, 1));

        BudgetTracker tracker = new BudgetTracker(generousRunLimits, stateFile, day::get);
        tracker.record(4, 1900);
        assertThat(tracker.canProceed(200)).isFalse();

        day.set(LocalDate.of(2026, 1, 2));

        assertThat(tracker.canProceed(200)).isTrue();
        assertThat(tracker.dailyState().requestsUsedToday()).isZero();
    }
}
