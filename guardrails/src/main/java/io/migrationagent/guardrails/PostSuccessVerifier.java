package io.migrationagent.guardrails;

import io.migrationagent.buildparse.BuildResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The backstop behind the per-patch guardrails: even a patch that dodges
 * every diff-pattern check still has to leave the repo with at least as
 * many tests, and no more skipped, than the original baseline — otherwise
 * a "success" is rejected after the fact (DESIGN.md section 10).
 *
 * <p>JaCoCo coverage comparison is explicitly not implemented here — doing
 * it properly needs a JaCoCo XML report parser this project doesn't have,
 * and fabricating a coverage number would violate the project's own
 * "never invent or estimate metrics" rule. Coverage is simply absent from
 * {@link PostSuccessVerdict}, not faked.
 */
public final class PostSuccessVerifier {

    public record PostSuccessVerdict(boolean passed, List<String> violations) {
    }

    public PostSuccessVerdict verify(BuildResult baseline, BuildResult finalResult) {
        List<String> violations = new ArrayList<>();

        if (finalResult.testsTotal() < baseline.testsTotal()) {
            violations.add("test count decreased: baseline=" + baseline.testsTotal()
                    + " final=" + finalResult.testsTotal());
        }
        if (finalResult.testsSkipped() > baseline.testsSkipped()) {
            violations.add("skipped test count increased: baseline=" + baseline.testsSkipped()
                    + " final=" + finalResult.testsSkipped());
        }

        return new PostSuccessVerdict(violations.isEmpty(), violations);
    }
}
