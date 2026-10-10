package io.migrationagent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavaxJakartaAutoFixerTest {

    private final JavaxJakartaAutoFixer fixer = new JavaxJakartaAutoFixer();

    @Test
    void rewritesAKnownSafeJavaxImportToJakarta(@TempDir Path repoRoot) throws Exception {
        Path file = repoRoot.resolve("Owner.java");
        Files.writeString(file, "import javax.persistence.Entity;\n\n@Entity\nclass Owner {}\n");

        boolean fixed = fixer.tryFix(file);

        assertThat(fixed).isTrue();
        assertThat(Files.readString(file)).contains("import jakarta.persistence.Entity;");
        assertThat(Files.readString(file)).doesNotContain("javax.persistence");
    }

    @Test
    void rewritesMultipleKnownPackagesInTheSameFile(@TempDir Path repoRoot) throws Exception {
        Path file = repoRoot.resolve("Controller.java");
        Files.writeString(file, """
                import javax.servlet.http.HttpServletRequest;
                import javax.validation.Valid;
                class Controller {}
                """);

        fixer.tryFix(file);

        String content = Files.readString(file);
        assertThat(content).contains("import jakarta.servlet.http.HttpServletRequest;");
        assertThat(content).contains("import jakarta.validation.Valid;");
    }

    @Test
    void leavesJavaxAnnotationAloneDeliberately(@TempDir Path repoRoot) throws Exception {
        Path file = repoRoot.resolve("Processor.java");
        Files.writeString(file, "import javax.annotation.processing.Processor;\nclass Foo {}\n");

        boolean fixed = fixer.tryFix(file);

        assertThat(fixed).isFalse();
        assertThat(Files.readString(file)).contains("javax.annotation.processing.Processor");
    }

    @Test
    void returnsFalseWhenNothingToFix(@TempDir Path repoRoot) throws Exception {
        Path file = repoRoot.resolve("Clean.java");
        Files.writeString(file, "class Clean {}\n");

        assertThat(fixer.tryFix(file)).isFalse();
    }

    @Test
    void returnsFalseForAPathThatIsNotARegularFile(@TempDir Path repoRoot) throws Exception {
        Path missing = repoRoot.resolve("com.example.SomeTestClassname");

        assertThat(fixer.tryFix(missing)).isFalse();
    }
}
