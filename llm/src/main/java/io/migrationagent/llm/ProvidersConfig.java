package io.migrationagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The whole of {@code config/providers.yaml}: every configured provider,
 * the order the model cascade (Phase 5) tries them in, and the shared
 * budget limits. {@code providers} is a plain map, not a fixed set of
 * fields, so adding a new provider (e.g. a bring-your-own-key OpenAI/custom
 * entry) is a config change only — nothing here assumes a specific set of
 * provider names.
 */
public record ProvidersConfig(
        Map<String, ProviderConfig> providers,
        List<String> cascadeOrder,
        BudgetLimits budgets
) {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public static ProvidersConfig load(Path yamlFile) throws IOException {
        return YAML_MAPPER.readValue(yamlFile.toFile(), ProvidersConfig.class);
    }

    public ProviderConfig get(String providerName) {
        ProviderConfig config = providers.get(providerName);
        if (config == null) {
            throw new IllegalArgumentException("No provider configured named '" + providerName + "'");
        }
        return config;
    }
}
