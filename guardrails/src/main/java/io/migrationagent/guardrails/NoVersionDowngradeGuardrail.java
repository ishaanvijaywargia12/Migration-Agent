package io.migrationagent.guardrails;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects a patch that downgrades the Spring Boot parent version below 3.0
 * or the declared Java version below 17 — the migration's whole point is
 * moving forward, so a "fix" that moves either backward isn't a fix.
 *
 * <p>Looks for a removed version line immediately paired with an added one
 * for the same property in a pom.xml hunk (the common shape a version bump
 * takes in a diff), rather than needing to read the pre-patch file
 * separately — keeps this rule self-contained to the diff text, like the
 * others.
 */
public final class NoVersionDowngradeGuardrail implements GuardrailRule {

    private static final int MIN_SPRING_BOOT_MAJOR = 3;
    private static final int MIN_JAVA_VERSION = 17;

    private static final Pattern POM_FILE = Pattern.compile("pom\\.xml$");
    private static final Pattern SPRING_BOOT_VERSION = Pattern.compile("<version>(\\d+)\\.\\d+\\.\\d+(?:[.-]\\S*)?</version>");
    private static final Pattern JAVA_VERSION_TAG = Pattern.compile(
            "<(java\\.version|maven\\.compiler\\.release|maven\\.compiler\\.target|maven\\.compiler\\.source)>(\\d+)</\\1>");

    @Override
    public String name() {
        return "NO_VERSION_DOWNGRADE";
    }

    @Override
    public GuardrailVerdict check(String unifiedDiff, Path repoRoot) {
        for (UnifiedDiff.FileDiff file : UnifiedDiff.parse(unifiedDiff)) {
            if (!POM_FILE.matcher(file.oldPath()).find()) {
                continue;
            }

            Integer removedJavaVersion = firstJavaVersion(file.removedLines());
            Integer addedJavaVersion = firstJavaVersion(file.addedLines());
            if (removedJavaVersion != null && addedJavaVersion != null
                    && removedJavaVersion >= MIN_JAVA_VERSION && addedJavaVersion < MIN_JAVA_VERSION) {
                return GuardrailVerdict.rejected(name(),
                        "downgrades Java version from " + removedJavaVersion + " to " + addedJavaVersion);
            }

            Integer removedBootMajor = firstSpringBootMajor(file.removedLines());
            Integer addedBootMajor = firstSpringBootMajor(file.addedLines());
            if (removedBootMajor != null && addedBootMajor != null
                    && removedBootMajor >= MIN_SPRING_BOOT_MAJOR && addedBootMajor < MIN_SPRING_BOOT_MAJOR) {
                return GuardrailVerdict.rejected(name(),
                        "downgrades Spring Boot major version from " + removedBootMajor + " to " + addedBootMajor);
            }
        }
        return GuardrailVerdict.ok();
    }

    private Integer firstJavaVersion(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = JAVA_VERSION_TAG.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(2));
            }
        }
        return null;
    }

    private Integer firstSpringBootMajor(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = SPRING_BOOT_VERSION.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }
}
