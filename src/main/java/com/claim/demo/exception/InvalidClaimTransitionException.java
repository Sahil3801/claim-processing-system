package com.claim.demo.exception;

import com.claim.demo.domain.ClaimStatus;

public class InvalidClaimTransitionException extends RuntimeException {

    public InvalidClaimTransitionException(Long claimId, ClaimStatus previousStatus, ClaimStatus newStatus) {
        super("Claim " + claimId + " cannot transition from " + previousStatus + " to " + newStatus);
    }
}
