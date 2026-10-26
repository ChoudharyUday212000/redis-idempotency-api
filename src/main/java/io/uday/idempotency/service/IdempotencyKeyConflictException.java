package io.uday.idempotency.service;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException() {
        super("The idempotency key was already used with a different request payload");
    }
}
