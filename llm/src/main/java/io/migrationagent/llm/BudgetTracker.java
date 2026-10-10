package io.migrationagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.function.Supplier;

/**
 * Tracks both per-run usage (in-memory, resets every CLI invocation) and
 * per-day usage (persisted to disk, keyed by UTC date so it survives across
 * invocations — DESIGN.md section 7.3). {@link #canProceed(int)} is checked
 * <i>before</i> every model call, not after, so a call that would exceed
 * the budget is never sent.
 */
public final class BudgetTracker {

    private final BudgetLimits limits;
    private final Path stateFilePath;
    private final Supplier<LocalDate> today;
    private final ObjectMapper mapper;

    private int runRequestsUsed = 0;
    private int runTokensUsed = 0;
    private BudgetState dailyState;

    public BudgetTracker(BudgetLimits limits, Path stateFilePath) throws IOException {
        this(limits, stateFilePath, () -> LocalDate.now(ZoneOffset.UTC));
    }

    BudgetTracker(BudgetLimits limits, Path stateFilePath, Supplier<LocalDate> today) throws IOException {
        this.limits = limits;
        this.stateFilePath = stateFilePath;
        this.today = today;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.dailyState = loadState();
    }

    public synchronized boolean canProceed(int estimatedTokens) {
        rolloverIfNewDay();
        return runRequestsUsed + 1 <= limits.perRunRequests()
                && runTokensUsed + estimatedTokens <= limits.perRunTokens()
                && dailyState.requestsUsedToday() + 1 <= limits.perDayRequests()
                && dailyState.tokensUsedToday() + estimatedTokens <= limits.perDayTokens();
    }

    public synchronized void record(int requests, int tokens) throws IOException {
        rolloverIfNewDay();
        runRequestsUsed += requests;
        runTokensUsed += tokens;
        dailyState = new BudgetState(
                dailyState.date(),
                dailyState.requestsUsedToday() + requests,
                dailyState.tokensUsedToday() + tokens);
        persist();
    }

    public synchronized int runRequestsUsed() {
        return runRequestsUsed;
    }

    public synchronized int runTokensUsed() {
        return runTokensUsed;
    }

    public synchronized BudgetState dailyState() {
        return dailyState;
    }

    private BudgetState loadState() throws IOException {
        LocalDate now = today.get();
        if (!Files.exists(stateFilePath)) {
            return BudgetState.freshFor(now);
        }
        BudgetState loaded = mapper.readValue(stateFilePath.toFile(), BudgetState.class);
        return loaded.date().equals(now) ? loaded : BudgetState.freshFor(now);
    }

    private void rolloverIfNewDay() {
        LocalDate now = today.get();
        if (!dailyState.date().equals(now)) {
            dailyState = BudgetState.freshFor(now);
        }
    }

    private void persist() throws IOException {
        Path parent = stateFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(stateFilePath.toFile(), dailyState);
    }
}
