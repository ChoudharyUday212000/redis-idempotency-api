package io.uday.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.uday.idempotency.model.PaymentRequest;
import io.uday.idempotency.model.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ObjectMapper mapper;
    private IdempotencyService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        mapper = new ObjectMapper().findAndRegisterModules();
        when(redis.opsForValue()).thenReturn(values);
        service = new IdempotencyService(redis, mapper);
    }

    @Test
    void reservesKeyAndStoresCreatedResponse() {
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(true);

        PaymentResponse response = service.createPayment(
                "checkout-123", new PaymentRequest("customer-1", 2500, "INR"));

        assertNotNull(response.paymentId());
        assertEquals("checkout-123", response.idempotencyKey());
        assertEquals(2500, response.amountMinorUnits());
        verify(values).set(startsWith("payment-idempotency:"), startsWith("DONE:"),
                eq(IdempotencyService.KEY_TTL));
    }

    @Test
    void returnsTheOriginalResponseForAnIdenticalRetry() throws Exception {
        PaymentRequest request = new PaymentRequest("customer-1", 2500, "INR");
        PaymentResponse cached = new PaymentResponse(
                UUID.fromString("95f5f1ce-e8c9-4dd2-a52c-9dbd4b4a2601"),
                "checkout-123", "customer-1", 2500, "INR",
                Instant.parse("2026-01-01T10:00:00Z"));
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(false);
        when(values.get(startsWith("payment-idempotency:")))
                .thenReturn("DONE:" + fingerprintFor(request) + ":" + mapper.writeValueAsString(cached));

        PaymentResponse response = service.createPayment("checkout-123", request);

        assertEquals(cached, response);
        verify(values, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void rejectsReusingAKeyForADifferentPayload() {
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(false);
        when(values.get(startsWith("payment-idempotency:")))
                .thenReturn("DONE:incorrect-payload-fingerprint:{\"paymentId\":\"ignored\"}");

        assertThrows(IdempotencyKeyConflictException.class,
                () -> service.createPayment(
                        "checkout-123", new PaymentRequest("customer-2", 2500, "INR")));
    }

    @Test
    void reportsConcurrentSameKeyRequestsAsInProgress() {
        PaymentRequest request = new PaymentRequest("customer-1", 2500, "INR");
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(false);
        when(values.get(startsWith("payment-idempotency:")))
                .thenReturn("PROCESSING:" + fingerprintFor(request));

        assertThrows(RequestInProgressException.class,
                () -> service.createPayment("checkout-123", request));
    }

    private String fingerprintFor(PaymentRequest request) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
