package io.migrationagent.llm;

import java.time.LocalDate;

/**
 * Persisted daily usage, keyed by UTC date so it survives across separate
 * CLI invocations (DESIGN.md section 7.3). Per-run usage is intentionally
 * not persisted here — it's tracked in memory only and resets every
 * invocation.
 */
public record BudgetState(LocalDate date, int requestsUsedToday, int tokensUsedToday) {

    static BudgetState freshFor(LocalDate date) {
        return new BudgetState(date, 0, 0);
    }
}
