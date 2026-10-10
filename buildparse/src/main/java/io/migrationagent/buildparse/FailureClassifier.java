package io.migrationagent.buildparse;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic, regex-based failure classification. This runs before any
 * model call — the agent only ever sees a failure after this has had a
 * chance to assign a known category, per the triage design in DESIGN.md
 * section 6.
 *
 * <p>Rules are checked in order and the first match wins, because a single
 * error can mention symbols from more than one category (e.g. a Hibernate
 * stack trace that also references a {@code javax.persistence} import) —
 * the ordering below puts the more specific/actionable category first.
 */
final class FailureClassifier {

    private static final Pattern JAVAX_JAKARTA = Pattern.compile(
            "javax\\.(persistence|servlet|validation|annotation|transaction|xml\\.bind|ws\\.rs|mail|inject)\\b");

    private static final Pattern SPRING_SECURITY = Pattern.compile(
            "WebSecurityConfigurerAdapter|GlobalMethodSecurityConfiguration"
            + "|\\.antMatchers\\(|\\.authorizeRequests\\(|EnableGlobalMethodSecurity");

    private static final Pattern REMOVED_RENAMED_API = Pattern.compile(
            "WebMvcConfigurerAdapter|EnableCircuitBreaker"
            + "|org\\.springframework\\.boot\\.context\\.embedded"
            + "|RepositoryRestConfigurerAdapter");

    private static final Pattern HIBERNATE_6 = Pattern.compile("org\\.hibernate\\.");

    private static final Pattern DEPENDENCY_CONFLICT = Pattern.compile(
            "DependencyResolutionException|Non-resolvable parent POM"
            + "|Could not resolve dependencies|Could not find artifact");

    private FailureClassifier() {
    }

    /**
     * @return the category this failure's content matches, or empty if none of the
     *         known rules apply — callers assign the structural default
     *         ({@code TEST_FAILURE} or {@code UNKNOWN}) themselves in that case.
     */
    static Optional<FailureCategory> classify(String errorType, String message) {
        String haystack = (nullToEmpty(errorType) + " " + nullToEmpty(message));

        if (JAVAX_JAKARTA.matcher(haystack).find()) {
            return Optional.of(FailureCategory.JAVAX_JAKARTA_LEFTOVER);
        }
        if (SPRING_SECURITY.matcher(haystack).find()) {
            return Optional.of(FailureCategory.SPRING_SECURITY_CONFIG);
        }
        if (REMOVED_RENAMED_API.matcher(haystack).find()) {
            return Optional.of(FailureCategory.REMOVED_RENAMED_API);
        }
        if (HIBERNATE_6.matcher(haystack).find()) {
            return Optional.of(FailureCategory.HIBERNATE_6_CHANGE);
        }
        if (DEPENDENCY_CONFLICT.matcher(haystack).find()) {
            return Optional.of(FailureCategory.DEPENDENCY_CONFLICT);
        }
        return Optional.empty();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
