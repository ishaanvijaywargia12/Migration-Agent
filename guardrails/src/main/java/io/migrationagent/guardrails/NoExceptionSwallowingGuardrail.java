package io.migrationagent.guardrails;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rejects a patch that adds a {@code catch} block in a test file whose body
 * is empty or merely logs the exception — a classic way to make a failing
 * assertion "pass" by swallowing the exception that would have propagated
 * it. Heuristic, not a parser: looks within each contiguous run of added
 * lines for a {@code catch (...)} followed only by blank/log lines before
 * the block closes, with no {@code throw}/{@code fail}/{@code assert}
 * anywhere in between.
 */
public final class NoExceptionSwallowingGuardrail implements GuardrailRule {

    private static final Pattern TEST_ROOT = Pattern.compile("src/test/(java|kotlin)/");
    private static final Pattern CATCH_LINE = Pattern.compile("catch\\s*\\(");
    private static final Pattern BLOCK_CLOSE = Pattern.compile("^\\s*}\\s*\\)?;?\\s*$");
    private static final Pattern LOG_ONLY_LINE = Pattern.compile(
            "^\\s*(log|logger)\\.\\w+\\(|System\\.(out|err)\\.|printStackTrace\\(");
    private static final Pattern REAL_HANDLING = Pattern.compile("\\b(throw|fail|assert|Assertions)\\b");

    @Override
    public String name() {
        return "NO_EXCEPTION_SWALLOWING";
    }

    @Override
    public GuardrailVerdict check(String unifiedDiff, Path repoRoot) {
        for (UnifiedDiff.FileDiff file : UnifiedDiff.parse(unifiedDiff)) {
            boolean touchesTestRoot = TEST_ROOT.matcher(file.oldPath()).find()
                    || (file.newPath() != null && TEST_ROOT.matcher(file.newPath()).find());
            if (!touchesTestRoot) {
                continue;
            }

            for (List<String> addedRun : consecutiveAddedRuns(file.hunkLines())) {
                if (hasSwallowedException(addedRun)) {
                    return GuardrailVerdict.rejected(name(),
                            "adds an empty or log-only catch block in test file " + file.oldPath());
                }
            }
        }
        return GuardrailVerdict.ok();
    }

    private boolean hasSwallowedException(List<String> addedLines) {
        for (int i = 0; i < addedLines.size(); i++) {
            if (!CATCH_LINE.matcher(addedLines.get(i)).find()) {
                continue;
            }
            for (int j = i + 1; j < addedLines.size(); j++) {
                String line = addedLines.get(j);
                if (BLOCK_CLOSE.matcher(line).matches()) {
                    return true; // closed without ever seeing real handling
                }
                if (REAL_HANDLING.matcher(line).find()) {
                    break; // legitimately handles/rethrows — not swallowing
                }
                if (!line.isBlank() && !LOG_ONLY_LINE.matcher(line).find()) {
                    break; // some other real statement — not what we flag here
                }
            }
        }
        return false;
    }

    private List<List<String>> consecutiveAddedRuns(List<String> hunkLines) {
        List<List<String>> runs = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : hunkLines) {
            if (line.startsWith("+")) {
                current.add(line.substring(1));
            } else if (!current.isEmpty()) {
                runs.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            runs.add(current);
        }
        return runs;
    }
}
