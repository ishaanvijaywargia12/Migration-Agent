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
import java.util.List;

public final class RunBuildTool implements AgentTool {

    // Same style/format plugin skips used everywhere else a build runs
    // (BaselineCommand, RewriteCommand) — an agent-triggered compile
    // shouldn't hit the same spring-javaformat-style false failures the
    // harness's own builds are already immune to.
    private static final List<String> STYLE_SKIP_ARGS = List.of(
            "-Dspring-javaformat.skip=true", "-Dcheckstyle.skip=true");

    private final SandboxRunner runner;
    private final Path repoRoot;
    private final int jdkMajorVersion;
    private final Duration timeout;

    public RunBuildTool(SandboxRunner runner, Path repoRoot, int jdkMajorVersion, Duration timeout) {
        this.runner = runner;
        this.repoRoot = repoRoot;
        this.jdkMajorVersion = jdkMajorVersion;
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "run_build";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(new FunctionDefinition(
                name(),
                "Compile the target repo in the sandbox. No arguments.",
                JsonSchemas.parse("""
                        {"type": "object", "properties": {}}
                        """)));
    }

    @Override
    public String execute(JsonNode arguments) throws IOException {
        List<String> mavenArgs = new java.util.ArrayList<>(STYLE_SKIP_ARGS);
        mavenArgs.add("compile");

        SandboxResult sandboxResult = runner.run(repoRoot, mavenArgs, jdkMajorVersion, timeout, false);
        BuildResult buildResult = new BuildOutputParser().parse(
                sandboxResult.output(), repoRoot, sandboxResult.wallClock(), sandboxResult.exitCode());
        return BuildResultFormatter.summarize(buildResult);
    }
}
