package io.migrationagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.migrationagent.guardrails.GuardrailEngine;
import io.migrationagent.guardrails.GuardrailVerdict;
import io.migrationagent.llm.FunctionDefinition;
import io.migrationagent.llm.ToolDefinition;
import org.eclipse.jgit.api.ApplyResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Applies a unified diff to the target repo's working tree via JGit, after
 * running it through the full {@link GuardrailEngine} (Phase 4) — test
 * deletion, assertion weakening, exception swallowing, version downgrades,
 * and path containment, in that order, all enforced here in harness code
 * before the model's claimed "rationale" is ever trusted (DESIGN.md
 * section 10, 12).
 */
public final class ProposePatchTool implements AgentTool {

    private final Path repoRoot;
    private final GuardrailEngine guardrailEngine = new GuardrailEngine();

    public ProposePatchTool(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    @Override
    public String name() {
        return "propose_patch";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(new FunctionDefinition(
                name(),
                "Propose a unified diff to apply to the target repo. Validated against guardrails before being applied.",
                JsonSchemas.parse("""
                        {
                          "type": "object",
                          "properties": {
                            "unified_diff": {"type": "string"},
                            "rationale": {"type": "string"}
                          },
                          "required": ["unified_diff", "rationale"]
                        }
                        """)));
    }

    @Override
    public String execute(JsonNode arguments) throws IOException {
        String unifiedDiff = arguments.get("unified_diff").asText();

        GuardrailVerdict verdict = guardrailEngine.validate(unifiedDiff, repoRoot);
        if (!verdict.accepted()) {
            return "REJECTED by " + verdict.ruleName() + ": " + verdict.reason();
        }

        try (Git git = Git.open(repoRoot.toFile())) {
            ApplyResult result = git.apply()
                    .setPatch(new ByteArrayInputStream(unifiedDiff.getBytes(StandardCharsets.UTF_8)))
                    .call();
            return "Applied. Updated files: " + result.getUpdatedFiles();
        } catch (GitAPIException e) {
            return "REJECTED: patch did not apply cleanly — " + e.getMessage();
        }
    }
}
