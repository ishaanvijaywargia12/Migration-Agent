package io.migrationagent.guardrails;

import java.nio.file.Path;

/**
 * One anti-cheating check, operating purely on the diff text (and, where
 * needed, the pre-patch file content already on disk at {@code repoRoot}).
 * This is deliberately diff/text-pattern based rather than full Java AST
 * analysis — DESIGN.md section 10's documented MVP scope trade-off, backed
 * by {@link PostSuccessVerifier} as the real safety net for whatever a
 * pattern-based check misses.
 *
 * <p>Every implementation here is pure harness code with zero dependency on
 * model-generated natural language (no reading of the model's own
 * "rationale" text, no trusting anything the model claims) — this is what
 * makes guardrails structurally immune to prompt injection via repo
 * content, not just "hopefully" immune (DESIGN.md section 12).
 */
public interface GuardrailRule {

    GuardrailVerdict check(String unifiedDiff, Path repoRoot);

    String name();
}
