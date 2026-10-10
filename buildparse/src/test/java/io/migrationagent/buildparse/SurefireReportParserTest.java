package io.migrationagent.buildparse;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class SurefireReportParserTest {

    private final SurefireReportParser parser = new SurefireReportParser();

    @Test
    void aggregatesCountsAcrossAllReportFiles() throws Exception {
        SurefireReportParser.Result result = parser.parse(fixtureDir());

        // FooTest: 3 passing. BarTest: 4 total (1 pass, 1 failure, 1 error, 1 skipped).
        assertThat(result.filesParsed()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(7);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errored()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void extractsOneFailureRecordPerFailingOrErroredTestcase() throws Exception {
        SurefireReportParser.Result result = parser.parse(fixtureDir());

        assertThat(result.failures()).hasSize(2);
    }

    @Test
    void classifiesAJavaxRelatedRuntimeErrorEvenThoughItCameFromATest() throws Exception {
        SurefireReportParser.Result result = parser.parse(fixtureDir());

        assertThat(result.failures())
                .anySatisfy(f -> assertThat(f.category()).isEqualTo(FailureCategory.JAVAX_JAKARTA_LEFTOVER));
    }

    @Test
    void defaultsToTestFailureCategoryWhenNoContentRuleMatches() throws Exception {
        SurefireReportParser.Result result = parser.parse(fixtureDir());

        assertThat(result.failures())
                .anySatisfy(f -> assertThat(f.category()).isEqualTo(FailureCategory.TEST_FAILURE));
    }

    @Test
    void skippedTestsDoNotProduceFailureRecords() throws Exception {
        SurefireReportParser.Result result = parser.parse(fixtureDir());

        assertThat(result.failures())
                .noneMatch(f -> f.message().contains("itIsSkippedForNow"));
    }

    private Path fixtureDir() throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource("surefire-reports").toURI()).getParent();
    }
}
