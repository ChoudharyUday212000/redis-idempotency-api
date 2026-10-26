package io.uday.idempotency.model;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String idempotencyKey,
        String customerReference,
        long amountMinorUnits,
        String currency,
        Instant createdAt) {
}
