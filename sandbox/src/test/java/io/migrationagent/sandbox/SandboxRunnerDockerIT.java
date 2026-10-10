package io.migrationagent.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Docker integration test. Named so it matches neither of Surefire's
 * default inclusion patterns ({@code Test*}, {@code *Test(s)},
 * {@code *TestCase}) — {@code mvn test} skips it automatically. Run
 * explicitly with {@code mvn test -Dtest=SandboxRunnerDockerIT -pl sandbox}.
 * Requires a running Docker daemon.
 */
class SandboxRunnerDockerIT {

    @Test
    void runsMavenTestInsideTheContainerAndReportsSuccess(@TempDir Path workspace) throws Exception {
        copyFixtureProject(workspace);

        SandboxRunner runner = new TestcontainersSandboxRunner();
        SandboxResult result = runner.run(workspace, List.of("test"), 17, Duration.ofMinutes(5));

        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("BUILD SUCCESS");
        assertThat(workspace.resolve("target/surefire-reports/TEST-com.example.CalculatorTest.xml")).exists();
    }

    private void copyFixtureProject(Path destination) throws IOException, URISyntaxException {
        Path fixtureRoot = Paths.get(
                getClass().getClassLoader().getResource("minimal-maven-project").toURI());

        try (Stream<Path> walk = Files.walk(fixtureRoot)) {
            for (Path source : walk.toList()) {
                Path target = destination.resolve(fixtureRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
