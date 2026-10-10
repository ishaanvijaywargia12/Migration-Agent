package io.migrationagent.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkManifestTest {

    @Test
    void loadsRepoUrlAndCommitIgnoringTheHumanReadableVerificationSections() throws Exception {
        BenchmarkManifest manifest = BenchmarkManifest.load(fixturePath());

        BenchmarkRepo repo = manifest.get("spring-petclinic");
        assertThat(repo.repoUrl()).isEqualTo("https://github.com/spring-projects/spring-petclinic.git");
        assertThat(repo.commitSha()).isEqualTo("276880edef4c3d1029865d19d6d28e982b9d4d01");
    }

    @Test
    void throwsAClearErrorForAnUnknownRepoName() throws Exception {
        BenchmarkManifest manifest = BenchmarkManifest.load(fixturePath());

        assertThatThrownBy(() -> manifest.get("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Path fixturePath() throws Exception {
        return Paths.get(getClass().getClassLoader().getResource("manifest-fixture.yaml").toURI());
    }
}
