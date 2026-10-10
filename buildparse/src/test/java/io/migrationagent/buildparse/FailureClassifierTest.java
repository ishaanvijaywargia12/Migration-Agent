package io.migrationagent.buildparse;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {

    @Test
    void classifiesJavaxImportLeftover() {
        Optional<FailureCategory> category = FailureClassifier.classify(
                "compile error", "package javax.persistence does not exist");

        assertThat(category).contains(FailureCategory.JAVAX_JAKARTA_LEFTOVER);
    }

    @Test
    void classifiesSpringSecurityConfigChange() {
        Optional<FailureCategory> category = FailureClassifier.classify(
                "compile error", "cannot find symbol: class WebSecurityConfigurerAdapter");

        assertThat(category).contains(FailureCategory.SPRING_SECURITY_CONFIG);
    }

    @Test
    void classifiesRemovedApi() {
        Optional<FailureCategory> category = FailureClassifier.classify(
                "compile error", "class WebMvcConfigurerAdapter does not exist");

        assertThat(category).contains(FailureCategory.REMOVED_RENAMED_API);
    }

    @Test
    void classifiesHibernate6Change() {
        Optional<FailureCategory> category = FailureClassifier.classify(
                "java.lang.NoSuchMethodError", "org.hibernate.cfg.Configuration.buildSessionFactory()");

        assertThat(category).contains(FailureCategory.HIBERNATE_6_CHANGE);
    }

    @Test
    void classifiesDependencyConflict() {
        Optional<FailureCategory> category = FailureClassifier.classify(
                null, "Could not resolve dependencies for project com.example:app:jar:1.0");

        assertThat(category).contains(FailureCategory.DEPENDENCY_CONFLICT);
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        Optional<FailureCategory> category = FailureClassifier.classify(
                "org.opentest4j.AssertionFailedError", "expected:<true> but was:<false>");

        assertThat(category).isEmpty();
    }
}
