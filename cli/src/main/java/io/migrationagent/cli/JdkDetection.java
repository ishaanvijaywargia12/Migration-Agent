package io.migrationagent.cli;

import io.migrationagent.sandbox.DeclaredJdkDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared JDK-resolution policy: detect from the repo's own pom.xml, falling
 * back to Java 8 — Spring Boot 2.7's own minimum — when nothing is declared
 * (DESIGN.md section 5.1).
 */
final class JdkDetection {

    static final int DEFAULT_BASELINE_JDK = 8;

    private JdkDetection() {
    }

    static int detectOrDefault(Path workspace) throws IOException {
        Path pomXml = workspace.resolve("pom.xml");
        if (!Files.exists(pomXml)) {
            return DEFAULT_BASELINE_JDK;
        }
        String pomContent = Files.readString(pomXml);
        return new DeclaredJdkDetector().detect(pomContent).orElse(DEFAULT_BASELINE_JDK);
    }
}
