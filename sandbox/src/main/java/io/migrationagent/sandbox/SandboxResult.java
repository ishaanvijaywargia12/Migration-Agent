package io.migrationagent.sandbox;

import java.time.Duration;

/**
 * Raw result of one sandbox build/test invocation, before
 * {@code buildparse} turns it into a structured {@code BuildResult}.
 *
 * @param exitCode  the Maven process's exit code, or {@link #TIMED_OUT} if
 *                  the wall-clock budget was exceeded and the container was
 *                  killed
 * @param output    combined stdout+stderr from the Maven invocation
 * @param wallClock actual elapsed time, capped at the configured timeout
 */
public record SandboxResult(int exitCode, String output, Duration wallClock) {

    public static final int TIMED_OUT = -1;

    public boolean timedOut() {
        return exitCode == TIMED_OUT;
    }
}
