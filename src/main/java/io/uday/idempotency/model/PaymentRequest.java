package io.uday.idempotency.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
        @NotBlank @Size(max = 100) String customerReference,
        @Positive long amountMinorUnits,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
