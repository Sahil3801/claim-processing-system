package com.claim.demo.exception;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(Long claimId) {
        super("Claim not found with id: " + claimId);
    }
}
