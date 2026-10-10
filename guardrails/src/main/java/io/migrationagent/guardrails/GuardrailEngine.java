package io.migrationagent.guardrails;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs every {@link GuardrailRule} against a proposed patch and returns the
 * first rejection, or acceptance if all pass. This is the one thing
 * {@code agent}'s {@code ProposePatchTool} calls before ever touching the
 * working tree — see DESIGN.md section 10 for the full rule list and the
 * documented scope trade-off (diff/text-pattern based, not full AST).
 */
public final class GuardrailEngine {

    private final List<GuardrailRule> rules;

    public GuardrailEngine() {
        this(List.of(
                new PathContainmentGuardrail(),
                new NoTestDeletionGuardrail(),
                new NoTestWeakeningGuardrail(),
                new NoExceptionSwallowingGuardrail(),
                new NoVersionDowngradeGuardrail()));
    }

    GuardrailEngine(List<GuardrailRule> rules) {
        this.rules = rules;
    }

    public GuardrailVerdict validate(String unifiedDiff, Path repoRoot) {
        for (GuardrailRule rule : rules) {
            GuardrailVerdict verdict = rule.check(unifiedDiff, repoRoot);
            if (!verdict.accepted()) {
                return verdict;
            }
        }
        return GuardrailVerdict.ok();
    }
}
