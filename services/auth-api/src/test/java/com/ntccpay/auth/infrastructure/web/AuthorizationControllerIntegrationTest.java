package com.ntccpay.auth.infrastructure.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the Phase 1 security posture from docs/security.md:
 * AuthN on the endpoint, idempotent HTTP semantics, and no full PAN in logs.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "ntccpay.security.api-keys.acme-corp=test-key-123")
@AutoConfigureMockMvc
class AuthorizationControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String VALID_BODY = """
            {"pan":"4242424242424242","amountMinor":1000,"currency":"USD","merchant":"acme-corp"}
            """;

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void captureLogs() {
        serviceLogger = (Logger) LoggerFactory.getLogger(
                com.ntccpay.auth.application.usecase.AuthorizationRequestService.class);
        serviceLogger.setLevel(Level.INFO);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogs() {
        serviceLogger.detachAppender(logAppender);
    }

    @Test
    void aRequestWithoutAnApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/authorizations")
                        .header("Idempotency-Key", "k-1")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRequestWithAnUnknownApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "wrong-key")
                        .header("Idempotency-Key", "k-1")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublicButEverythingElseIsProtected() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(post("/v1/authorizations")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAuthenticatedRequestIsCreatedAndApproved() throws Exception {
        mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "test-key-123")
                        .header("Idempotency-Key", "k-1")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.maskedPan").value("****4242"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.authorizationId").isNotEmpty());
    }

    @Test
    void aRetriedRequestReturnsTheSameDecisionWith200() throws Exception {
        var first = mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "test-key-123")
                        .header("Idempotency-Key", "k-replay")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var originalId = com.jayway.jsonpath.JsonPath.read(first, "$.authorizationId");

        mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "test-key-123")
                        .header("Idempotency-Key", "k-replay")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationId").value(originalId))
                .andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    void theSameKeyWithADifferentBodyConflicts() throws Exception {
        mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "test-key-123")
                        .header("Idempotency-Key", "k-conflict")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "test-key-123")
                        .header("Idempotency-Key", "k-conflict")
                        .contentType("application/json")
                        .content("""
                                {"pan":"4242424242424242","amountMinor":2000,"currency":"USD","merchant":"acme-corp"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency key conflict"));
    }

    @Test
    void anInvalidBodyIsA400Problem() throws Exception {
        mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "test-key-123")
                        .header("Idempotency-Key", "k-invalid")
                        .contentType("application/json")
                        .content("""
                                {"pan":"nope","amountMinor":-5,"currency":"US","merchant":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void noFullPanEverAppearsInLogOutput() throws Exception {
        mockMvc.perform(post("/v1/authorizations")
                        .header("X-API-Key", "test-key-123")
                        .header("Idempotency-Key", "k-log")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        var logged = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(logged).isNotBlank();
        assertThat(logged).doesNotContain("4242424242424242");
        assertThat(logged).contains("****4242");
    }
}
