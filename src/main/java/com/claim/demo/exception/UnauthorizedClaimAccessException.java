package com.claim.demo.exception;

public class UnauthorizedClaimAccessException extends RuntimeException {

    public UnauthorizedClaimAccessException(Long claimId, String username) {
        super("User " + username + " is not authorized to access claim " + claimId);
    }

    public UnauthorizedClaimAccessException(String message) {
        super(message);
    }
}
