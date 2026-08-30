package com.claim.demo.controller;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.dto.ClaimCreateRequest;
import com.claim.demo.dto.ClaimDTO;
import com.claim.demo.service.ClaimService;
import com.claim.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClaimsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaimService claimService;

    @MockBean
    private JwtService jwtService;

    @Test
    void createsDraftClaimFromRequestDto() throws Exception {
        when(claimService.createClaimForClaimant(
                any(ClaimCreateRequest.class), eq("claimant"), eq("create-11")))
                .thenReturn(claim(11L, ClaimStatus.DRAFT));

        mockMvc.perform(post("/api/claims")
                        .principal(() -> "claimant")
                        .header("Idempotency-Key", "create-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":7,"claimAmount":1250.75,"claimType":"MEDICAL",
                                 "description":"Emergency room treatment","emailId":"user@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimId").value(11))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.claimStatus").value("DRAFT"))
                .andExpect(jsonPath("$.description").value("Emergency room treatment"))
                .andExpect(jsonPath("$.user").doesNotExist());

        ArgumentCaptor<ClaimCreateRequest> request = ArgumentCaptor.forClass(ClaimCreateRequest.class);
        verify(claimService).createClaimForClaimant(
                request.capture(), eq("claimant"), eq("create-11"));
        assertEquals(new BigDecimal("1250.75"), request.getValue().claimAmount());
        assertEquals("MEDICAL", request.getValue().claimType());
        assertEquals("Emergency room treatment", request.getValue().description());
    }

    @Test
    void mapsEveryActionEndpointToItsControlledStatus() throws Exception {
        Principal principal = () -> "officer@example.com";
        when(claimService.submitClaimForClaimant(11L, "officer@example.com", "submit-11"))
                .thenReturn(claim(11L, ClaimStatus.SUBMITTED));
        when(claimService.transitionClaimStatus(eq(11L), any(ClaimStatus.class),
                eq("officer@example.com"), any()))
                .thenAnswer(invocation -> claim(11L, invocation.getArgument(1)));

        mockMvc.perform(post("/api/claims/11/submit")
                        .principal(principal)
                        .header("Idempotency-Key", "submit-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("SUBMITTED"));
        mockMvc.perform(post("/api/claims/11/review").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Assigned\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("UNDER_REVIEW"));
        mockMvc.perform(post("/api/claims/11/approve").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("APPROVED"));
        mockMvc.perform(post("/api/claims/11/reject").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing documents\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("REJECTED"));
        mockMvc.perform(post("/api/claims/11/settle").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Paid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStatus").value("SETTLED"));

        verify(claimService).submitClaimForClaimant(11L, "officer@example.com", "submit-11");
        verify(claimService).transitionClaimStatus(11L, ClaimStatus.UNDER_REVIEW, "officer@example.com", "Assigned");
        verify(claimService).transitionClaimStatus(11L, ClaimStatus.APPROVED, "officer@example.com", "Verified");
        verify(claimService).transitionClaimStatus(11L, ClaimStatus.REJECTED, "officer@example.com", "Missing documents");
        verify(claimService).transitionClaimStatus(11L, ClaimStatus.SETTLED, "officer@example.com", "Paid");
    }

    @Test
    void returnsSingleClaimAndPrincipalScopedClaims() throws Exception {
        ClaimDTO claim = claim(11L, ClaimStatus.DRAFT);
        when(claimService.getClaimForActor(11L, "officer", true)).thenReturn(claim);
        when(claimService.findClaimsByUsername(eq("claimant"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(claim), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/claims/11").principal(officerAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(11));
        mockMvc.perform(get("/api/claims/my").principal(() -> "claimant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].claimId").value(11))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void appliesOfficerFiltersAndPagination() throws Exception {
        ClaimDTO claim = claim(11L, ClaimStatus.SUBMITTED);
        when(claimService.findClaims(eq(ClaimStatus.SUBMITTED), eq("medical"), eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(claim, claim), PageRequest.of(1, 5), 7));

        mockMvc.perform(get("/api/claims")
                        .param("status", "SUBMITTED")
                        .param("claimType", "medical")
                        .param("userId", "7")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.content[0].claimStatus").value("SUBMITTED"));
    }

    private ClaimDTO claim(Long id, ClaimStatus status) {
        return new ClaimDTO(id, 7L, "user@example.com", new Date(), new BigDecimal("1250.75"),
                "MEDICAL", "Emergency room treatment", status, new Date());
    }

    private UsernamePasswordAuthenticationToken officerAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "officer", null, List.of(new SimpleGrantedAuthority("ROLE_CLAIMS_OFFICER")));
    }
}
