package com.claim.demo.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency key cannot be reused for a different request: " + idempotencyKey);
    }
}
