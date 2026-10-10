package io.migrationagent.buildparse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the one thing Surefire's XML reports never contain: compiler
 * errors. Maven's compiler plugin prints these to the console in the form
 * {@code [ERROR] /path/Foo.java:[line,col] message}, one line per error,
 * so this is a line-oriented regex scan rather than a real parser.
 *
 * <p>Known limitation, worth calling out rather than hiding: this only
 * catches errors in that specific javac-via-maven-compiler-plugin format.
 * A build failure from, say, an annotation processor or a plugin that
 * doesn't follow this convention won't be picked up here — it would surface
 * as {@code compiled=false} with an empty failure list, which is still an
 * honest (if less actionable) signal rather than a silent miss.
 */
final class MavenConsoleParser {

    private static final Pattern COMPILE_ERROR_LINE = Pattern.compile(
            "^\\[ERROR]\\s+(?<file>[^\\s:]+\\.java):\\[(?<line>\\d+),(?<col>\\d+)]\\s+(?<message>.+)$");

    private MavenConsoleParser() {
    }

    static List<Failure> parseCompileErrors(String consoleOutput) {
        List<Failure> failures = new ArrayList<>();
        if (consoleOutput == null || consoleOutput.isEmpty()) {
            return failures;
        }

        String[] lines = consoleOutput.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = COMPILE_ERROR_LINE.matcher(lines[i]);
            if (!matcher.matches()) {
                continue;
            }
            String file = matcher.group("file");
            int line = Integer.parseInt(matcher.group("line"));
            String message = matcher.group("message");

            String rawExcerpt = collectFollowingErrorLines(lines, i);
            FailureCategory category = FailureClassifier.classify(null, message)
                    .orElse(FailureCategory.UNKNOWN);

            failures.add(new Failure(category, file, line, "compile error", message, rawExcerpt));
        }
        return failures;
    }

    /**
     * javac often explains one error over several consecutive {@code [ERROR]}
     * lines (e.g. "cannot find symbol" followed by "symbol:" and "location:"
     * detail lines) — this folds those into one failure's excerpt instead of
     * creating spurious extra {@link Failure} entries for the detail lines.
     */
    private static String collectFollowingErrorLines(String[] lines, int startIndex) {
        StringBuilder excerpt = new StringBuilder(lines[startIndex]);
        int i = startIndex + 1;
        while (i < lines.length
                && lines[i].startsWith("[ERROR]")
                && !COMPILE_ERROR_LINE.matcher(lines[i]).matches()) {
            excerpt.append('\n').append(lines[i]);
            i++;
        }
        return excerpt.toString();
    }
}
