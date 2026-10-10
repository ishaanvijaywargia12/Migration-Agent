package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.migrationagent.buildparse.BuildOutputParser;
import io.migrationagent.buildparse.BuildResult;
import io.migrationagent.llm.FunctionDefinition;
import io.migrationagent.llm.ToolDefinition;
import io.migrationagent.sandbox.SandboxResult;
import io.migrationagent.sandbox.SandboxRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class RunTestsTool implements AgentTool {

    private static final List<String> STYLE_SKIP_ARGS = List.of(
            "-Dspring-javaformat.skip=true", "-Dcheckstyle.skip=true");

    private final SandboxRunner runner;
    private final Path repoRoot;
    private final int jdkMajorVersion;
    private final Duration timeout;

    public RunTestsTool(SandboxRunner runner, Path repoRoot, int jdkMajorVersion, Duration timeout) {
        this.runner = runner;
        this.repoRoot = repoRoot;
        this.jdkMajorVersion = jdkMajorVersion;
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "run_tests";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(new FunctionDefinition(
                name(),
                "Run the target repo's tests in the sandbox.",
                JsonSchemas.parse("""
                        {
                          "type": "object",
                          "properties": {
                            "test_filter": {"type": "string"}
                          }
                        }
                        """)));
    }

    @Override
    public String execute(JsonNode arguments) throws IOException {
        List<String> mavenArgs = new ArrayList<>(STYLE_SKIP_ARGS);
        if (arguments.hasNonNull("test_filter")) {
            mavenArgs.add("-Dtest=" + arguments.get("test_filter").asText());
        }
        mavenArgs.add("test");

        SandboxResult sandboxResult = runner.run(repoRoot, mavenArgs, jdkMajorVersion, timeout, false);
        BuildResult buildResult = new BuildOutputParser().parse(
                sandboxResult.output(), repoRoot, sandboxResult.wallClock(), sandboxResult.exitCode());
        return BuildResultFormatter.summarize(buildResult);
    }
}
