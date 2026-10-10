package io.migrationagent.rewrite;

import java.util.List;

/**
 * The one OpenRewrite recipe this tool applies: the official Spring-maintained
 * Boot 2 → 3 migration recipe. Versions are pinned exactly rather than
 * resolved via {@code RELEASE}, so a run today and a run months from now
 * apply the same rules to the same commit (DESIGN.md section 1.1, open
 * question 5).
 *
 * <p>Verified directly against the artifacts, not just documentation: the
 * recipe name below was confirmed by downloading
 * {@code rewrite-spring-6.37.1.jar} and reading
 * {@code META-INF/rewrite/spring-boot-35.yml}, which chains down through
 * {@code UpgradeSpringBoot_3_4} → ... → {@code _3_0}, and confirmed that the
 * {@code _3_0} step includes {@code org.openrewrite.java.migrate.UpgradeToJava17}
 * and the javax→jakarta artifact coordinate swaps.
 */
public final class SpringBootMigrationRecipe {

    public static final String PLUGIN_VERSION = "6.46.1";
    public static final String RECIPE_ARTIFACT_COORDINATES = "org.openrewrite.recipe:rewrite-spring:6.37.1";
    public static final String ACTIVE_RECIPE = "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5";

    /**
     * OpenRewrite's own tooling requires a modern JDK to run, independent of
     * whatever JDK the target repo currently declares (which, pre-migration,
     * is often 8 or 11).
     */
    public static final int REQUIRED_JDK_MAJOR_VERSION = 17;

    private SpringBootMigrationRecipe() {
    }

    /**
     * Arguments to run after {@code mvn} inside the sandbox. Invoked as a
     * plain CLI goal rather than injecting the plugin into the target
     * repo's {@code pom.xml} — keeps the sandbox's write footprint minimal
     * and avoids the plugin's own bootstrap entry showing up as a "change"
     * in the before/after diff.
     */
    public static List<String> mavenArgs() {
        return List.of(
                "-U",
                "org.openrewrite.maven:rewrite-maven-plugin:" + PLUGIN_VERSION + ":run",
                "-Drewrite.recipeArtifactCoordinates=" + RECIPE_ARTIFACT_COORDINATES,
                "-Drewrite.activeRecipes=" + ACTIVE_RECIPE
        );
    }
}
