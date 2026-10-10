package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.migrationagent.llm.FunctionDefinition;
import io.migrationagent.llm.ToolDefinition;
import io.migrationagent.sandbox.SandboxResult;
import io.migrationagent.sandbox.SandboxRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class GetDependencyTreeTool implements AgentTool {

    private final SandboxRunner runner;
    private final Path repoRoot;
    private final int jdkMajorVersion;
    private final Duration timeout;

    public GetDependencyTreeTool(SandboxRunner runner, Path repoRoot, int jdkMajorVersion, Duration timeout) {
        this.runner = runner;
        this.repoRoot = repoRoot;
        this.jdkMajorVersion = jdkMajorVersion;
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "get_dependency_tree";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(new FunctionDefinition(
                name(),
                "Return `mvn dependency:tree` output for the target repo.",
                JsonSchemas.parse("""
                        {"type": "object", "properties": {}}
                        """)));
    }

    @Override
    public String execute(JsonNode arguments) {
        // Agent-triggered — offline, reusing the .m2 cache a harness-controlled
        // build already populated (DESIGN.md section 5.2).
        SandboxResult result = runner.run(
                repoRoot, List.of("dependency:tree"), jdkMajorVersion, timeout, false);
        return result.output();
    }
}
