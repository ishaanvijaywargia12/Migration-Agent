package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.migrationagent.buildparse.BuildOutputParser;
import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.buildparse.Failure;
import io.migrationagent.buildparse.FailureCategory;
import io.migrationagent.llm.ChatCompletionResponse;
import io.migrationagent.llm.ChatMessage;
import io.migrationagent.llm.ToolCall;
import io.migrationagent.llm.ToolDefinition;
import io.migrationagent.sandbox.SandboxResult;
import io.migrationagent.sandbox.SandboxRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hand-written agent loop (DESIGN.md section 9) — no LangChain, no
 * agent framework, just a plain while-loop calling a model with a fixed
 * tool set and re-measuring real build/test state after every accepted
 * patch. Progress is always re-derived from an actual sandbox run, never
 * from the model's own claim of success.
 *
 * <p>Each iteration: try deterministic rule-based fixes first (no model
 * call needed if they're enough), then — for whatever's left — call the
 * current {@link ModelCascade} tier, dispatch its tool calls, and rebuild.
 * If an iteration makes no net progress, the cascade escalates to the next
 * tier for the following iteration (DESIGN.md section 7.4).
 */
public final class AgentLoop {

    private static final int MAX_TOOL_ROUNDS_PER_ITERATION = 8;
    private static final List<String> STYLE_SKIP_ARGS = List.of(
            "-Dspring-javaformat.skip=true", "-Dcheckstyle.skip=true");

    private final ModelCascade cascade;
    private final List<AgentTool> tools;
    private final Map<String, AgentTool> toolsByName;
    private final SandboxRunner sandboxRunner;
    private final Path repoRoot;
    private final int jdkMajorVersion;
    private final Duration buildTimeout;
    private final TraceWriter trace;
    private final Path patchesDir;
    private final int maxIterations;
    private final boolean triageEnabled;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JavaxJakartaAutoFixer javaxJakartaAutoFixer = new JavaxJakartaAutoFixer();

    public AgentLoop(
            ModelCascade cascade, List<AgentTool> tools, SandboxRunner sandboxRunner, Path repoRoot,
            int jdkMajorVersion, Duration buildTimeout,
            TraceWriter trace, Path patchesDir, int maxIterations) {
        this(cascade, tools, sandboxRunner, repoRoot, jdkMajorVersion, buildTimeout,
                trace, patchesDir, maxIterations, true);
    }

    /**
     * @param triageEnabled false disables the deterministic rule-based fixes
     *                      (currently just {@link JavaxJakartaAutoFixer}) —
     *                      exists for the Phase 6 "hybrid without triage"
     *                      ablation (DESIGN.md's evaluation section), not a
     *                      production knob anyone would normally flip.
     */
    public AgentLoop(
            ModelCascade cascade, List<AgentTool> tools, SandboxRunner sandboxRunner, Path repoRoot,
            int jdkMajorVersion, Duration buildTimeout,
            TraceWriter trace, Path patchesDir, int maxIterations, boolean triageEnabled) {
        this.cascade = cascade;
        this.tools = tools;
        this.toolsByName = new HashMap<>();
        for (AgentTool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
        this.sandboxRunner = sandboxRunner;
        this.repoRoot = repoRoot;
        this.jdkMajorVersion = jdkMajorVersion;
        this.buildTimeout = buildTimeout;
        this.trace = trace;
        this.patchesDir = patchesDir;
        this.maxIterations = maxIterations;
        this.triageEnabled = triageEnabled;
    }

    /**
     * @return the last measured {@link BuildResult} — resolved if
     *         {@code isResolved()} is true on it, otherwise whatever state
     *         the loop stopped in (budget exhausted or iterations used up).
     */
    public BuildResult run(BuildResult initialResult) throws IOException, InterruptedException {
        BuildResult current = initialResult;
        int iteration = 0;
        String finalStatus = "unresolved";

        while (!isResolved(current) && iteration < maxIterations) {
            iteration++;
            int failuresBeforeThisIteration = current.failureCount();

            if (triageEnabled && applyTriageFixes(current)) {
                current = rebuildAndMeasure();
                trace.write(new TraceEvent.BuildResultEvent(
                        Instant.now(), iteration, current.compiled(),
                        current.testsTotal(), current.testsFailed(), current.testsErrored()));
                if (isResolved(current)) {
                    break; // fixed mechanically, no model call needed this iteration
                }
            }

            List<ChatMessage> conversation = buildInitialContext(current);
            boolean patchAppliedThisIteration = false;

            for (int round = 0; round < MAX_TOOL_ROUNDS_PER_ITERATION; round++) {
                ChatCompletionResponse response;
                try {
                    response = callModel(conversation);
                } catch (io.migrationagent.llm.BudgetExceededException budgetExceeded) {
                    finalStatus = "budget_exhausted";
                    trace.write(new TraceEvent.RunSummary(Instant.now(), finalStatus, iteration));
                    return current;
                } catch (IOException modelError) {
                    // The provider rejected the request outright (e.g. a free-tier
                    // model generated a tool call that doesn't match the declared
                    // schema, and the provider validates that server-side with an
                    // HTTP 400 instead of just handing us the malformed call). Not
                    // fatal to the whole run: abandon this iteration's tool-call
                    // round so the existing no-progress check below escalates the
                    // cascade, same as any other iteration that made no progress.
                    trace.write(new TraceEvent.ModelCallError(
                            Instant.now(), cascade.currentTierIndex(), cascade.current().providerName(),
                            modelError.getMessage()));
                    break;
                }

                ChatMessage assistantMessage = response.firstMessage();
                conversation.add(assistantMessage);

                if (assistantMessage.toolCalls() == null || assistantMessage.toolCalls().isEmpty()) {
                    break; // model is done exploring/proposing for this iteration
                }

                for (ToolCall toolCall : assistantMessage.toolCalls()) {
                    String resultText = dispatchToolCall(toolCall);
                    conversation.add(ChatMessage.toolResult(toolCall.id(), resultText));
                    if (toolCall.function().name().equals("propose_patch") && resultText.startsWith("Applied")) {
                        patchAppliedThisIteration = true;
                    }
                }
            }

            if (patchAppliedThisIteration) {
                current = rebuildAndMeasure();
                trace.write(new TraceEvent.BuildResultEvent(
                        Instant.now(), iteration, current.compiled(),
                        current.testsTotal(), current.testsFailed(), current.testsErrored()));
            }

            if (current.failureCount() >= failuresBeforeThisIteration && cascade.hasNextTier()) {
                cascade.escalate();
            }
        }

        if (isResolved(current)) {
            finalStatus = "success";
        }
        trace.write(new TraceEvent.RunSummary(Instant.now(), finalStatus, iteration));
        return current;
    }

    public static boolean isResolved(BuildResult result) {
        return result.compiled() && result.testsRan()
                && result.testsFailed() == 0 && result.testsErrored() == 0;
    }

    /**
     * Mechanical fixes attempted before any model call — see
     * {@link JavaxJakartaAutoFixer}. {@code failure.file()} is only a real
     * path for compile-error-sourced failures (surefire-sourced ones carry
     * a test classname there instead); the fixer's own file-existence check
     * naturally no-ops on the latter.
     */
    private boolean applyTriageFixes(BuildResult result) throws IOException {
        boolean fixedAny = false;
        for (Failure failure : result.failures()) {
            if (failure.category() == FailureCategory.JAVAX_JAKARTA_LEFTOVER && failure.file() != null) {
                Path file = repoRoot.resolve(failure.file());
                if (javaxJakartaAutoFixer.tryFix(file)) {
                    fixedAny = true;
                    trace.write(new TraceEvent.RuleFix(Instant.now(), "JAVAX_JAKARTA_AUTO_FIX", failure.file()));
                }
            }
        }
        return fixedAny;
    }

    private ChatCompletionResponse callModel(List<ChatMessage> conversation)
            throws IOException, InterruptedException {
        List<ToolDefinition> toolDefinitions = tools.stream().map(AgentTool::definition).toList();
        ModelCascade.Tier tier = cascade.current();

        Instant start = Instant.now();
        ChatCompletionResponse response = tier.chatClient().chat(conversation, toolDefinitions);
        long latencyMs = Duration.between(start, Instant.now()).toMillis();

        int tokensIn = response.usage() != null ? response.usage().promptTokens() : 0;
        int tokensOut = response.usage() != null ? response.usage().completionTokens() : 0;
        trace.write(new TraceEvent.ModelCall(
                Instant.now(), cascade.currentTierIndex(), tier.providerName(), tier.model(),
                tokensIn, tokensOut, latencyMs));
        return response;
    }

    private String dispatchToolCall(ToolCall toolCall) throws IOException {
        String toolName = toolCall.function().name();
        AgentTool tool = toolsByName.get(toolName);
        if (tool == null) {
            return "Unknown tool: " + toolName;
        }

        JsonNode arguments;
        try {
            arguments = mapper.readTree(toolCall.function().arguments());
        } catch (Exception malformedArguments) {
            return "Malformed tool arguments: " + malformedArguments.getMessage();
        }

        String resultText;
        try {
            resultText = tool.execute(arguments);
        } catch (Exception toolFailure) {
            resultText = "Tool execution failed: " + toolFailure.getMessage();
        }

        trace.write(new TraceEvent.ToolCall(Instant.now(), toolName, arguments.toString(), truncate(resultText, 500)));

        if (toolName.equals("propose_patch")) {
            recordPatchTrace(arguments, resultText);
        }
        return resultText;
    }

    private void recordPatchTrace(JsonNode arguments, String resultText) throws IOException {
        String diff = arguments.hasNonNull("unified_diff") ? arguments.get("unified_diff").asText() : "";
        String rationale = arguments.hasNonNull("rationale") ? arguments.get("rationale").asText() : "";
        String hash = sha256(diff);

        Files.createDirectories(patchesDir);
        Files.writeString(patchesDir.resolve(hash + ".diff"), diff);

        String verdict = resultText.startsWith("Applied") ? "accepted" : "rejected: " + truncate(resultText, 200);
        trace.write(new TraceEvent.PatchProposed(Instant.now(), hash, rationale, verdict));
    }

    private BuildResult rebuildAndMeasure() throws IOException {
        List<String> mavenArgs = new ArrayList<>(STYLE_SKIP_ARGS);
        mavenArgs.add("test");
        SandboxResult sandboxResult = sandboxRunner.run(repoRoot, mavenArgs, jdkMajorVersion, buildTimeout, false);
        return new BuildOutputParser().parse(
                sandboxResult.output(), repoRoot, sandboxResult.wallClock(), sandboxResult.exitCode());
    }

    private List<ChatMessage> buildInitialContext(BuildResult currentResult) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("""
                You are helping migrate a Spring Boot 2.7 Maven project to Spring Boot 3.x. \
                An automated OpenRewrite recipe has already applied the deterministic parts of \
                this migration; what remains are the build/test failures listed below.

                You may only act through the tools provided — there is no shell access. To fix a \
                failure, use propose_patch with a valid unified diff and a clear rationale. After \
                proposing patches, the harness will automatically rebuild and re-test to verify \
                real progress; your own claim that something is fixed is not what determines success.

                Do not delete tests, remove or weaken assertions, add @Disabled/@Ignore, or touch \
                files outside this repository — such changes will be rejected or flagged by a \
                separate verification step regardless of whether this conversation accepts them.
                """));
        messages.add(ChatMessage.user(describeFailures(currentResult)));
        return messages;
    }

    private String describeFailures(BuildResult result) {
        StringBuilder description = new StringBuilder();
        description.append("Current build state:\n");
        description.append("Compiled: ").append(result.compiled()).append('\n');
        description.append("Tests: total=").append(result.testsTotal())
                .append(" failed=").append(result.testsFailed())
                .append(" errored=").append(result.testsErrored())
                .append(" skipped=").append(result.testsSkipped())
                .append('\n');

        if (!result.failures().isEmpty()) {
            description.append("\nFailures:\n");
            int shown = 0;
            for (Failure failure : result.failures()) {
                if (shown >= 15) {
                    description.append("... and ").append(result.failures().size() - shown).append(" more\n");
                    break;
                }
                description.append("- [").append(failure.category()).append("] ")
                        .append(failure.file() != null ? failure.file() : "?")
                        .append(failure.line() >= 0 ? ":" + failure.line() : "")
                        .append(" ").append(failure.message()).append('\n');
                shown++;
            }
        }
        return description.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "... [truncated]" : text;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
