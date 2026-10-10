package io.migrationagent.guardrails;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Rejects a patch that deletes a whole test file, or removes a
 * {@code @Test}-annotated method from one.
 */
public final class NoTestDeletionGuardrail implements GuardrailRule {

    private static final Pattern TEST_ROOT = Pattern.compile("src/test/(java|kotlin)/");
    private static final Pattern TEST_ANNOTATION = Pattern.compile("@(\\w+\\.)*Test\\b");

    @Override
    public String name() {
        return "NO_TEST_DELETION";
    }

    @Override
    public GuardrailVerdict check(String unifiedDiff, Path repoRoot) {
        for (UnifiedDiff.FileDiff file : UnifiedDiff.parse(unifiedDiff)) {
            boolean touchesTestRoot = TEST_ROOT.matcher(file.oldPath()).find()
                    || (file.newPath() != null && TEST_ROOT.matcher(file.newPath()).find());
            if (!touchesTestRoot) {
                continue;
            }

            if (file.isFullFileDeletion()) {
                return GuardrailVerdict.rejected(name(), "deletes a test file: " + file.oldPath());
            }

            for (String removedLine : file.removedLines()) {
                if (TEST_ANNOTATION.matcher(removedLine).find()) {
                    return GuardrailVerdict.rejected(name(),
                            "removes a @Test-annotated method in " + file.oldPath());
                }
            }
        }
        return GuardrailVerdict.ok();
    }
}
