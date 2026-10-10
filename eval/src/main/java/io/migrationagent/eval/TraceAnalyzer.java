package io.migrationagent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summarizes a run's {@code trace.jsonl} for the results table — parsed as
 * plain JSON (not the {@code agent} module's {@code TraceEvent} types),
 * deliberately, so {@code eval} doesn't need a dependency on {@code agent}
 * just to read back what it already wrote in a known, stable line format.
 *
 * <p>{@code modelCallsByTier} is an exact count (every {@code model_call}
 * line records its tier). Attributing a specific *fix* to a specific tier
 * is only approximate: a {@code build_result} line is matched to whichever
 * tier's {@code model_call} most recently preceded it by timestamp, and
 * "improved" means total failed+errored tests went down from the previous
 * build_result (or from zero, for the first one). This is a reasonable
 * approximation given DESIGN.md's per-iteration (not per-failure-group)
 * cascade tracking — it is not a claim that the matched tier's specific
 * tool call caused the improvement, only that it was the active tier when
 * the improvement was observed.
 */
public final class TraceAnalyzer {

    public record TraceSummary(
            int ruleFixCount,
            int totalModelCalls,
            Map<Integer, Integer> modelCallsByTier,
            int totalTokensIn,
            int totalTokensOut,
            Map<Integer, Integer> iterationsImprovedByTier,
            String finalStatus,
            int iterationsCompleted
    ) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public TraceSummary analyze(Path traceFile) throws IOException {
        int ruleFixCount = 0;
        int totalTokensIn = 0;
        int totalTokensOut = 0;
        String finalStatus = "unknown";
        int iterationsCompleted = 0;
        Map<Integer, Integer> modelCallsByTier = new LinkedHashMap<>();
        Map<Integer, Integer> iterationsImprovedByTier = new LinkedHashMap<>();

        Integer lastSeenTier = null;
        int previousFailedPlusErrored = 0;
        boolean sawFirstBuildResult = false;

        List<String> lines = Files.exists(traceFile) ? Files.readAllLines(traceFile) : List.of();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode event = MAPPER.readTree(line);
            String type = event.path("type").asText();

            switch (type) {
                case "rule_fix" -> ruleFixCount++;
                case "model_call" -> {
                    int tier = event.path("tier").asInt();
                    lastSeenTier = tier;
                    modelCallsByTier.merge(tier, 1, Integer::sum);
                    totalTokensIn += event.path("tokensIn").asInt();
                    totalTokensOut += event.path("tokensOut").asInt();
                }
                case "build_result" -> {
                    int failedPlusErrored = event.path("testsFailed").asInt() + event.path("testsErrored").asInt();
                    boolean improved = sawFirstBuildResult && failedPlusErrored < previousFailedPlusErrored;
                    if (improved && lastSeenTier != null) {
                        iterationsImprovedByTier.merge(lastSeenTier, 1, Integer::sum);
                    }
                    previousFailedPlusErrored = failedPlusErrored;
                    sawFirstBuildResult = true;
                }
                case "run_summary" -> {
                    finalStatus = event.path("finalStatus").asText();
                    iterationsCompleted = event.path("iterations").asInt();
                }
                default -> {
                    // tool_call, patch_proposed — not needed for the summary table.
                }
            }
        }

        int totalModelCalls = modelCallsByTier.values().stream().mapToInt(Integer::intValue).sum();
        return new TraceSummary(
                ruleFixCount, totalModelCalls, modelCallsByTier,
                totalTokensIn, totalTokensOut, iterationsImprovedByTier,
                finalStatus, iterationsCompleted);
    }
}
