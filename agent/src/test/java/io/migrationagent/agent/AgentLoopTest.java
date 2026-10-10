package io.migrationagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.buildparse.Failure;
import io.migrationagent.buildparse.FailureCategory;
import io.migrationagent.llm.BudgetExceededException;
import io.migrationagent.llm.ChatClient;
import io.migrationagent.llm.ChatCompletionResponse;
import io.migrationagent.llm.ChatMessage;
import io.migrationagent.llm.Choice;
import io.migrationagent.llm.FunctionCall;
import io.migrationagent.llm.ToolCall;
import io.migrationagent.llm.Usage;
import io.migrationagent.sandbox.SandboxResult;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real loop logic end to end — real JGit repo, real
 * {@link ProposePatchTool} applying a real diff, real
 * {@link io.migrationagent.buildparse.BuildOutputParser} reading real
 * surefire XML off disk — with only the model and the Docker sandbox faked
 * out, since those are the two things that genuinely can't run in a unit
 * test.
 */
class AgentLoopTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void appliesAProposedPatchAndResolvesOnceTheRebuildShowsPassingTests(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            Files.writeString(repoRoot.resolve("Foo.java"), "line1\nline2\nline3\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setSign(false).call();
        }

        BuildResult unresolvedState = new BuildResult(
                true, true, 1, 1, 0, 0,
                List.of(new Failure(FailureCategory.TEST_FAILURE, "Foo.java", 2, "AssertionError", "expected line2-changed", "")),
                Duration.ZERO, 1);

        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,3 @@
                 line1
                -line2
                +line2-changed
                 line3
                """;
        String toolArgs = mapper.writeValueAsString(Map.of("unified_diff", diff, "rationale", "fix the assertion"));

        ChatCompletionResponse proposePatchResponse = new ChatCompletionResponse(
                List.of(new Choice(
                        ChatMessage.assistant(null, List.of(
                                new ToolCall("call_1", "function", new FunctionCall("propose_patch", toolArgs)))),
                        "tool_calls")),
                new Usage(50, 20, 70));
        ChatCompletionResponse doneResponse = new ChatCompletionResponse(
                List.of(new Choice(ChatMessage.assistant("That should fix it.", null), "stop")),
                new Usage(10, 5, 15));

        FakeChatClient chatClient = new FakeChatClient(List.of(proposePatchResponse, doneResponse));
        FakeSandboxRunner sandboxRunner = new FakeSandboxRunner(new SandboxResult(0, "mvn test output", Duration.ofSeconds(5)));

        // Simulates what a real `mvn test` run would leave behind after the
        // fix — BuildOutputParser reads this off the real filesystem when
        // the loop rebuilds, independent of the fake sandbox's own output.
        Path surefireDir = repoRoot.resolve("target/surefire-reports");
        Files.createDirectories(surefireDir);
        Files.writeString(surefireDir.resolve("TEST-Foo.xml"), """
                <testsuite name="Foo" tests="1" failures="0" errors="0" skipped="0">
                  <testcase name="itWorks" classname="Foo"/>
                </testsuite>
                """);

        List<AgentTool> tools = List.of(
                new ReadFileTool(repoRoot),
                new ProposePatchTool(repoRoot));

        try (TraceWriter trace = new TraceWriter(repoRoot.resolve("trace.jsonl"))) {
            ModelCascade cascade = ModelCascade.singleTier(chatClient, "fake-provider", "fake-model");
            AgentLoop loop = new AgentLoop(
                    cascade, tools, sandboxRunner, repoRoot,
                    17, Duration.ofSeconds(60), trace, repoRoot.resolve("patches"), 5);

            BuildResult result = loop.run(unresolvedState);

            assertThat(AgentLoop.isResolved(result)).isTrue();
            assertThat(result.testsTotal()).isEqualTo(1);
            assertThat(result.testsFailed()).isZero();
        }

        assertThat(chatClient.callCount()).isEqualTo(2);
        assertThat(Files.readString(repoRoot.resolve("Foo.java"))).contains("line2-changed");

        List<String> traceLines = Files.readAllLines(repoRoot.resolve("trace.jsonl"));
        assertThat(traceLines).anySatisfy(line -> assertThat(line).contains("\"type\":\"patch_proposed\""));
        assertThat(traceLines).anySatisfy(line -> assertThat(line).contains("\"type\":\"run_summary\"").contains("\"finalStatus\":\"success\""));
    }

    @Test
    void stopsAfterMaxIterationsWithoutResolvingIfTheModelNeverFixesIt(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            git.commit().setMessage("empty").setAllowEmpty(true).setSign(false).call();
        }

        BuildResult unresolvedState = new BuildResult(
                true, true, 1, 1, 0, 0,
                List.of(new Failure(FailureCategory.UNKNOWN, "Foo.java", 1, "Error", "still broken", "")),
                Duration.ZERO, 1);

        // Model gives up immediately every time it's asked — no tool calls.
        ChatCompletionResponse givesUp = new ChatCompletionResponse(
                List.of(new Choice(ChatMessage.assistant("I don't know how to fix this.", null), "stop")),
                new Usage(10, 5, 15));

        FakeChatClient chatClient = new FakeChatClient(List.of(givesUp, givesUp));
        FakeSandboxRunner sandboxRunner = new FakeSandboxRunner(new SandboxResult(1, "still failing", Duration.ofSeconds(1)));

        try (TraceWriter trace = new TraceWriter(repoRoot.resolve("trace.jsonl"))) {
            ModelCascade cascade = ModelCascade.singleTier(chatClient, "fake-provider", "fake-model");
            AgentLoop loop = new AgentLoop(
                    cascade, List.of(new ReadFileTool(repoRoot)),
                    sandboxRunner, repoRoot, 17, Duration.ofSeconds(60), trace, repoRoot.resolve("patches"), 2);

            BuildResult result = loop.run(unresolvedState);

            assertThat(AgentLoop.isResolved(result)).isFalse();
        }

        assertThat(chatClient.callCount()).isEqualTo(2);
        List<String> traceLines = Files.readAllLines(repoRoot.resolve("trace.jsonl"));
        assertThat(traceLines).anySatisfy(line ->
                assertThat(line).contains("\"type\":\"run_summary\"").contains("\"finalStatus\":\"unresolved\""));
    }

    @Test
    void stopsCleanlyWhenTheBudgetIsExhausted(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            git.commit().setMessage("empty").setAllowEmpty(true).setSign(false).call();
        }

        BuildResult unresolvedState = new BuildResult(
                true, true, 1, 1, 0, 0, List.of(), Duration.ZERO, 1);

        ChatClient budgetExceededClient = (messages, tools) -> {
            throw new BudgetExceededException("out of budget");
        };
        FakeSandboxRunner sandboxRunner = new FakeSandboxRunner(new SandboxResult(1, "n/a", Duration.ZERO));

        try (TraceWriter trace = new TraceWriter(repoRoot.resolve("trace.jsonl"))) {
            ModelCascade cascade = ModelCascade.singleTier(budgetExceededClient, "fake-provider", "fake-model");
            AgentLoop loop = new AgentLoop(
                    cascade, List.of(),
                    sandboxRunner, repoRoot, 17, Duration.ofSeconds(60), trace, repoRoot.resolve("patches"), 5);

            BuildResult result = loop.run(unresolvedState);

            assertThat(result).isEqualTo(unresolvedState);
        }

        List<String> traceLines = Files.readAllLines(repoRoot.resolve("trace.jsonl"));
        assertThat(traceLines).anySatisfy(line ->
                assertThat(line).contains("\"finalStatus\":\"budget_exhausted\""));
    }

    @Test
    void resolvesViaTriageAloneWithoutEverCallingTheModel(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            Files.writeString(repoRoot.resolve("Owner.java"), "import javax.persistence.Entity;\n\n@Entity\nclass Owner {}\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setSign(false).call();
        }

        BuildResult unresolvedState = new BuildResult(
                false, false, 0, 0, 0, 0,
                List.of(new Failure(FailureCategory.JAVAX_JAKARTA_LEFTOVER, "Owner.java", 1,
                        "compile error", "package javax.persistence does not exist", "")),
                Duration.ZERO, 1);

        // What the rebuild after the triage fix will find on disk.
        Path surefireDir = repoRoot.resolve("target/surefire-reports");
        Files.createDirectories(surefireDir);
        Files.writeString(surefireDir.resolve("TEST-Owner.xml"), """
                <testsuite name="Owner" tests="1" failures="0" errors="0" skipped="0">
                  <testcase name="itWorks" classname="Owner"/>
                </testsuite>
                """);

        // Empty script: any call at all throws, proving the model is never reached.
        FakeChatClient chatClient = new FakeChatClient(List.of());
        FakeSandboxRunner sandboxRunner = new FakeSandboxRunner(new SandboxResult(0, "mvn test output", Duration.ofSeconds(1)));

        try (TraceWriter trace = new TraceWriter(repoRoot.resolve("trace.jsonl"))) {
            ModelCascade cascade = ModelCascade.singleTier(chatClient, "fake-provider", "fake-model");
            AgentLoop loop = new AgentLoop(
                    cascade, List.of(), sandboxRunner, repoRoot,
                    17, Duration.ofSeconds(60), trace, repoRoot.resolve("patches"), 5);

            BuildResult result = loop.run(unresolvedState);

            assertThat(AgentLoop.isResolved(result)).isTrue();
        }

        assertThat(chatClient.callCount()).isZero();
        assertThat(Files.readString(repoRoot.resolve("Owner.java"))).contains("import jakarta.persistence.Entity;");

        List<String> traceLines = Files.readAllLines(repoRoot.resolve("trace.jsonl"));
        assertThat(traceLines).anySatisfy(line -> assertThat(line).contains("\"type\":\"rule_fix\""));
    }

    @Test
    void skipsTriageEntirelyWhenDisabledForTheAblationStudy(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            Files.writeString(repoRoot.resolve("Owner.java"), "import javax.persistence.Entity;\n\n@Entity\nclass Owner {}\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setSign(false).call();
        }

        BuildResult unresolvedState = new BuildResult(
                false, false, 0, 0, 0, 0,
                List.of(new Failure(FailureCategory.JAVAX_JAKARTA_LEFTOVER, "Owner.java", 1,
                        "compile error", "package javax.persistence does not exist", "")),
                Duration.ZERO, 1);

        // With triage disabled, the model has to be asked instead — give it
        // one scripted "I give up" response so the loop can terminate.
        ChatCompletionResponse givesUp = new ChatCompletionResponse(
                List.of(new Choice(ChatMessage.assistant("no idea", null), "stop")),
                new Usage(10, 5, 15));
        FakeChatClient chatClient = new FakeChatClient(List.of(givesUp));
        FakeSandboxRunner sandboxRunner = new FakeSandboxRunner(new SandboxResult(1, "still broken", Duration.ofSeconds(1)));

        try (TraceWriter trace = new TraceWriter(repoRoot.resolve("trace.jsonl"))) {
            ModelCascade cascade = ModelCascade.singleTier(chatClient, "fake-provider", "fake-model");
            AgentLoop loop = new AgentLoop(
                    cascade, List.of(), sandboxRunner, repoRoot,
                    17, Duration.ofSeconds(60), trace, repoRoot.resolve("patches"), 1, false);

            loop.run(unresolvedState);
        }

        // The file is untouched — triage never ran — and the model was
        // actually asked instead of the loop resolving silently via triage.
        assertThat(Files.readString(repoRoot.resolve("Owner.java"))).contains("import javax.persistence.Entity;");
        assertThat(chatClient.callCount()).isEqualTo(1);
    }

    @Test
    void escalatesToTheNextCascadeTierAfterAnIterationMakesNoProgress(@TempDir Path repoRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            Files.writeString(repoRoot.resolve("Foo.java"), "line1\nline2\nline3\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setSign(false).call();
        }

        BuildResult unresolvedState = new BuildResult(
                true, true, 1, 1, 0, 0,
                List.of(new Failure(FailureCategory.UNKNOWN, "Foo.java", 2, "AssertionError", "still broken", "")),
                Duration.ZERO, 1);

        // Tier 1 gives up without even trying a patch — no progress.
        ChatCompletionResponse tier1GivesUp = new ChatCompletionResponse(
                List.of(new Choice(ChatMessage.assistant("I can't figure this out.", null), "stop")),
                new Usage(10, 5, 15));
        FakeChatClient tier1Client = new FakeChatClient(List.of(tier1GivesUp));

        // Tier 2 actually fixes it.
        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,3 @@
                 line1
                -line2
                +line2-changed
                 line3
                """;
        String toolArgs = mapper.writeValueAsString(Map.of("unified_diff", diff, "rationale", "fix it"));
        ChatCompletionResponse tier2ProposesPatch = new ChatCompletionResponse(
                List.of(new Choice(
                        ChatMessage.assistant(null, List.of(
                                new ToolCall("call_1", "function", new FunctionCall("propose_patch", toolArgs)))),
                        "tool_calls")),
                new Usage(50, 20, 70));
        ChatCompletionResponse tier2Done = new ChatCompletionResponse(
                List.of(new Choice(ChatMessage.assistant("Fixed.", null), "stop")),
                new Usage(10, 5, 15));
        FakeChatClient tier2Client = new FakeChatClient(List.of(tier2ProposesPatch, tier2Done));

        Path surefireDir = repoRoot.resolve("target/surefire-reports");
        Files.createDirectories(surefireDir);
        Files.writeString(surefireDir.resolve("TEST-Foo.xml"), """
                <testsuite name="Foo" tests="1" failures="0" errors="0" skipped="0">
                  <testcase name="itWorks" classname="Foo"/>
                </testsuite>
                """);

        FakeSandboxRunner sandboxRunner = new FakeSandboxRunner(new SandboxResult(0, "mvn test output", Duration.ofSeconds(1)));
        List<AgentTool> tools = List.of(new ProposePatchTool(repoRoot));

        try (TraceWriter trace = new TraceWriter(repoRoot.resolve("trace.jsonl"))) {
            ModelCascade cascade = new ModelCascade(List.of(
                    new ModelCascade.Tier(tier1Client, "tier1-provider", "tier1-model"),
                    new ModelCascade.Tier(tier2Client, "tier2-provider", "tier2-model")));
            AgentLoop loop = new AgentLoop(
                    cascade, tools, sandboxRunner, repoRoot,
                    17, Duration.ofSeconds(60), trace, repoRoot.resolve("patches"), 5);

            BuildResult result = loop.run(unresolvedState);

            assertThat(AgentLoop.isResolved(result)).isTrue();
            assertThat(cascade.currentTierIndex()).isEqualTo(1);
        }

        assertThat(tier1Client.callCount()).isEqualTo(1);
        assertThat(tier2Client.callCount()).isEqualTo(2);

        List<String> traceLines = Files.readAllLines(repoRoot.resolve("trace.jsonl"));
        assertThat(traceLines).anySatisfy(line -> assertThat(line).contains("\"tier\":0").contains("tier1-provider"));
        assertThat(traceLines).anySatisfy(line -> assertThat(line).contains("\"tier\":1").contains("tier2-provider"));
    }
}
