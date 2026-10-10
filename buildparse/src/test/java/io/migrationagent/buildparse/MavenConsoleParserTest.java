package io.migrationagent.buildparse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenConsoleParserTest {

    @Test
    void findsNoCompileErrorsInASuccessfulBuild() {
        List<Failure> failures = MavenConsoleParser.parseCompileErrors(readFixture("success-console.txt"));

        assertThat(failures).isEmpty();
    }

    @Test
    void extractsOneFailurePerCompileErrorLine() {
        List<Failure> failures = MavenConsoleParser.parseCompileErrors(readFixture("compile-error-console.txt"));

        assertThat(failures).hasSize(2);
    }

    @Test
    void capturesFileAndLineFromTheErrorLocation() {
        List<Failure> failures = MavenConsoleParser.parseCompileErrors(readFixture("compile-error-console.txt"));

        assertThat(failures.get(0).file()).endsWith("PetController.java");
        assertThat(failures.get(0).line()).isEqualTo(15);
    }

    @Test
    void classifiesTheJavaxImportErrorViaContentRules() {
        List<Failure> failures = MavenConsoleParser.parseCompileErrors(readFixture("compile-error-console.txt"));

        assertThat(failures.get(0).category()).isEqualTo(FailureCategory.JAVAX_JAKARTA_LEFTOVER);
    }

    @Test
    void foldsFollowUpErrorDetailLinesIntoTheSameFailuresExcerptInsteadOfCreatingNewFailures() {
        List<Failure> failures = MavenConsoleParser.parseCompileErrors(readFixture("compile-error-console.txt"));

        Failure cannotFindSymbol = failures.get(1);
        assertThat(cannotFindSymbol.rawExcerpt()).contains("symbol:   class Entity");
        assertThat(cannotFindSymbol.rawExcerpt()).contains("location: class com.example.PetController");
    }

    @Test
    void defaultsToUnknownCategoryWhenNoContentRuleMatches() {
        List<Failure> failures = MavenConsoleParser.parseCompileErrors(
                "[ERROR] /repo/src/main/java/com/example/Foo.java:[1,1] some new kind of error\n");

        assertThat(failures.get(0).category()).isEqualTo(FailureCategory.UNKNOWN);
    }

    private String readFixture(String name) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("console/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
