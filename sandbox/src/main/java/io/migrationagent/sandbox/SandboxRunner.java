package io.migrationagent.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Runs a Maven command against a checked-out repo inside a disposable,
 * resource-limited container. This is the one interface the rest of the
 * codebase depends on — {@link TestcontainersSandboxRunner} is the only
 * implementation for now, but keeping this as an interface means the agent
 * loop (Phase 3+) never gets a direct reference to Docker/Testcontainers
 * types, which is part of how "the agent has no shell access" is enforced
 * structurally rather than by convention (see DESIGN.md section 3 and 12).
 */
public interface SandboxRunner {

    /**
     * Network enabled — for harness-controlled dependency resolution
     * (baseline builds, the OpenRewrite step, any first build of a freshly
     * cloned or rewritten repo). See the 5-arg overload for agent-triggered
     * rebuilds, which should run offline (DESIGN.md section 5.2).
     *
     * @param repoDir         host path to the already-checked-out repo (see {@link RepoCheckout})
     * @param mavenArgs       arguments after {@code mvn}, e.g. {@code List.of("test")}
     * @param jdkMajorVersion selects the container image, see {@link SandboxImages}
     * @param timeout         wall-clock budget; the container is killed if exceeded
     */
    default SandboxResult run(Path repoDir, List<String> mavenArgs, int jdkMajorVersion, Duration timeout) {
        return run(repoDir, mavenArgs, jdkMajorVersion, timeout, true);
    }

    /**
     * @param networkEnabled false disables the container's network entirely
     *                       (not just Maven's {@code -o} flag) — used for
     *                       agent-triggered rebuilds once the {@code .m2}
     *                       cache is already warm from an earlier
     *                       network-enabled build, both for speed and so
     *                       agent-authored code can't reach the network
     *                       even if it tried (DESIGN.md section 5.2, 12).
     */
    SandboxResult run(Path repoDir, List<String> mavenArgs, int jdkMajorVersion, Duration timeout, boolean networkEnabled);
}
