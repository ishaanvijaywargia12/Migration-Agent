package io.migrationagent.sandbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeclaredJdkDetectorTest {

    private final DeclaredJdkDetector detector = new DeclaredJdkDetector();

    @Test
    void detectsMavenCompilerRelease() {
        String pom = "<properties><maven.compiler.release>17</maven.compiler.release></properties>";

        assertThat(detector.detect(pom)).contains(17);
    }

    @Test
    void detectsSpringBootStyleJavaVersionProperty() {
        String pom = "<properties><java.version>1.8</java.version></properties>";

        assertThat(detector.detect(pom)).contains(8);
    }

    @Test
    void prefersCompilerReleaseOverJavaVersionWhenBothPresent() {
        String pom = "<properties>"
                + "<java.version>11</java.version>"
                + "<maven.compiler.release>17</maven.compiler.release>"
                + "</properties>";

        assertThat(detector.detect(pom)).contains(17);
    }

    @Test
    void fallsBackToCompilerTargetWhenReleaseIsAbsent() {
        String pom = "<properties><maven.compiler.target>11</maven.compiler.target></properties>";

        assertThat(detector.detect(pom)).contains(11);
    }

    @Test
    void returnsEmptyWhenNoRecognizedPropertyIsPresent() {
        String pom = "<properties><some.other.property>value</some.other.property></properties>";

        assertThat(detector.detect(pom)).isEmpty();
    }
}
