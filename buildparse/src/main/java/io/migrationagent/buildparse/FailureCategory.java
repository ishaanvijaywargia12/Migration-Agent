package io.migrationagent.buildparse;

/**
 * Deterministic classification of a build/test failure, assigned by
 * {@link FailureClassifier} before any model ever sees the failure.
 * UNKNOWN is a valid, expected outcome for a failure that doesn't match a
 * known rule — it is always escalated to the model cascade, never dropped.
 */
public enum FailureCategory {
    JAVAX_JAKARTA_LEFTOVER,
    REMOVED_RENAMED_API,
    SPRING_SECURITY_CONFIG,
    HIBERNATE_6_CHANGE,
    DEPENDENCY_CONFLICT,
    TEST_FAILURE,
    UNKNOWN
}
