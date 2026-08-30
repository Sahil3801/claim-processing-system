package com.claim.demo.exception;

public class InvalidClaimStatusException extends RuntimeException {

    public InvalidClaimStatusException(String status) {
        super("Unknown claim status: " + status);
    }
}
