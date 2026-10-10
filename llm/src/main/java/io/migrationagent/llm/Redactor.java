package io.migrationagent.llm;

import java.util.regex.Pattern;

/**
 * Defense-in-depth redaction for anything that might get logged, traced, or
 * included in an error message. This is <b>not</b> the primary secrecy
 * control — the primary control is that credential values are only ever
 * held by {@link OpenAiCompatibleChatClient} and never passed to the
 * {@code agent}, {@code trace}, or {@code report} modules (DESIGN.md
 * section 12, section 13). This exists in case a raw HTTP error body or
 * exception message ever echoes a key back.
 */
public final class Redactor {

    // Known key shapes for the three configured providers, plus a generic
    // fallback for long bearer-token-looking strings.
    private static final Pattern[] KEY_PATTERNS = {
            Pattern.compile("gsk_[A-Za-z0-9]{20,}"),
            Pattern.compile("sk-or-[A-Za-z0-9-]{20,}"),
            Pattern.compile("AIza[A-Za-z0-9_-]{30,}"),
            Pattern.compile("Bearer\\s+[A-Za-z0-9._-]{20,}")
    };

    private Redactor() {
    }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (Pattern pattern : KEY_PATTERNS) {
            result = pattern.matcher(result).replaceAll("[REDACTED]");
        }
        return result;
    }
}
