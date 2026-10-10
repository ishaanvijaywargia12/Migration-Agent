package io.migrationagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Appends one JSON line per {@link TraceEvent} to {@code trace.jsonl}.
 * Nothing in {@link TraceEvent} ever carries a credential value (DESIGN.md
 * section 11) — token *counts* pass through {@code model_call}, not token
 * *content*, so there is nothing here that needs redaction.
 */
public final class TraceWriter implements Closeable {

    private final BufferedWriter writer;
    private final ObjectMapper mapper;

    public TraceWriter(Path traceFilePath) throws IOException {
        Path parent = traceFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(
                traceFilePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public synchronized void write(TraceEvent event) throws IOException {
        writer.write(mapper.writeValueAsString(event));
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
