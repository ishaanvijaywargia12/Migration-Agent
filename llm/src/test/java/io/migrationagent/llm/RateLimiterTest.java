package io.migrationagent.llm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses an injected fake clock/sleeper so this test is fully deterministic
 * and instant — no real {@code Thread.sleep} anywhere. The fake sleeper
 * advances the fake clock by exactly the requested amount, simulating real
 * time passing without actually waiting.
 */
class RateLimiterTest {

    @Test
    void allowsBurstUpToBucketCapacityWithoutWaiting() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0);
        AtomicInteger sleepCalls = new AtomicInteger(0);
        RateLimiter limiter = new RateLimiter(60, clock::get, millis -> sleepCalls.incrementAndGet());

        for (int i = 0; i < 60; i++) {
            limiter.acquire();
        }

        assertThat(sleepCalls.get()).isZero();
    }

    @Test
    void blocksOnceTheBucketIsExhaustedAndResumesAfterSimulatedRefill() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0);
        RateLimiter.Sleeper advancingSleeper = millis -> clock.addAndGet(millis);
        RateLimiter limiter = new RateLimiter(60, clock::get, advancingSleeper);

        for (int i = 0; i < 60; i++) {
            limiter.acquire();
        }

        // Bucket is empty; 60 rpm = 1 token/second, so the 61st acquire()
        // must wait ~1000ms (simulated) before succeeding.
        long clockBefore = clock.get();
        limiter.acquire();

        assertThat(clock.get() - clockBefore).isGreaterThanOrEqualTo(1000);
    }
}
