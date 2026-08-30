package com.claim.demo.integration;

import com.claim.demo.repository.ClaimRepository;
import com.claim.demo.service.ClaimCacheService;
import com.claim.demo.service.ClaimStatusEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClaimsPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("claims_test")
            .withUsername("claims_test")
            .withPassword("claims_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.test.database.replace", () -> "none");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClaimCacheService claimCacheService;

    @MockBean
    private ClaimStatusEventPublisher eventPublisher;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM processed_kafka_events");
        jdbcTemplate.update("DELETE FROM claim_status_history");
        jdbcTemplate.update("DELETE FROM claims");
        jdbcTemplate.update("DELETE FROM users");
        insertUser(201, "alice", "alice@example.com", "CLAIMANT");
        insertUser(202, "bob", "bob@example.com", "CLAIMANT");
        insertUser(203, "officer", "officer@example.com", "CLAIMS_OFFICER");
        insertUser(204, "admin", "admin@example.com", "ADMIN");
        when(claimCacheService.getClaim(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void runsFlywayAgainstRealPostgresAndCreatesReportingIndex() throws Exception {
        String product;
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        }
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'idx_claims_claim_date'
                """, Integer.class);

        assertThat(product).isEqualTo("PostgreSQL");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void enforcesPositiveAmountAndValidStatusConstraints() {
        assertThatThrownBy(() -> insertClaim(
                2101, "2026-01-01T08:00:00", "0.00", "MEDICAL", "DRAFT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertClaim(
                2102, "2026-01-01T08:00:00", "10.00", "MEDICAL", "UNKNOWN", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesCreationSubmissionAndConsumedEventIdempotencyConstraints() {
        insertClaim(2201, "2026-01-01T08:00:00", "10.00", "MEDICAL", "DRAFT",
                "create-key", null);
        assertThatThrownBy(() -> insertClaim(
                2202, "2026-01-01T09:00:00", "20.00", "AUTO", "DRAFT", "create-key", null))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertClaim(2203, "2026-01-01T10:00:00", "30.00", "HOME", "SUBMITTED",
                null, "submit-key");
        assertThatThrownBy(() -> insertClaim(
                2204, "2026-01-01T11:00:00", "40.00", "HOME", "SUBMITTED", null, "submit-key"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("""
                INSERT INTO processed_kafka_events (event_id, claim_id, processed_at)
                VALUES ('event-key', 2201, CURRENT_TIMESTAMP)
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO processed_kafka_events (event_id, claim_id, processed_at)
                VALUES ('event-key', 2201, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void executesReportingAggregationsWithPostgresNumericAndDateSemantics() {
        insertClaim(2301, "2026-02-01T08:00:00", "100.00", "MEDICAL", "SUBMITTED", null, null);
        insertClaim(2302, "2026-02-01T12:00:00", "300.00", "MEDICAL", "APPROVED", null, null);
        insertClaim(2303, "2026-02-02T09:00:00", "200.00", "AUTO", "REJECTED", null, null);

        var overall = claimRepository.summarizeOverall();
        var types = claimRepository.summarizeByClaimType();
        var daily = claimRepository.summarizeDaily(
                LocalDateTime.parse("2026-02-01T00:00:00"),
                LocalDateTime.parse("2026-02-03T00:00:00"));

        assertThat(overall.getTotalClaims()).isEqualTo(3);
        assertThat(overall.getTotalAmount()).isEqualByComparingTo("600.00");
        assertThat(overall.getAverageAmount()).isEqualByComparingTo("200.00");
        assertThat(types).filteredOn(type -> type.getClaimType().equals("MEDICAL"))
                .singleElement()
                .satisfies(type -> {
                    assertThat(type.getTotalClaims()).isEqualTo(2);
                    assertThat(type.getAverageAmount()).isEqualByComparingTo("200.00");
                });
        assertThat(daily).hasSize(2);
        assertThat(daily.get(0).getReportDate().toLocalDate().toString()).isEqualTo("2026-02-01");
        assertThat(daily.get(0).getTotalAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    void exercisesSecuredIdempotentClaimLifecycleAndLiveReporting() throws Exception {
        String request = """
                {"userId":201,"claimAmount":125.50,"claimType":"MEDICAL",
                 "description":"PostgreSQL integration claim","emailId":"alice@example.com"}
                """;

        String firstBody = mockMvc.perform(post("/api/claims")
                        .with(user("alice").roles("CLAIMANT"))
                        .header("Idempotency-Key", "postgres-create-1")
                        .contentType(APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long claimId = objectMapper.readTree(firstBody).get("claimId").asLong();

        String retryBody = mockMvc.perform(post("/api/claims")
                        .with(user("alice").roles("CLAIMANT"))
                        .header("Idempotency-Key", "postgres-create-1")
                        .contentType(APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode retry = objectMapper.readTree(retryBody);
        assertThat(retry.get("claimId").asLong()).isEqualTo(claimId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM claims", Long.class)).isEqualTo(1);

        mockMvc.perform(post("/api/claims/{id}/submit", claimId)
                        .with(user("alice").roles("CLAIMANT"))
                        .header("Idempotency-Key", "postgres-submit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("SUBMITTED"));
        mockMvc.perform(post("/api/claims/{id}/submit", claimId)
                        .with(user("alice").roles("CLAIMANT"))
                        .header("Idempotency-Key", "postgres-submit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("SUBMITTED"));

        mockMvc.perform(get("/api/claims/{id}", claimId)
                        .with(user("bob").roles("CLAIMANT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_CLAIM_ACCESS"));

        mockMvc.perform(post("/api/claims/{id}/review", claimId)
                        .with(user("officer").roles("CLAIMS_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("UNDER_REVIEW"));
        mockMvc.perform(post("/api/claims/{id}/approve", claimId)
                        .with(user("officer").roles("CLAIMS_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("APPROVED"));

        mockMvc.perform(get("/api/reports/summary")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims").value(1))
                .andExpect(jsonPath("$.approved.totalClaims").value(1))
                .andExpect(jsonPath("$.approved.totalClaimAmount").value(125.50));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM claim_status_history WHERE claim_id = ?", Long.class, claimId))
                .isEqualTo(3);
        verify(eventPublisher, times(3)).publishAfterCommit(org.mockito.ArgumentMatchers.any());
    }

    private void insertUser(long userId, String username, String email, String role) {
        jdbcTemplate.update("""
                INSERT INTO users
                    (user_id, username, password_hash, email, role, created_at, status)
                VALUES (?, ?, 'hash', ?, ?, CURRENT_TIMESTAMP, 'active')
                """, userId, username, email, role);
    }

    private void insertClaim(long claimId, String date, String amount, String type, String status,
                             String creationKey, String submissionKey) {
        LocalDateTime timestamp = LocalDateTime.parse(date);
        jdbcTemplate.update("""
                INSERT INTO claims
                    (claim_id, user_id, claim_date, claim_amount, claim_type, description,
                     claim_status, last_updated, idempotency_key, submission_idempotency_key)
                VALUES (?, 201, ?, ?, ?, 'PostgreSQL fixture', ?, ?, ?, ?)
                """, claimId, timestamp, new BigDecimal(amount), type, status, timestamp,
                creationKey, submissionKey);
    }
}
