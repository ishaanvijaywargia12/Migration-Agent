package io.migrationagent.llm;

import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiter, refilled continuously at {@code rpm/60} tokens
 * per second (DESIGN.md section 7.3). {@link #acquire()} blocks the caller
 * until a token is available rather than rejecting — there's no cascade
 * tier to fall back to yet (that's Phase 5), so blocking is the only
 * sensible behavior for a single-provider client.
 */
public final class RateLimiter {

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final double maxTokens;
    private final double tokensPerMillisecond;
    private final LongSupplier clockMillis;
    private final Sleeper sleeper;

    private double availableTokens;
    private long lastRefillTimeMillis;

    public RateLimiter(int requestsPerMinute) {
        this(requestsPerMinute, System::currentTimeMillis, Thread::sleep);
    }

    RateLimiter(int requestsPerMinute, LongSupplier clockMillis, Sleeper sleeper) {
        this.maxTokens = requestsPerMinute;
        this.tokensPerMillisecond = requestsPerMinute / 60_000.0;
        this.clockMillis = clockMillis;
        this.sleeper = sleeper;
        this.availableTokens = requestsPerMinute;
        this.lastRefillTimeMillis = clockMillis.getAsLong();
    }

    public synchronized void acquire() throws InterruptedException {
        while (true) {
            refill();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return;
            }
            double tokensNeeded = 1.0 - availableTokens;
            long waitMillis = (long) Math.ceil(tokensNeeded / tokensPerMillisecond);
            sleeper.sleep(Math.max(waitMillis, 1));
        }
    }

    private void refill() {
        long now = clockMillis.getAsLong();
        long elapsed = now - lastRefillTimeMillis;
        if (elapsed > 0) {
            availableTokens = Math.min(maxTokens, availableTokens + elapsed * tokensPerMillisecond);
            lastRefillTimeMillis = now;
        }
    }
}
