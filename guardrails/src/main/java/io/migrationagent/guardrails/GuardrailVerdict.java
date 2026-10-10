package io.migrationagent.guardrails;

/**
 * The outcome of one {@link GuardrailRule} check. {@code ruleName} is what
 * makes "log which guardrail rejected it" concrete (DESIGN.md section 10) —
 * every rejection is attributable to a specific, named rule, not a generic
 * "patch rejected."
 */
public record GuardrailVerdict(boolean accepted, String ruleName, String reason) {

    public static GuardrailVerdict ok() {
        return new GuardrailVerdict(true, null, null);
    }

    public static GuardrailVerdict rejected(String ruleName, String reason) {
        return new GuardrailVerdict(false, ruleName, reason);
    }
}
