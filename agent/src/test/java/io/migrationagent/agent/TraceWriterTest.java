package io.migrationagent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceWriterTest {

    @Test
    void appendsOneJsonLinePerEvent(@TempDir Path tempDir) throws Exception {
        Path traceFile = tempDir.resolve("trace.jsonl");

        try (TraceWriter writer = new TraceWriter(traceFile)) {
            writer.write(new TraceEvent.ModelCall(Instant.now(), 0, "groq", "openai/gpt-oss-20b", 100, 50, 640));
            writer.write(new TraceEvent.ToolCall(Instant.now(), "read_file", "{\"path\":\"Foo.java\"}", "line1\n"));
            writer.write(new TraceEvent.RunSummary(Instant.now(), "success", 3));
        }

        List<String> lines = Files.readAllLines(traceFile);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("\"type\":\"model_call\"").contains("\"provider\":\"groq\"");
        assertThat(lines.get(1)).contains("\"type\":\"tool_call\"").contains("read_file");
        assertThat(lines.get(2)).contains("\"type\":\"run_summary\"").contains("\"finalStatus\":\"success\"");
    }

    @Test
    void appendsToAnExistingFileAcrossSeparateWriterInstances(@TempDir Path tempDir) throws Exception {
        Path traceFile = tempDir.resolve("trace.jsonl");

        try (TraceWriter first = new TraceWriter(traceFile)) {
            first.write(new TraceEvent.RunSummary(Instant.now(), "unresolved", 1));
        }
        try (TraceWriter second = new TraceWriter(traceFile)) {
            second.write(new TraceEvent.RunSummary(Instant.now(), "success", 2));
        }

        assertThat(Files.readAllLines(traceFile)).hasSize(2);
    }
}
