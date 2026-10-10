package io.migrationagent.llm;

public record BudgetLimits(
        int perRunRequests,
        int perRunTokens,
        int perDayRequests,
        int perDayTokens
) {
}
