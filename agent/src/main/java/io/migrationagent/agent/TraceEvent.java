package io.migrationagent.agent;

import java.time.Instant;

/**
 * One line of {@code runs/<run-id>/trace.jsonl}. {@code budget_status} per
 * provider from DESIGN.md section 11's full schema still isn't here — it
 * lives in {@code ~/.migration-agent/budget-state.json} instead
 * (DESIGN.md section 7.3), and nothing has needed a per-event snapshot of
 * it yet.
 */
public sealed interface TraceEvent {

    record ModelCall(
            String type, Instant ts, int tier, String provider, String model,
            int tokensIn, int tokensOut, long latencyMs
    ) implements TraceEvent {
        public ModelCall(Instant ts, int tier, String provider, String model, int tokensIn, int tokensOut, long latencyMs) {
            this("model_call", ts, tier, provider, model, tokensIn, tokensOut, latencyMs);
        }
    }

    /** A mechanical, harness-coded fix applied before ever calling the model — see {@link JavaxJakartaAutoFixer}. */
    record RuleFix(
            String type, Instant ts, String ruleName, String file
    ) implements TraceEvent {
        public RuleFix(Instant ts, String ruleName, String file) {
            this("rule_fix", ts, ruleName, file);
        }
    }

    record ToolCall(
            String type, Instant ts, String tool, String argsSummary, String resultSummary
    ) implements TraceEvent {
        public ToolCall(Instant ts, String tool, String argsSummary, String resultSummary) {
            this("tool_call", ts, tool, argsSummary, resultSummary);
        }
    }

    record PatchProposed(
            String type, Instant ts, String diffHash, String rationale, String verdict
    ) implements TraceEvent {
        public PatchProposed(Instant ts, String diffHash, String rationale, String verdict) {
            this("patch_proposed", ts, diffHash, rationale, verdict);
        }
    }

    record BuildResultEvent(
            String type, Instant ts, int iteration, boolean compiled,
            int testsTotal, int testsFailed, int testsErrored
    ) implements TraceEvent {
        public BuildResultEvent(Instant ts, int iteration, boolean compiled, int testsTotal, int testsFailed, int testsErrored) {
            this("build_result", ts, iteration, compiled, testsTotal, testsFailed, testsErrored);
        }
    }

    /**
     * The provider rejected or failed to answer a chat completion request
     * (e.g. a free-tier model generating a tool call that doesn't match the
     * declared JSON Schema, which some providers reject server-side with an
     * HTTP 400 rather than just returning the malformed call to us). Treated
     * as a recoverable per-iteration failure, not a fatal error — see
     * {@link AgentLoop#run}.
     */
    record ModelCallError(
            String type, Instant ts, int tier, String provider, String message
    ) implements TraceEvent {
        public ModelCallError(Instant ts, int tier, String provider, String message) {
            this("model_call_error", ts, tier, provider, message);
        }
    }

    record RunSummary(
            String type, Instant ts, String finalStatus, int iterations
    ) implements TraceEvent {
        public RunSummary(Instant ts, String finalStatus, int iterations) {
            this("run_summary", ts, finalStatus, iterations);
        }
    }
}
