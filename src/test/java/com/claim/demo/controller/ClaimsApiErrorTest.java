package com.claim.demo.controller;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.dto.ClaimCreateRequest;
import com.claim.demo.exception.ClaimNotFoundException;
import com.claim.demo.exception.DuplicateClaimException;
import com.claim.demo.exception.InvalidClaimTransitionException;
import com.claim.demo.exception.IdempotencyKeyConflictException;
import com.claim.demo.exception.UnauthorizedClaimAccessException;
import com.claim.demo.exception.UserNotFoundException;
import com.claim.demo.service.ClaimService;
import com.claim.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClaimsApiErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaimService claimService;

    @MockBean
    private JwtService jwtService;

    @Test
    void rejectsInvalidClaimCreationBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .header("Idempotency-Key", "invalid-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":0,\"claimAmount\":0,\"claimType\":\" \",\"description\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/claims"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.violations.userId").value("userId must be positive"))
                .andExpect(jsonPath("$.violations.claimAmount").value("claimAmount must be greater than zero"))
                .andExpect(jsonPath("$.violations.claimType").value("claimType is required"))
                .andExpect(jsonPath("$.violations.description").value("description is required"));

        verify(claimService, never()).createClaimForClaimant(
                any(ClaimCreateRequest.class), any(), any());
    }

    @Test
    void requiresReasonWhenRejecting() throws Exception {
        mockMvc.perform(post("/api/claims/12/reject")
                        .principal(() -> "officer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.reason")
                        .value("reason is required when rejecting a claim"));

        verify(claimService, never()).transitionClaimStatus(
                any(), any(), any(), any());
    }

    @Test
    void returnsNotFoundForMissingClaim() throws Exception {
        when(claimService.getClaimForActor(99L, "officer", true)).thenThrow(new ClaimNotFoundException(99L));

        mockMvc.perform(get("/api/claims/99").principal(officerAuthentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CLAIM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Claim not found with id: 99"));
    }

    @Test
    void returnsConflictForInvalidTransition() throws Exception {
        Principal principal = () -> "officer";
        when(claimService.submitClaimForClaimant(12L, "officer", "submit-12"))
                .thenThrow(new InvalidClaimTransitionException(12L, ClaimStatus.APPROVED, ClaimStatus.SUBMITTED));

        mockMvc.perform(post("/api/claims/12/submit")
                        .principal(principal)
                        .header("Idempotency-Key", "submit-12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_CLAIM_TRANSITION"))
                .andExpect(jsonPath("$.path").value("/api/claims/12/submit"));
    }

    @Test
    void returnsConflictForDuplicateClaim() throws Exception {
        when(claimService.createClaimForClaimant(
                any(ClaimCreateRequest.class), eq("claimant"), eq("duplicate-create")))
                .thenThrow(new DuplicateClaimException("Claim request was already processed"));

        mockMvc.perform(post("/api/claims")
                        .principal(() -> "claimant")
                        .header("Idempotency-Key", "duplicate-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_CLAIM"))
                .andExpect(jsonPath("$.message").value("Claim request was already processed"));
    }

    @Test
    void returnsForbiddenForUnauthorizedClaimAccess() throws Exception {
        when(claimService.getClaimForActor(12L, "claimant", false))
                .thenThrow(new UnauthorizedClaimAccessException(12L, "claimant"));

        mockMvc.perform(get("/api/claims/12").principal(claimantAuthentication()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_CLAIM_ACCESS"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void returnsNotFoundWhenCreateReferencesUnknownUser() throws Exception {
        when(claimService.createClaimForClaimant(
                any(ClaimCreateRequest.class), eq("claimant"), eq("unknown-user-create")))
                .thenThrow(new UserNotFoundException(404L));

        mockMvc.perform(post("/api/claims")
                        .principal(() -> "claimant")
                        .header("Idempotency-Key", "unknown-user-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with id: 404"));
    }

    @Test
    void requiresIdempotencyKeyForCreation() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .principal(() -> "claimant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_IDEMPOTENCY_KEY"))
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));

        verify(claimService, never()).createClaimForClaimant(any(), any(), any());
    }

    @Test
    void returnsConflictForInvalidIdempotencyKeyReuse() throws Exception {
        when(claimService.createClaimForClaimant(
                any(ClaimCreateRequest.class), eq("claimant"), eq("already-used")))
                .thenThrow(new IdempotencyKeyConflictException("already-used"));

        mockMvc.perform(post("/api/claims")
                        .principal(() -> "claimant")
                        .header("Idempotency-Key", "already-used")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSE"))
                .andExpect(jsonPath("$.status").value(409));
    }

    private String validClaimRequest() {
        return """
                {"userId":7,"claimAmount":100.00,"claimType":"MEDICAL",
                 "description":"Emergency treatment","emailId":"user@example.com"}
                """;
    }

    private UsernamePasswordAuthenticationToken officerAuthentication() {
        return authentication("officer", "ROLE_CLAIMS_OFFICER");
    }

    private UsernamePasswordAuthenticationToken claimantAuthentication() {
        return authentication("claimant", "ROLE_CLAIMANT");
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String authority) {
        return new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(authority)));
    }
}
