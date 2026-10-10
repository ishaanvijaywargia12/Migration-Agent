package io.migrationagent.guardrails;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rejects a patch that, within a test file, adds {@code @Disabled}/
 * {@code @Ignore}, or leaves a test method with fewer assertion calls than
 * it had before. The assertion check is a line-count heuristic (removed
 * assertion-shaped lines outnumbering added ones) rather than true
 * per-method before/after comparison — a documented MVP limitation
 * (DESIGN.md section 10), backed by {@link PostSuccessVerifier} as the
 * final check that test count didn't actually drop.
 */
public final class NoTestWeakeningGuardrail implements GuardrailRule {

    private static final Pattern TEST_ROOT = Pattern.compile("src/test/(java|kotlin)/");
    private static final Pattern DISABLING_ANNOTATION = Pattern.compile("@(\\w+\\.)*(Disabled|Ignore)\\b");
    private static final Pattern ASSERTION_CALL = Pattern.compile("\\b(assert\\w*|verify|fail)\\s*\\(");

    @Override
    public String name() {
        return "NO_TEST_WEAKENING";
    }

    @Override
    public GuardrailVerdict check(String unifiedDiff, Path repoRoot) {
        for (UnifiedDiff.FileDiff file : UnifiedDiff.parse(unifiedDiff)) {
            boolean touchesTestRoot = TEST_ROOT.matcher(file.oldPath()).find()
                    || (file.newPath() != null && TEST_ROOT.matcher(file.newPath()).find());
            if (!touchesTestRoot || file.isFullFileDeletion()) {
                continue;
            }

            for (String addedLine : file.addedLines()) {
                if (DISABLING_ANNOTATION.matcher(addedLine).find()) {
                    return GuardrailVerdict.rejected(name(),
                            "adds @Disabled/@Ignore in test file " + file.oldPath());
                }
            }

            int removedAssertions = countMatches(file.removedLines(), ASSERTION_CALL);
            int addedAssertions = countMatches(file.addedLines(), ASSERTION_CALL);
            if (removedAssertions > addedAssertions) {
                return GuardrailVerdict.rejected(name(),
                        "reduces assertion count in " + file.oldPath()
                                + " (removed " + removedAssertions + ", added " + addedAssertions + ")");
            }
        }
        return GuardrailVerdict.ok();
    }

    private int countMatches(List<String> lines, Pattern pattern) {
        int count = 0;
        for (String line : lines) {
            if (pattern.matcher(line).find()) {
                count++;
            }
        }
        return count;
    }
}
