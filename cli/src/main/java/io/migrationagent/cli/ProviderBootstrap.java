package io.migrationagent.cli;

import io.migrationagent.agent.ModelCascade;
import io.migrationagent.llm.BudgetTracker;
import io.migrationagent.llm.ChatClient;
import io.migrationagent.llm.EnvFile;
import io.migrationagent.llm.OpenAiCompatibleChatClient;
import io.migrationagent.llm.ProviderConfig;
import io.migrationagent.llm.ProvidersConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code config/providers.yaml} and {@code .env} and produces ready
 * {@link ChatClient}s. This is the one place in the whole codebase that
 * ever holds a resolved API key value — it's read here, handed straight
 * into the {@code llm} module's client, and never passed to {@code agent},
 * {@code trace}, or anywhere else (DESIGN.md section 12, 13).
 */
final class ProviderBootstrap {

    /**
     * Defaults preserve every existing call site's behavior unchanged.
     * Overriding either path is how someone who clones this repo brings
     * their own keys/providers without ever touching the committed
     * {@code config/providers.yaml} or needing a {@code .env} at the repo
     * root — see the {@code --env-file}/{@code --providers-config} options
     * on {@code migrate} and {@code evaluate}.
     */
    private static final Path DEFAULT_PROVIDERS_CONFIG = Path.of("config/providers.yaml");
    private static final Path DEFAULT_ENV_FILE = Path.of(".env");

    record Bootstrapped(ChatClient chatClient, String providerName, String model) {
    }

    private ProviderBootstrap() {
    }

    static Bootstrapped load(String providerName) throws IOException {
        return load(providerName, DEFAULT_PROVIDERS_CONFIG, DEFAULT_ENV_FILE);
    }

    static Bootstrapped load(String providerName, Path providersConfigPath, Path envFilePath) throws IOException {
        ProvidersConfig config = ProvidersConfig.load(providersConfigPath);
        BudgetTracker budgetTracker = newBudgetTracker(config);
        ProviderConfig providerConfig = requireEnabled(config, providerName);
        return buildTier(providerName, providerConfig, budgetTracker, envFilePath);
    }

    /**
     * Builds a cascade from every <i>enabled</i> provider in
     * {@code cascade_order}, in that order, sharing one {@link BudgetTracker}
     * across all tiers — {@code config/providers.yaml}'s {@code budgets:}
     * section is a single top-level block, not per-provider, so the budget
     * is a whole-run limit regardless of which tier a given call lands on.
     *
     * <p>A provider missing its API key is skipped with a printed warning
     * rather than failing the whole cascade — not having an optional
     * secondary tier configured shouldn't block a working primary one.
     */
    static ModelCascade loadCascade() throws IOException {
        return loadCascade(DEFAULT_PROVIDERS_CONFIG, DEFAULT_ENV_FILE);
    }

    static ModelCascade loadCascade(Path providersConfigPath, Path envFilePath) throws IOException {
        ProvidersConfig config = ProvidersConfig.load(providersConfigPath);
        BudgetTracker budgetTracker = newBudgetTracker(config);
        Map<String, String> envFile = EnvFile.load(envFilePath);

        List<ModelCascade.Tier> tiers = new ArrayList<>();
        for (String providerName : config.cascadeOrder()) {
            ProviderConfig providerConfig = config.get(providerName);
            if (!providerConfig.enabled()) {
                continue;
            }
            String apiKey = resolveApiKey(providerConfig, envFile);
            if (apiKey == null) {
                System.out.println("Skipping provider '" + providerName + "' in the cascade — no "
                        + providerConfig.apiKeyEnv() + " found in .env or the environment.");
                continue;
            }
            tiers.add(new ModelCascade.Tier(
                    new OpenAiCompatibleChatClient(providerConfig, apiKey, budgetTracker),
                    providerName, providerConfig.model()));
        }

        if (tiers.isEmpty()) {
            throw new IOException("No usable providers — every cascade tier is either disabled "
                    + "in config/providers.yaml or missing its API key.");
        }
        return new ModelCascade(tiers);
    }

    private static Bootstrapped buildTier(
            String providerName, ProviderConfig providerConfig, BudgetTracker budgetTracker, Path envFilePath)
            throws IOException {
        Map<String, String> envFile = EnvFile.load(envFilePath);
        String apiKey = resolveApiKey(providerConfig, envFile);
        if (apiKey == null) {
            throw new IOException("No API key found for provider '" + providerName + "' — set "
                    + providerConfig.apiKeyEnv() + " in .env or the environment.");
        }
        ChatClient chatClient = new OpenAiCompatibleChatClient(providerConfig, apiKey, budgetTracker);
        return new Bootstrapped(chatClient, providerName, providerConfig.model());
    }

    private static ProviderConfig requireEnabled(ProvidersConfig config, String providerName) throws IOException {
        ProviderConfig providerConfig = config.get(providerName);
        if (!providerConfig.enabled()) {
            throw new IOException("Provider '" + providerName + "' is disabled in config/providers.yaml");
        }
        return providerConfig;
    }

    private static String resolveApiKey(ProviderConfig providerConfig, Map<String, String> envFile) {
        String apiKey = envFile.get(providerConfig.apiKeyEnv());
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv(providerConfig.apiKeyEnv());
        }
        return (apiKey == null || apiKey.isBlank()) ? null : apiKey;
    }

    private static BudgetTracker newBudgetTracker(ProvidersConfig config) throws IOException {
        Path budgetStateFile = Path.of(System.getProperty("user.home"), ".migration-agent", "budget-state.json");
        return new BudgetTracker(config.budgets(), budgetStateFile);
    }
}
