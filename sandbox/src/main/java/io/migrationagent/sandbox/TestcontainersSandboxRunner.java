package io.migrationagent.sandbox;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Docker-backed {@link SandboxRunner}. One fresh container per invocation
 * (not a long-lived one reused across builds), so a leaked process or file
 * descriptor from one build can never bleed into the next — see DESIGN.md
 * section 5.2.
 *
 * <p>The target repo is bind-mounted read-write (Maven needs to write to
 * {@code target/}), and a single host-wide {@code .m2} cache directory is
 * bind-mounted in so repeated runs don't re-download the same dependencies
 * from Maven Central every time.
 */
public final class TestcontainersSandboxRunner implements SandboxRunner {

    private static final long MEMORY_LIMIT_BYTES = 4L * 1024 * 1024 * 1024;
    private static final long CPU_LIMIT_NANOS = 2_000_000_000L;

    static {
        // Testcontainers 1.21.3 vendors its own shaded, frozen copy of an old
        // docker-java-core, whose default Docker API version negotiation
        // falls back to a baseline old enough that recent Docker Engine
        // releases (MinAPIVersion 1.40+, e.g. Docker Desktop 4.79 / Engine
        // 29.x) reject it outright with HTTP 400 on every connection
        // strategy. Setting this system property (read by
        // org.testcontainers.shaded.com.github.dockerjava.core.DefaultDockerClientConfig)
        // pins a modern-but-conservative version instead of relying on that
        // stale default. Confirmed via decompiling the shaded class and
        // reproducing the exact same 400 with a raw curl to an old versioned
        // endpoint against a real Docker Desktop daemon.
        if (System.getProperty("api.version") == null) {
            System.setProperty("api.version", "1.41");
        }
    }

    private final Path m2CacheDir;

    public TestcontainersSandboxRunner() {
        this(Path.of(System.getProperty("user.home"), ".migration-agent", "m2-cache"));
    }

    TestcontainersSandboxRunner(Path m2CacheDir) {
        this.m2CacheDir = m2CacheDir;
    }

    private static final String LIVE_LOG_FILE_NAME = ".sandbox-live-output.log";

    @Override
    public SandboxResult run(
            Path repoDir, List<String> mavenArgs, int jdkMajorVersion, Duration timeout, boolean networkEnabled) {
        try {
            Files.createDirectories(m2CacheDir);
            Files.deleteIfExists(repoDir.resolve(LIVE_LOG_FILE_NAME));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare sandbox run directories", e);
        }

        String image = SandboxImages.imageFor(jdkMajorVersion);
        List<String> effectiveArgs = networkEnabled ? mavenArgs : withOfflineFlag(mavenArgs);
        // Redirected into a file inside the bind-mounted workspace (not just
        // captured via execInContainer's return value) so that if the
        // process has to be killed on timeout, whatever it printed before
        // the kill survives on the host — execInContainer only returns
        // output for a process that finishes, so without this a timed-out
        // run gives zero insight into whether it was genuinely stuck or
        // just slow (a real gap this hit in practice: a multi-module
        // reactor's rewrite step timed out with nothing to show for it
        // until this was added).
        //
        // Deliberately NOT `... | tee file` here: piping through tee would
        // make the shell's exit status reflect tee's exit code, not mvn's,
        // silently breaking every exit-code-based success/failure check
        // downstream. Redirecting to the file, capturing mvn's real exit
        // code into $CODE, then `cat`-ing the file back out keeps
        // execInContainer's returned stdout and exit code both correct.
        String logFile = "/workspace/" + LIVE_LOG_FILE_NAME;
        String mavenCommand = "cd /workspace && mvn -B " + String.join(" ", effectiveArgs)
                + " > " + logFile + " 2>&1; CODE=$?; cat " + logFile + "; exit $CODE";

        try (GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image))
                .withFileSystemBind(repoDir.toAbsolutePath().toString(), "/workspace", BindMode.READ_WRITE)
                .withFileSystemBind(m2CacheDir.toAbsolutePath().toString(), "/root/.m2", BindMode.READ_WRITE)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withMemory(MEMORY_LIMIT_BYTES)
                        .withNanoCPUs(CPU_LIMIT_NANOS))
                .withNetworkMode(networkEnabled ? null : "none")
                .withCommand("tail", "-f", "/dev/null")) {

            container.start();
            return execWithTimeout(container, mavenCommand, timeout, repoDir);
        } finally {
            // Whatever was needed from this file is already captured in the
            // returned SandboxResult (via the normal stdout path, or via
            // readPartialOutput() on timeout) — left behind, it would show
            // up as a spurious "changed file" to anything diffing the repo
            // afterward (caught in practice: it inflated an OpenRewrite
            // diff's file-changed count by one).
            try {
                Files.deleteIfExists(repoDir.resolve(LIVE_LOG_FILE_NAME));
            } catch (IOException ignored) {
                // Best-effort cleanup; leaving a stray log file is harmless
                // compared to failing an otherwise-successful build.
            }
        }
    }

    private List<String> withOfflineFlag(List<String> mavenArgs) {
        List<String> withFlag = new java.util.ArrayList<>();
        withFlag.add("-o");
        withFlag.addAll(mavenArgs);
        return withFlag;
    }

    private SandboxResult execWithTimeout(GenericContainer<?> container, String command, Duration timeout, Path repoDir) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Instant start = Instant.now();
        try {
            Future<Container.ExecResult> future = executor.submit(
                    () -> container.execInContainer("sh", "-c", command));
            Container.ExecResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return new SandboxResult(
                    result.getExitCode(),
                    result.getStdout() + result.getStderr(),
                    Duration.between(start, Instant.now()));
        } catch (TimeoutException timedOut) {
            // The enclosing try-with-resources in run() stops (and removes) the
            // container as soon as this method returns, which kills the
            // in-progress Maven process along with it. Whatever it printed
            // before the kill is still readable from the host via the bind
            // mount, since writes to the log file land on the host
            // filesystem directly, independent of the container's lifecycle.
            String partialOutput = readPartialOutput(repoDir);
            String message = "Build timed out after " + timeout
                    + (partialOutput.isEmpty() ? "" : "\n\n--- output before timeout ---\n" + partialOutput);
            return new SandboxResult(
                    SandboxResult.TIMED_OUT,
                    message,
                    Duration.between(start, Instant.now()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for sandbox build", interrupted);
        } catch (ExecutionException e) {
            throw new RuntimeException("Sandbox build failed to execute", e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static final int MAX_PARTIAL_OUTPUT_CHARS = 5000;

    private String readPartialOutput(Path repoDir) {
        try {
            Path logFile = repoDir.resolve(LIVE_LOG_FILE_NAME);
            if (!Files.exists(logFile)) {
                return "";
            }
            String content = Files.readString(logFile);
            return content.length() > MAX_PARTIAL_OUTPUT_CHARS
                    ? "... [truncated] ...\n" + content.substring(content.length() - MAX_PARTIAL_OUTPUT_CHARS)
                    : content;
        } catch (IOException unreadable) {
            return "";
        }
    }
}
