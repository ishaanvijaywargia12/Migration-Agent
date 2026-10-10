package io.migrationagent.buildparse;

/**
 * One classified build or test failure.
 *
 * @param file       source file the failure points at, or {@code null} if the
 *                   tool output didn't attribute one (e.g. a dependency conflict)
 * @param line       1-based source line, or -1 if not applicable
 * @param errorType  the exception/error class name or compiler error code, e.g.
 *                   {@code "javax.persistence.Entity"} or {@code "cannot find symbol"}
 * @param message    the human-readable failure message
 * @param rawExcerpt bounded-size excerpt of the original output this was parsed from,
 *                   kept for the trace/report — never unbounded, see {@link #MAX_EXCERPT_LENGTH}
 */
public record Failure(
        FailureCategory category,
        String file,
        int line,
        String errorType,
        String message,
        String rawExcerpt
) {
    public static final int MAX_EXCERPT_LENGTH = 2000;

    public Failure {
        if (rawExcerpt != null && rawExcerpt.length() > MAX_EXCERPT_LENGTH) {
            rawExcerpt = rawExcerpt.substring(0, MAX_EXCERPT_LENGTH) + "... [truncated]";
        }
    }
}
