package io.migrationagent.rewrite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootVersionDetectorTest {

    private final SpringBootVersionDetector detector = new SpringBootVersionDetector();

    @Test
    void detectsBoot3Parent() {
        String pom = """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.5.0</version>
                  </parent>
                </project>
                """;
        assertThat(detector.isBoot3OrHigher(pom)).isTrue();
    }

    @Test
    void rejectsUnmigratedBoot2Parent() {
        String pom = """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.7.3</version>
                  </parent>
                </project>
                """;
        assertThat(detector.isBoot3OrHigher(pom)).isFalse();
    }

    @Test
    void returnsFalseWhenNoSpringBootParentPresent() {
        String pom = """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>some-other-parent</artifactId>
                    <version>3.0.0</version>
                  </parent>
                </project>
                """;
        assertThat(detector.isBoot3OrHigher(pom)).isFalse();
    }
}
