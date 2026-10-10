package io.migrationagent.guardrails;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A patch may only touch files inside the repository it was scoped to.
 */
public final class PathContainmentGuardrail implements GuardrailRule {

    @Override
    public String name() {
        return "PATH_CONTAINMENT";
    }

    @Override
    public GuardrailVerdict check(String unifiedDiff, Path repoRoot) {
        Set<String> paths = new LinkedHashSet<>();
        for (UnifiedDiff.FileDiff file : UnifiedDiff.parse(unifiedDiff)) {
            if (!"/dev/null".equals(file.oldPath())) {
                paths.add(file.oldPath());
            }
            if (!"/dev/null".equals(file.newPath())) {
                paths.add(file.newPath());
            }
        }

        Path root = repoRoot.toAbsolutePath().normalize();
        for (String path : paths) {
            Path resolved = root.resolve(path).normalize();
            if (!resolved.startsWith(root)) {
                return GuardrailVerdict.rejected(name(), "patch touches a path outside the repository: " + path);
            }
        }
        return GuardrailVerdict.ok();
    }
}
