package io.migrationagent.eval;

/**
 * The five ablation configurations the evaluation harness compares, per
 * the project's evaluation requirements: how much does each piece of the
 * pipeline actually contribute?
 */
public enum AblationMode {
    /** Baseline, then the OpenRewrite recipe only — no agent loop at all. */
    OPENREWRITE_ONLY,
    /** Baseline, then straight to the agent loop — the recipe is never applied. */
    LLM_ONLY,
    /** The full pipeline: recipe, then (if needed) the agent loop with triage and the full cascade. */
    HYBRID,
    /** Hybrid, but {@link io.migrationagent.agent.JavaxJakartaAutoFixer}-style rule fixes are disabled. */
    HYBRID_NO_TRIAGE,
    /** Hybrid, but the agent loop only ever uses the first cascade tier — no escalation. */
    HYBRID_NO_CASCADE
}
