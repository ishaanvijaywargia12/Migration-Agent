package io.migrationagent.sandbox;

import java.util.Map;

/**
 * Maps a JDK major version to the exact Docker image used to build/test in
 * that JDK. Images are the official {@code maven} images (Maven bundled
 * with Eclipse Temurin), pinned to the 3.9.x Maven line rather than a
 * floating {@code latest}/{@code 3} tag, for the same reproducibility
 * reason the OpenRewrite plugin versions are pinned (DESIGN.md open
 * question 5): a run today and a run next year should use the same
 * toolchain unless someone deliberately bumps this map.
 */
final class SandboxImages {

    private static final Map<Integer, String> IMAGE_BY_JDK_MAJOR = Map.of(
            8, "maven:3.9-eclipse-temurin-8",
            11, "maven:3.9-eclipse-temurin-11",
            17, "maven:3.9-eclipse-temurin-17"
    );

    private SandboxImages() {
    }

    static String imageFor(int jdkMajorVersion) {
        String image = IMAGE_BY_JDK_MAJOR.get(jdkMajorVersion);
        if (image == null) {
            throw new IllegalArgumentException(
                    "No sandbox image configured for JDK " + jdkMajorVersion
                    + " — supported versions are " + IMAGE_BY_JDK_MAJOR.keySet());
        }
        return image;
    }
}
