package io.migrationagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Talks to any OpenAI-compatible {@code /chat/completions} endpoint — Groq,
 * OpenRouter, and Gemini's compatibility shim all speak the same request/
 * response shape, which is the entire point of "OpenAI-compatible"
 * (DESIGN.md section 7.2). One thin client, parameterized by
 * {@link ProviderConfig}, rather than a provider-specific SDK per tier.
 */
public final class OpenAiCompatibleChatClient implements ChatClient {

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final Duration DEFAULT_BASE_BACKOFF = Duration.ofSeconds(2);

    private final ProviderConfig config;
    private final String apiKey;
    private final BudgetTracker budgetTracker;
    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;
    private final ObjectMapper mapper;
    private final int maxRetries;
    private final Duration baseBackoff;
    private final RateLimiter.Sleeper sleeper;

    public OpenAiCompatibleChatClient(ProviderConfig config, String apiKey, BudgetTracker budgetTracker) {
        this(config, apiKey, budgetTracker, DEFAULT_MAX_RETRIES, DEFAULT_BASE_BACKOFF, Thread::sleep);
    }

    OpenAiCompatibleChatClient(
            ProviderConfig config, String apiKey, BudgetTracker budgetTracker,
            int maxRetries, Duration baseBackoff, RateLimiter.Sleeper sleeper) {
        this.config = config;
        this.apiKey = apiKey;
        this.budgetTracker = budgetTracker;
        this.httpClient = HttpClient.newHttpClient();
        this.rateLimiter = new RateLimiter(config.rpm());
        this.mapper = ChatJson.mapper();
        this.maxRetries = maxRetries;
        this.baseBackoff = baseBackoff;
        this.sleeper = sleeper;
    }

    @Override
    public ChatCompletionResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools)
            throws IOException, InterruptedException {
        int estimatedTokens = estimateTokens(messages);
        if (!budgetTracker.canProceed(estimatedTokens)) {
            throw new BudgetExceededException(
                    "Budget exceeded — refusing to call the model. See ~/.migration-agent/budget-state.json");
        }

        String requestBody = mapper.writeValueAsString(new ChatCompletionRequest(
                config.model(), messages, tools, tools.isEmpty() ? null : "auto"));

        HttpResponse<String> response = sendWithRetry(requestBody);
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Chat completion failed: HTTP " + response.statusCode()
                    + " " + Redactor.redact(truncate(response.body())));
        }

        ChatCompletionResponse parsed = mapper.readValue(response.body(), ChatCompletionResponse.class);
        int actualTokens = parsed.usage() != null ? parsed.usage().totalTokens() : estimatedTokens;
        budgetTracker.record(1, actualTokens);
        return parsed;
    }

    private HttpResponse<String> sendWithRetry(String requestBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        int attempt = 0;
        while (true) {
            rateLimiter.acquire();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 429 || attempt >= maxRetries) {
                return response;
            }
            Duration backoff = retryAfter(response).orElse(baseBackoff.multipliedBy(1L << attempt));
            sleeper.sleep(backoff.toMillis());
            attempt++;
        }
    }

    private Optional<Duration> retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(value.trim()));
                    } catch (NumberFormatException notAnInteger) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull);
    }

    /**
     * Rough token estimate (~4 characters per token, a common heuristic for
     * English text) used only for the budget pre-check before a real
     * {@code usage} figure comes back from the provider. Deliberately
     * approximate and documented as such — the real count from the response
     * is what actually gets recorded via {@link BudgetTracker#record}.
     */
    private int estimateTokens(List<ChatMessage> messages) {
        int totalChars = 0;
        for (ChatMessage message : messages) {
            if (message.content() != null) {
                totalChars += message.content().length();
            }
        }
        return Math.max(1, totalChars / 4);
    }

    private String truncate(String text) {
        int maxLength = 500;
        return text.length() > maxLength ? text.substring(0, maxLength) + "... [truncated]" : text;
    }
}
