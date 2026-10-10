package io.migrationagent.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @Test
    void redactsAGroqStyleKey() {
        String result = Redactor.redact("failed with key gsk_abcdefghijklmnopqrstuvwx1234");

        assertThat(result).doesNotContain("gsk_");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    void redactsAnOpenRouterStyleKey() {
        String result = Redactor.redact("Authorization header used sk-or-abcdefghijklmnopqrstuvwx");

        assertThat(result).doesNotContain("sk-or-abcdefghijklmnopqrstuvwx");
    }

    @Test
    void redactsABearerToken() {
        String result = Redactor.redact("Header: Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345");

        assertThat(result).doesNotContain("abcdefghijklmnopqrstuvwxyz012345");
    }

    @Test
    void leavesOrdinaryTextUntouched() {
        String result = Redactor.redact("HTTP 500 Internal Server Error");

        assertThat(result).isEqualTo("HTTP 500 Internal Server Error");
    }

    @Test
    void handlesNullGracefully() {
        assertThat(Redactor.redact(null)).isNull();
    }
}
