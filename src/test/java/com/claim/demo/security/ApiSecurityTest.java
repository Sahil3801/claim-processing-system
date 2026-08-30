package com.claim.demo.security;

import com.claim.demo.config.SecurityConfig;
import com.claim.demo.controller.AuthController;
import com.claim.demo.controller.ClaimsController;
import com.claim.demo.controller.ReportsController;
import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.dto.ClaimDTO;
import com.claim.demo.dto.ClaimsSummaryDTO;
import com.claim.demo.dto.RegisterUserRequest;
import com.claim.demo.dto.UserDTO;
import com.claim.demo.filter.JwtAuthenticationFilter;
import com.claim.demo.service.ClaimService;
import com.claim.demo.service.ReportService;
import com.claim.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, ClaimsController.class, ReportsController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityErrorHandler.class})
class ApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaimService claimService;

    @MockBean
    private ReportService reportService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void authenticationEndpointsRemainPublic() throws Exception {
        when(userService.registerUser(any(RegisterUserRequest.class)))
                .thenReturn(new UserDTO(1L, "claimant", "claimant@example.com", "CLAIMANT", "active"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"claimant\",\"password\":\"password12\",\"email\":\"claimant@example.com\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedRequestsReturnJson401() throws Exception {
        mockMvc.perform(get("/api/claims/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/api/claims/1"));
    }

    @Test
    @WithMockUser(username = "claimant", roles = "CLAIMANT")
    void claimantCanCreateButCannotReviewOrListAllClaims() throws Exception {
        when(claimService.createClaimForClaimant(any(), eq("claimant"), eq("security-create")))
                .thenReturn(claim(1L, ClaimStatus.DRAFT));

        mockMvc.perform(post("/api/claims")
                        .header("Idempotency-Key", "security-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClaimRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/claims/1/review"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "officer", roles = "CLAIMS_OFFICER")
    void officerCanReviewAndListClaimsButCannotAccessReports() throws Exception {
        when(claimService.transitionClaimStatus(1L, ClaimStatus.UNDER_REVIEW, "officer", null))
                .thenReturn(claim(1L, ClaimStatus.UNDER_REVIEW));
        when(claimService.findClaims(eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(post("/api/claims/1/review"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanAccessReportingAndOfficerActions() throws Exception {
        when(reportService.generateClaimsSummary())
                .thenReturn(new ClaimsSummaryDTO(1, new BigDecimal("25.00")));
        when(claimService.transitionClaimStatus(1L, ClaimStatus.APPROVED, "admin", null))
                .thenReturn(claim(1L, ClaimStatus.APPROVED));

        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims").value(1));
        mockMvc.perform(post("/api/claims/1/approve"))
                .andExpect(status().isOk());
    }

    private String validClaimRequest() {
        return """
                {"userId":1,"claimAmount":25.00,"claimType":"MEDICAL",
                 "description":"Treatment","emailId":"claimant@example.com"}
                """;
    }

    private ClaimDTO claim(Long id, ClaimStatus status) {
        return new ClaimDTO(id, 1L, "claimant@example.com", new Date(), new BigDecimal("25.00"),
                "MEDICAL", "Treatment", status, new Date());
    }
}
