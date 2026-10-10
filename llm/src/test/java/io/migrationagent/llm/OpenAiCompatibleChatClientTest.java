package io.migrationagent.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real local HTTP server (JDK's built-in
 * {@code com.sun.net.httpserver}) rather than mocking the HTTP client —
 * this exercises the actual request serialization, response parsing, and
 * retry loop end to end, with no real network or provider account needed.
 */
class OpenAiCompatibleChatClientTest {

    private static final String SUCCESS_BODY = """
            {"choices":[{"message":{"role":"assistant","content":"hello","tool_calls":null},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
            """;

    // Reproduces a real failure: a Groq reasoning-capable model returned this
    // exact shape — a "reasoning" field the response DTOs don't declare —
    // and crashed every single model call until ChatJson was configured to
    // ignore unknown properties instead of failing on them.
    private static final String RESPONSE_WITH_UNKNOWN_FIELD = """
            {"choices":[{"message":{"role":"assistant","content":"hello","reasoning":"thinking it through...","tool_calls":null},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
            """;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesASuccessfulResponseAndRecordsRealUsage(@TempDir Path tempDir) throws Exception {
        server = startServer((exchange, callCount) -> respond(exchange, 200, SUCCESS_BODY));

        BudgetTracker budget = budgetTracker(tempDir);
        ChatClient client = newClient(budget);

        ChatCompletionResponse response = client.chat(List.of(ChatMessage.user("hi")), List.of());

        assertThat(response.firstMessage().content()).isEqualTo("hello");
        assertThat(response.usage().totalTokens()).isEqualTo(15);
        // The real usage figure from the response is what gets recorded,
        // not the rough pre-call estimate.
        assertThat(budget.runTokensUsed()).isEqualTo(15);
        assertThat(budget.runRequestsUsed()).isEqualTo(1);
    }

    @Test
    void toleratesUnknownFieldsInTheResponseInsteadOfFailingTheCall(@TempDir Path tempDir) throws Exception {
        server = startServer((exchange, callCount) -> respond(exchange, 200, RESPONSE_WITH_UNKNOWN_FIELD));

        ChatCompletionResponse response = newClient(budgetTracker(tempDir))
                .chat(List.of(ChatMessage.user("hi")), List.of());

        assertThat(response.firstMessage().content()).isEqualTo("hello");
    }

    @Test
    void retriesOn429AndSucceedsOnTheNextAttempt(@TempDir Path tempDir) throws Exception {
        server = startServer((exchange, callCount) -> {
            if (callCount == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                respond(exchange, 429, "{}");
            } else {
                respond(exchange, 200, SUCCESS_BODY);
            }
        });

        ChatClient client = newClient(budgetTracker(tempDir));

        ChatCompletionResponse response = client.chat(List.of(ChatMessage.user("hi")), List.of());

        assertThat(response.firstMessage().content()).isEqualTo("hello");
    }

    @Test
    void givesUpAfterExhaustingRetriesOnRepeated429s(@TempDir Path tempDir) throws Exception {
        server = startServer((exchange, callCount) -> {
            exchange.getResponseHeaders().add("Retry-After", "0");
            respond(exchange, 429, "{}");
        });

        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                fakeProviderConfig(), "fake-key", budgetTracker(tempDir),
                2, Duration.ofMillis(1), millis -> { });

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("hi")), List.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("429");
    }

    @Test
    void refusesToCallTheProviderWhenTheBudgetIsAlreadyExhausted(@TempDir Path tempDir) throws Exception {
        AtomicInteger serverCalls = new AtomicInteger(0);
        server = startServer((exchange, callCount) -> {
            serverCalls.incrementAndGet();
            respond(exchange, 200, SUCCESS_BODY);
        });

        BudgetLimits zeroBudget = new BudgetLimits(0, 0, 0, 0);
        BudgetTracker budget = new BudgetTracker(zeroBudget, tempDir.resolve("budget.json"), () -> LocalDate.of(2026, 1, 1));
        ChatClient client = newClient(budget);

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("hi")), List.of()))
                .isInstanceOf(BudgetExceededException.class);
        assertThat(serverCalls.get()).isZero();
    }

    private ChatClient newClient(BudgetTracker budget) {
        return new OpenAiCompatibleChatClient(
                fakeProviderConfig(), "fake-key", budget,
                5, Duration.ofMillis(1), millis -> { });
    }

    private ProviderConfig fakeProviderConfig() {
        return new ProviderConfig(
                "http://localhost:" + server.getAddress().getPort(),
                "FAKE_API_KEY", "fake-model", 1000, null, true);
    }

    private BudgetTracker budgetTracker(Path tempDir) throws IOException {
        BudgetLimits generous = new BudgetLimits(1000, 1_000_000, 1000, 1_000_000);
        return new BudgetTracker(generous, tempDir.resolve("budget.json"), () -> LocalDate.of(2026, 1, 1));
    }

    @FunctionalInterface
    private interface Responder {
        void respond(com.sun.net.httpserver.HttpExchange exchange, int callCount) throws IOException;
    }

    private HttpServer startServer(Responder responder) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicInteger callCount = new AtomicInteger(0);
        httpServer.createContext("/chat/completions", exchange -> {
            try {
                responder.respond(exchange, callCount.incrementAndGet());
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
        return httpServer;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
