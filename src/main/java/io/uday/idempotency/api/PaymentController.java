package io.uday.idempotency.api;

import io.uday.idempotency.model.PaymentRequest;
import io.uday.idempotency.model.PaymentResponse;
import io.uday.idempotency.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final IdempotencyService idempotencyService;

    public PaymentController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response = idempotencyService.createPayment(idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + response.paymentId()))
                .body(response);
    }
}
