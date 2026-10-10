package io.migrationagent.sandbox;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the JDK version a Maven project declares, by scanning the raw
 * text of its root {@code pom.xml} for the properties Maven and Spring Boot
 * conventionally use for this.
 *
 * <p>This is a deliberately simple text scan, not a full POM model resolve
 * (no profile activation, no property inheritance across a parent POM). For
 * the small, single-declaration benchmark repos this tool targets in the
 * MVP, the property is reliably a direct child of {@code <properties>} in
 * the repo's own root pom.xml, so this is sufficient — and it avoids
 * pulling in a full Maven model-building dependency just to read one
 * number. If a target repo declares its JDK version some other way, this
 * returns empty and the caller applies its own documented default.
 */
public final class DeclaredJdkDetector {

    private static final Pattern COMPILER_RELEASE = tagPattern("maven.compiler.release");
    private static final Pattern COMPILER_TARGET = tagPattern("maven.compiler.target");
    private static final Pattern COMPILER_SOURCE = tagPattern("maven.compiler.source");
    private static final Pattern SPRING_BOOT_JAVA_VERSION = tagPattern("java.version");

    public Optional<Integer> detect(String pomXmlContent) {
        return firstMatch(pomXmlContent, COMPILER_RELEASE)
                .or(() -> firstMatch(pomXmlContent, COMPILER_TARGET))
                .or(() -> firstMatch(pomXmlContent, COMPILER_SOURCE))
                .or(() -> firstMatch(pomXmlContent, SPRING_BOOT_JAVA_VERSION));
    }

    private Optional<Integer> firstMatch(String pomXmlContent, Pattern pattern) {
        Matcher matcher = pattern.matcher(pomXmlContent);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return parseMajorVersion(matcher.group(1));
    }

    private Optional<Integer> parseMajorVersion(String rawValue) {
        String value = rawValue.trim();
        // Old-style versions like "1.8" mean Java 8.
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Pattern tagPattern(String tagName) {
        return Pattern.compile("<" + Pattern.quote(tagName) + ">\\s*([^<]+?)\\s*</" + Pattern.quote(tagName) + ">");
    }
}
