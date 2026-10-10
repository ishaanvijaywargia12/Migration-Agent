package io.migrationagent.agent;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Every tool that touches the filesystem resolves the model-supplied path
 * through this first. This is a basic tool-safety property (a tool must
 * only ever act within the repo it was scoped to), not one of the
 * anti-cheating guardrails that validate patch *content* — those are
 * Phase 4, harness-enforced separately from anything a tool call itself
 * does (DESIGN.md section 10).
 */
final class PathContainment {

    private PathContainment() {
    }

    /**
     * @return the resolved, normalized absolute path
     * @throws IOException if {@code relativePath} would resolve outside {@code repoRoot}
     */
    static Path resolveWithinRepo(Path repoRoot, String relativePath) throws IOException {
        Path root = repoRoot.toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Path escapes the repository: " + relativePath);
        }
        return resolved;
    }
}
