package io.migrationagent.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic triage fix for the one category OpenRewrite's recipe might
 * not have fully caught: a leftover {@code javax.*} import in a file the
 * recipe's parser skipped or that was added after the recipe ran. This is
 * harness-coded, reviewed, tested text substitution — the same trust level
 * as the OpenRewrite recipe step itself, not model output, so unlike
 * {@code propose_patch} it writes directly to the file rather than going
 * through {@code GuardrailEngine} (which exists to catch an LLM cheating,
 * not to second-guess a fixed, known-safe harness transformation).
 *
 * <p>Only packages with an unambiguous, total javax→jakarta rename are
 * included. {@code javax.annotation} is deliberately excluded:
 * {@code javax.annotation.processing.*} is JDK built-in and unrelated to
 * Jakarta EE, so a blanket substitution would wrongly rewrite it — the
 * handful of genuinely-moved {@code javax.annotation} types aren't worth
 * that false-positive risk. Left to the model if it comes up.
 */
final class JavaxJakartaAutoFixer {

    private static final Map<String, String> SAFE_PACKAGE_RENAMES = Map.of(
            "javax.persistence", "jakarta.persistence",
            "javax.servlet", "jakarta.servlet",
            "javax.validation", "jakarta.validation",
            "javax.transaction", "jakarta.transaction",
            "javax.ws.rs", "jakarta.ws.rs",
            "javax.xml.bind", "jakarta.xml.bind",
            "javax.mail", "jakarta.mail",
            "javax.jms", "jakarta.jms");

    boolean tryFix(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        String content = Files.readString(file);
        String fixed = content;
        for (Map.Entry<String, String> rename : SAFE_PACKAGE_RENAMES.entrySet()) {
            String importPattern = "\\bimport " + Pattern.quote(rename.getKey()) + "\\.";
            fixed = fixed.replaceAll(importPattern, "import " + rename.getValue() + ".");
        }

        if (fixed.equals(content)) {
            return false;
        }
        Files.writeString(file, fixed);
        return true;
    }
}
