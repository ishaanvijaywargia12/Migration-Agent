package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.migrationagent.llm.FunctionDefinition;
import io.migrationagent.llm.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ReadFileTool implements AgentTool {

    private static final int MAX_LINES_WITHOUT_RANGE = 300;

    private final Path repoRoot;

    public ReadFileTool(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(new FunctionDefinition(
                name(),
                "Read lines from a file in the target repo.",
                JsonSchemas.parse("""
                        {
                          "type": "object",
                          "properties": {
                            "path": {"type": "string"},
                            "start_line": {"type": "integer"},
                            "end_line": {"type": "integer"}
                          },
                          "required": ["path"]
                        }
                        """)));
    }

    @Override
    public String execute(JsonNode arguments) throws IOException {
        String relativePath = arguments.get("path").asText();
        Path file = PathContainment.resolveWithinRepo(repoRoot, relativePath);
        if (!Files.isRegularFile(file)) {
            return "No such file: " + relativePath;
        }

        List<String> lines = Files.readAllLines(file);
        int startLine = arguments.hasNonNull("start_line") ? arguments.get("start_line").asInt() : 1;
        int endLine = arguments.hasNonNull("end_line")
                ? arguments.get("end_line").asInt()
                : Math.min(lines.size(), startLine + MAX_LINES_WITHOUT_RANGE - 1);

        int fromIndex = Math.max(0, startLine - 1);
        int toIndex = Math.min(lines.size(), endLine);
        if (fromIndex >= toIndex) {
            return "Requested line range is empty or out of bounds for " + relativePath
                    + " (file has " + lines.size() + " lines).";
        }

        StringBuilder result = new StringBuilder();
        for (int i = fromIndex; i < toIndex; i++) {
            result.append(i + 1).append(": ").append(lines.get(i)).append('\n');
        }
        return result.toString();
    }
}
