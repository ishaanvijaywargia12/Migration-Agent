package io.migrationagent.llm;

import java.io.IOException;

/**
 * Thrown instead of sending a model call that would exceed the configured
 * per-run or per-day budget. Extends {@link IOException} so callers that
 * already handle the chat client's checked exceptions don't need a separate
 * catch clause — the distinction that matters (budget vs. transport
 * failure) is in the exception type, not the catch signature.
 */
public final class BudgetExceededException extends IOException {

    public BudgetExceededException(String message) {
        super(message);
    }
}
