package io.migrationagent.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceAnalyzerTest {

    private final TraceAnalyzer analyzer = new TraceAnalyzer();

    @Test
    void countsRuleFixesAndModelCallsByTier(@TempDir Path tempDir) throws Exception {
        Path trace = tempDir.resolve("trace.jsonl");
        Files.writeString(trace, String.join("\n", List.of(
                """
                {"type":"rule_fix","ts":1,"ruleName":"JAVAX_JAKARTA_AUTO_FIX","file":"Foo.java"}""",
                """
                {"type":"model_call","ts":2,"tier":0,"provider":"groq","model":"m","tokensIn":100,"tokensOut":20,"latencyMs":500}""",
                """
                {"type":"model_call","ts":3,"tier":0,"provider":"groq","model":"m","tokensIn":150,"tokensOut":30,"latencyMs":500}""",
                """
                {"type":"model_call","ts":4,"tier":1,"provider":"openrouter","model":"m2","tokensIn":200,"tokensOut":40,"latencyMs":500}""",
                """
                {"type":"run_summary","ts":5,"finalStatus":"success","iterations":2}"""
        )) + "\n");

        TraceAnalyzer.TraceSummary summary = analyzer.analyze(trace);

        assertThat(summary.ruleFixCount()).isEqualTo(1);
        assertThat(summary.totalModelCalls()).isEqualTo(3);
        assertThat(summary.modelCallsByTier()).containsEntry(0, 2).containsEntry(1, 1);
        assertThat(summary.totalTokensIn()).isEqualTo(450);
        assertThat(summary.totalTokensOut()).isEqualTo(90);
        assertThat(summary.finalStatus()).isEqualTo("success");
        assertThat(summary.iterationsCompleted()).isEqualTo(2);
    }

    @Test
    void attributesAnImprovedBuildResultToTheMostRecentlyActiveTier(@TempDir Path tempDir) throws Exception {
        Path trace = tempDir.resolve("trace.jsonl");
        Files.writeString(trace, String.join("\n", List.of(
                """
                {"type":"model_call","ts":1,"tier":0,"provider":"groq","model":"m","tokensIn":10,"tokensOut":5,"latencyMs":100}""",
                // First build_result: nothing to compare against yet, so not "improved".
                """
                {"type":"build_result","ts":2,"iteration":1,"compiled":true,"testsTotal":5,"testsFailed":3,"testsErrored":0}""",
                """
                {"type":"model_call","ts":3,"tier":1,"provider":"openrouter","model":"m2","tokensIn":10,"tokensOut":5,"latencyMs":100}""",
                // Second build_result: failed+errored dropped from 3 to 1 — improvement, attributed to tier 1.
                """
                {"type":"build_result","ts":4,"iteration":2,"compiled":true,"testsTotal":5,"testsFailed":1,"testsErrored":0}"""
        )) + "\n");

        TraceAnalyzer.TraceSummary summary = analyzer.analyze(trace);

        assertThat(summary.iterationsImprovedByTier()).containsEntry(1, 1);
        assertThat(summary.iterationsImprovedByTier()).doesNotContainKey(0);
    }

    @Test
    void returnsAnEmptySummaryForAMissingTraceFile(@TempDir Path tempDir) throws Exception {
        TraceAnalyzer.TraceSummary summary = analyzer.analyze(tempDir.resolve("does-not-exist.jsonl"));

        assertThat(summary.totalModelCalls()).isZero();
        assertThat(summary.ruleFixCount()).isZero();
        assertThat(summary.finalStatus()).isEqualTo("unknown");
    }
}
