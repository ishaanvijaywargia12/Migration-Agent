package io.migrationagent.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real network integration test — clones a small, stable public repo.
 * Named so it matches neither of Surefire's default inclusion patterns,
 * same reasoning as {@link SandboxRunnerDockerIT}. Run explicitly with
 * {@code mvn test -Dtest=RepoCheckoutIT -pl sandbox}.
 */
class RepoCheckoutIT {

    @Test
    void clonesAPublicRepoAndChecksOutTheRequestedCommit(@TempDir Path destination) throws Exception {
        new RepoCheckout().checkout(
                "https://github.com/octocat/Hello-World.git",
                "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d",
                destination);

        assertThat(destination.resolve("README")).exists();
        assertThat(Files.readString(destination.resolve("README"))).contains("Hello World");
    }
}
