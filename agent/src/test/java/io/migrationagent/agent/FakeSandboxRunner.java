package io.migrationagent.agent;

import io.migrationagent.sandbox.SandboxResult;
import io.migrationagent.sandbox.SandboxRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Returns a fixed result without touching Docker. {@link io.migrationagent.buildparse.BuildOutputParser}
 * reads surefire reports from the real filesystem regardless of what this
 * returns, so tests arrange the "after" state by writing fixture surefire
 * XML into the repo directory directly, not through this fake.
 */
final class FakeSandboxRunner implements SandboxRunner {

    private final SandboxResult scriptedResult;

    FakeSandboxRunner(SandboxResult scriptedResult) {
        this.scriptedResult = scriptedResult;
    }

    @Override
    public SandboxResult run(Path repoDir, List<String> mavenArgs, int jdkMajorVersion, Duration timeout, boolean networkEnabled) {
        return scriptedResult;
    }
}
