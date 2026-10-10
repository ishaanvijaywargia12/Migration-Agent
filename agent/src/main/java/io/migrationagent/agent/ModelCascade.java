package io.migrationagent.agent;

import io.migrationagent.llm.ChatClient;

import java.util.List;

/**
 * Tries a small/fast free model first, escalating to the next configured
 * tier only after a <i>verified</i> failure — the previous tier's patch
 * attempt didn't reduce the failure count (DESIGN.md section 7.4). This is
 * one-directional: once escalated, the loop never falls back to an earlier
 * (presumably weaker) tier within the same run.
 *
 * <p>Escalation here is per-iteration, not per-failure-group as DESIGN.md's
 * pseudocode sketches — a deliberate Phase 5 scope simplification. Tracking
 * "which tier is assigned to which specific failure" would mean splitting
 * the agent loop into per-category sub-loops, a bigger structural change to
 * working Phase 3 code than this phase's budget justifies. Per-iteration
 * escalation still captures the core idea (don't keep paying for a tier
 * that's demonstrably not helping) without that refactor.
 */
public final class ModelCascade {

    public record Tier(ChatClient chatClient, String providerName, String model) {
    }

    private final List<Tier> tiers;
    private int currentIndex = 0;

    public ModelCascade(List<Tier> tiers) {
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("ModelCascade needs at least one tier");
        }
        this.tiers = tiers;
    }

    public static ModelCascade singleTier(ChatClient chatClient, String providerName, String model) {
        return new ModelCascade(List.of(new Tier(chatClient, providerName, model)));
    }

    public Tier current() {
        return tiers.get(currentIndex);
    }

    public List<String> allProviderNames() {
        return tiers.stream().map(Tier::providerName).toList();
    }

    public int currentTierIndex() {
        return currentIndex;
    }

    public boolean hasNextTier() {
        return currentIndex < tiers.size() - 1;
    }

    public void escalate() {
        if (hasNextTier()) {
            currentIndex++;
        }
    }
}
