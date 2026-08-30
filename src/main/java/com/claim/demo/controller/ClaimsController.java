package com.claim.demo.controller;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.dto.ClaimActionRequest;
import com.claim.demo.dto.ClaimCreateRequest;
import com.claim.demo.dto.ClaimDTO;
import com.claim.demo.dto.PageResponse;
import com.claim.demo.dto.RejectClaimRequest;
import com.claim.demo.service.ClaimService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;

@RestController
@RequestMapping("/api/claims")
@Validated
public class ClaimsController {

    private final ClaimService claimService;

    public ClaimsController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ClaimDTO> createClaim(@Valid @RequestBody ClaimCreateRequest request,
                                                @RequestHeader("Idempotency-Key")
                                                @NotBlank(message = "Idempotency-Key must not be blank")
                                                @Size(max = 128, message = "Idempotency-Key must be at most 128 characters")
                                                String idempotencyKey,
                                                Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(claimService.createClaimForClaimant(
                        request, principal.getName(), idempotencyKey));
    }

    @PostMapping("/{claimId}/submit")
    public ClaimDTO submitClaim(
            @PathVariable Long claimId,
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key must not be blank")
            @Size(max = 128, message = "Idempotency-Key must be at most 128 characters")
            String idempotencyKey,
            Principal principal) {
        return claimService.submitClaimForClaimant(
                claimId, principal.getName(), idempotencyKey);
    }

    @GetMapping("/{claimId}")
    public ClaimDTO getClaim(@PathVariable Long claimId, Authentication authentication) {
        return claimService.getClaimForActor(
                claimId, authentication.getName(), canViewAnyClaim(authentication));
    }

    @GetMapping("/my")
    public PageResponse<ClaimDTO> getMyClaims(
            Principal principal,
            @PageableDefault(size = 20, sort = "claimDate") Pageable pageable) {
        return PageResponse.from(claimService.findClaimsByUsername(principal.getName(), pageable));
    }

    @GetMapping
    public PageResponse<ClaimDTO> getClaims(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String claimType,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "claimDate") Pageable pageable) {
        return PageResponse.from(claimService.findClaims(status, claimType, userId, pageable));
    }

    @PostMapping("/{claimId}/review")
    public ClaimDTO reviewClaim(@PathVariable Long claimId, Principal principal,
                                @Valid @RequestBody(required = false) ClaimActionRequest request) {
        return transition(claimId, ClaimStatus.UNDER_REVIEW, principal, reason(request));
    }

    @PostMapping("/{claimId}/approve")
    public ClaimDTO approveClaim(@PathVariable Long claimId, Principal principal,
                                 @Valid @RequestBody(required = false) ClaimActionRequest request) {
        return transition(claimId, ClaimStatus.APPROVED, principal, reason(request));
    }

    @PostMapping("/{claimId}/reject")
    public ClaimDTO rejectClaim(@PathVariable Long claimId, Principal principal,
                                @Valid @RequestBody RejectClaimRequest request) {
        return transition(claimId, ClaimStatus.REJECTED, principal, request.reason());
    }

    @PostMapping("/{claimId}/settle")
    public ClaimDTO settleClaim(@PathVariable Long claimId, Principal principal,
                                @Valid @RequestBody(required = false) ClaimActionRequest request) {
        return transition(claimId, ClaimStatus.SETTLED, principal, reason(request));
    }

    private ClaimDTO transition(Long claimId, ClaimStatus status, Principal principal, String reason) {
        return claimService.transitionClaimStatus(claimId, status, principal.getName(), reason);
    }

    private String reason(ClaimActionRequest request) {
        return request == null ? null : request.reason();
    }

    private boolean canViewAnyClaim(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CLAIMS_OFFICER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
