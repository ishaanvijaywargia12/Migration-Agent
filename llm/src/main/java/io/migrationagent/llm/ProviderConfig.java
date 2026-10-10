package io.migrationagent.llm;

/**
 * One provider's configuration, loaded from {@code config/providers.yaml}.
 * No {@code name} field here deliberately — the provider's name is the key
 * it's stored under in {@link ProvidersConfig#providers()}, and duplicating
 * it as a field inside the value would risk the two disagreeing.
 *
 * <p>Rate limiting here is request-based (RPM) only, not token-based (TPM) —
 * a token-based limiter would need to estimate request size accurately
 * before sending, which is approximate anyway. Overall token spend is
 * bounded by {@link BudgetLimits} instead, and an occasional TPM-triggered
 * 429 is already handled by the same retry/backoff path as an RPM one.
 *
 * @param apiKeyEnv the name of an environment variable to read the key
 *                     from — never the key value itself, see DESIGN.md
 *                     section 13
 * @param rpd          nullable: not every provider's free tier publishes a
 *                     daily cap (only a per-minute one)
 */
public record ProviderConfig(
        String baseUrl,
        String apiKeyEnv,
        String model,
        int rpm,
        Integer rpd,
        boolean enabled
) {
}
