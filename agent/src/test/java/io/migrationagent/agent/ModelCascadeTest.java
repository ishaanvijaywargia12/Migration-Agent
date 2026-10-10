package io.migrationagent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelCascadeTest {

    @Test
    void startsAtTheFirstTier() {
        ModelCascade cascade = new ModelCascade(List.of(
                new ModelCascade.Tier(null, "groq", "model-a"),
                new ModelCascade.Tier(null, "openrouter", "model-b")));

        assertThat(cascade.currentTierIndex()).isZero();
        assertThat(cascade.current().providerName()).isEqualTo("groq");
    }

    @Test
    void escalatesToTheNextTier() {
        ModelCascade cascade = new ModelCascade(List.of(
                new ModelCascade.Tier(null, "groq", "model-a"),
                new ModelCascade.Tier(null, "openrouter", "model-b")));

        cascade.escalate();

        assertThat(cascade.currentTierIndex()).isEqualTo(1);
        assertThat(cascade.current().providerName()).isEqualTo("openrouter");
    }

    @Test
    void neverEscalatesPastTheLastTier() {
        ModelCascade cascade = ModelCascade.singleTier(null, "groq", "model-a");

        assertThat(cascade.hasNextTier()).isFalse();
        cascade.escalate();

        assertThat(cascade.currentTierIndex()).isZero();
    }

    @Test
    void rejectsAnEmptyTierList() {
        assertThatThrownBy(() -> new ModelCascade(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
