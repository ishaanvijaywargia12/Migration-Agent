package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.migrationagent.llm.FunctionDefinition;
import io.migrationagent.llm.ToolDefinition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * A pure-Java, ripgrep-<i>style</i> search (not a wrapper around the real
 * {@code rg} binary) — this way the tool works the same regardless of
 * what's installed on the host, at the cost of being slower on very large
 * repos. For the benchmark repos this tool targets, that trade-off is fine.
 */
public final class SearchCodeTool implements AgentTool {

    private static final int MAX_RESULTS = 100;

    private final Path repoRoot;

    public SearchCodeTool(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    @Override
    public String name() {
        return "search_code";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(new FunctionDefinition(
                name(),
                "ripgrep-style search over the target repo's tracked files.",
                JsonSchemas.parse("""
                        {
                          "type": "object",
                          "properties": {
                            "pattern": {"type": "string"},
                            "glob": {"type": "string"}
                          },
                          "required": ["pattern"]
                        }
                        """)));
    }

    @Override
    public String execute(JsonNode arguments) throws IOException {
        String patternText = arguments.get("pattern").asText();
        Pattern pattern;
        try {
            pattern = Pattern.compile(patternText);
        } catch (PatternSyntaxException invalidRegex) {
            return "Invalid regex pattern: " + invalidRegex.getMessage();
        }

        PathMatcher globMatcher = arguments.hasNonNull("glob")
                ? FileSystems.getDefault().getPathMatcher("glob:" + arguments.get("glob").asText())
                : path -> true;

        List<String> matches;
        try (Stream<Path> walk = Files.walk(repoRoot)) {
            matches = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isNotInIgnoredDirectory)
                    .filter(file -> globMatcher.matches(file.getFileName()))
                    .flatMap(file -> grepFile(file, pattern).stream())
                    .limit(MAX_RESULTS)
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        if (matches.isEmpty()) {
            return "No matches for pattern: " + patternText;
        }
        return String.join("\n", matches);
    }

    private boolean isNotInIgnoredDirectory(Path file) {
        Path relative = repoRoot.relativize(file);
        for (Path segment : relative) {
            String name = segment.toString();
            if (name.equals(".git") || name.equals("target") || name.equals("node_modules")) {
                return false;
            }
        }
        return true;
    }

    private List<String> grepFile(Path file, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(file);
            String relativePath = repoRoot.relativize(file).toString();
            return lines.stream()
                    .filter(pattern.asPredicate())
                    .map(line -> relativePath + ": " + line.strip())
                    .toList();
        } catch (IOException | UncheckedIOException unreadable) {
            // Binary files or encoding issues — skip rather than fail the
            // whole search over one unreadable file.
            return List.of();
        }
    }
}
