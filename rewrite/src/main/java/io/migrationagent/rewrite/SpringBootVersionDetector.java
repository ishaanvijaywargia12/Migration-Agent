package io.migrationagent.rewrite;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether a repo's root {@code pom.xml} declares a Spring Boot 3.x+
 * parent, by scanning raw text rather than a full POM model resolve — the
 * same deliberate simplification {@code DeclaredJdkDetector} uses, for the
 * same reason (no profile activation or parent-POM property inheritance
 * needed for this project's small, single-declaration benchmark repos).
 *
 * <p>Exists because a build/test pass alone can't tell "migrated to Boot 3"
 * apart from "happened to still compile on JDK 17 without migrating at
 * all" — Boot 2.7 application code is frequently JDK-17-compatible as-is,
 * since JDK 17 running old code is unrelated to Boot 3 requiring JDK 17. An
 * ablation mode that can skip the OpenRewrite recipe entirely (LLM_ONLY)
 * needs this as a real check, not just a clean build, before it can claim
 * the migration actually happened.
 */
public final class SpringBootVersionDetector {

    private static final Pattern PARENT_BLOCK = Pattern.compile("<parent>(.*?)</parent>", Pattern.DOTALL);
    private static final Pattern VERSION_TAG = Pattern.compile("<version>\\s*([^<]+?)\\s*</version>");

    public boolean isBoot3OrHigher(String pomXmlContent) {
        Matcher parentMatcher = PARENT_BLOCK.matcher(pomXmlContent);
        while (parentMatcher.find()) {
            String parentBlock = parentMatcher.group(1);
            if (!parentBlock.contains("spring-boot-starter-parent")) {
                continue;
            }
            Optional<Integer> majorVersion = firstMajorVersion(parentBlock);
            if (majorVersion.isPresent()) {
                return majorVersion.get() >= 3;
            }
        }
        return false;
    }

    private Optional<Integer> firstMajorVersion(String parentBlock) {
        Matcher versionMatcher = VERSION_TAG.matcher(parentBlock);
        if (!versionMatcher.find()) {
            return Optional.empty();
        }
        String version = versionMatcher.group(1).trim();
        int dotIndex = version.indexOf('.');
        String majorPart = dotIndex == -1 ? version : version.substring(0, dotIndex);
        try {
            return Optional.of(Integer.parseInt(majorPart));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
