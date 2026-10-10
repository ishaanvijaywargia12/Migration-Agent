package io.migrationagent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathContainmentTest {

    @Test
    void resolvesAPathInsideTheRepo(@TempDir Path repoRoot) throws Exception {
        Path resolved = PathContainment.resolveWithinRepo(repoRoot, "src/main/Foo.java");

        assertThat(resolved).isEqualTo(repoRoot.toAbsolutePath().normalize().resolve("src/main/Foo.java"));
    }

    @Test
    void rejectsADotDotTraversalEscapingTheRepo(@TempDir Path repoRoot) {
        assertThatThrownBy(() -> PathContainment.resolveWithinRepo(repoRoot, "../../etc/passwd"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("escapes the repository");
    }

    @Test
    void rejectsAnAbsolutePathOutsideTheRepo(@TempDir Path repoRoot) {
        assertThatThrownBy(() -> PathContainment.resolveWithinRepo(repoRoot, "/etc/passwd"))
                .isInstanceOf(IOException.class);
    }
}
