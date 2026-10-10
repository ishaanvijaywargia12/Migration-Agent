package io.migrationagent.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal {@code KEY=value} parser for {@code .env} files. Deliberately not
 * a third-party dotenv library — this is the entire format we need to
 * support (DESIGN.md section 13), and one less dependency to audit for a
 * file that touches secrets.
 */
public final class EnvFile {

    private EnvFile() {
    }

    public static Map<String, String> load(Path envFilePath) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(envFilePath)) {
            return values;
        }
        for (String line : Files.readAllLines(envFilePath)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String key = trimmed.substring(0, equalsIndex).trim();
            String value = trimmed.substring(equalsIndex + 1).trim();
            values.put(key, stripSurroundingQuotes(value));
        }
        return values;
    }

    private static String stripSurroundingQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
