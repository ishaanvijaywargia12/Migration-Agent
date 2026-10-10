package io.migrationagent.llm;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ProvidersConfigTest {

    @Test
    void loadsProviderFieldsFromSnakeCaseYaml() throws Exception {
        ProvidersConfig config = ProvidersConfig.load(fixturePath());

        ProviderConfig groq = config.get("groq");
        assertThat(groq.baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(groq.apiKeyEnv()).isEqualTo("GROQ_API_KEY");
        assertThat(groq.model()).isEqualTo("openai/gpt-oss-20b");
        assertThat(groq.rpm()).isEqualTo(30);
        assertThat(groq.rpd()).isEqualTo(1000);
        assertThat(groq.enabled()).isTrue();
    }

    @Test
    void loadsCascadeOrderAndBudgets() throws Exception {
        ProvidersConfig config = ProvidersConfig.load(fixturePath());

        assertThat(config.cascadeOrder()).containsExactly("groq", "gemini");
        assertThat(config.budgets().perRunRequests()).isEqualTo(60);
        assertThat(config.budgets().perDayTokens()).isEqualTo(1_000_000);
    }

    @Test
    void throwsAClearErrorForAnUnknownProviderName() throws Exception {
        ProvidersConfig config = ProvidersConfig.load(fixturePath());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> config.get("does-not-exist"));
    }

    private Path fixturePath() throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource("providers-fixture.yaml").toURI());
    }
}
