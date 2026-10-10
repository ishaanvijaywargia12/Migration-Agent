package io.migrationagent.guardrails;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal unified-diff parser: just enough structure (per-file path pairs,
 * ordered content lines with their +/-/space prefix) for the guardrail
 * rules to pattern-match against. Not a general-purpose diff/patch library
 * — {@code org.eclipse.jgit.api.ApplyCommand} (used by {@code agent}'s
 * {@code ProposePatchTool}) is what actually applies the patch; this exists
 * purely to let guardrails inspect the diff's *content* before that happens.
 */
final class UnifiedDiff {

    record FileDiff(String oldPath, String newPath, List<String> hunkLines) {

        List<String> addedLines() {
            return hunkLines.stream().filter(l -> l.startsWith("+")).map(l -> l.substring(1)).toList();
        }

        List<String> removedLines() {
            return hunkLines.stream().filter(l -> l.startsWith("-")).map(l -> l.substring(1)).toList();
        }

        boolean isFullFileDeletion() {
            return "/dev/null".equals(newPath);
        }
    }

    private UnifiedDiff() {
    }

    static List<FileDiff> parse(String diffText) {
        List<FileDiff> files = new ArrayList<>();
        String[] lines = diffText.split("\n", -1);

        String oldPath = null;
        String newPath = null;
        List<String> hunkLines = null;

        for (String line : lines) {
            if (line.startsWith("--- ")) {
                flush(files, oldPath, newPath, hunkLines);
                oldPath = stripPrefix(line.substring(4).trim());
                newPath = null;
                hunkLines = new ArrayList<>();
            } else if (line.startsWith("+++ ") && hunkLines != null) {
                newPath = stripPrefix(line.substring(4).trim());
            } else if (hunkLines != null && (line.startsWith("+") || line.startsWith("-") || line.startsWith(" "))) {
                hunkLines.add(line);
            }
            // "@@ ... @@" hunk-position headers and anything else are ignored —
            // the rules only care about actual content lines.
        }
        flush(files, oldPath, newPath, hunkLines);
        return files;
    }

    private static void flush(List<FileDiff> files, String oldPath, String newPath, List<String> hunkLines) {
        if (oldPath != null && hunkLines != null) {
            files.add(new FileDiff(oldPath, newPath, hunkLines));
        }
    }

    private static String stripPrefix(String path) {
        if (path.equals("/dev/null")) {
            return path;
        }
        if (path.startsWith("a/") || path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }
}
