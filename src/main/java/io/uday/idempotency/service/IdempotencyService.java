package io.uday.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uday.idempotency.model.PaymentRequest;
import io.uday.idempotency.model.PaymentResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class IdempotencyService {

    static final Duration KEY_TTL = Duration.ofMinutes(10);
    private static final String REDIS_KEY_PREFIX = "payment-idempotency:";
    private static final String PROCESSING_PREFIX = "PROCESSING:";
    private static final String DONE_PREFIX = "DONE:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public PaymentResponse createPayment(String idempotencyKey, PaymentRequest request) {
        validateKey(idempotencyKey);
        String redisKey = REDIS_KEY_PREFIX + sha256(idempotencyKey);
        String payloadFingerprint = fingerprint(request);
        String processingValue = PROCESSING_PREFIX + payloadFingerprint;

        Boolean reserved = redis.opsForValue().setIfAbsent(redisKey, processingValue, KEY_TTL);
        if (!Boolean.TRUE.equals(reserved)) {
            return readExisting(redisKey, payloadFingerprint);
        }

        // Demo behavior: create a response only; no gateway call or movement of funds occurs.
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(),
                idempotencyKey,
                request.customerReference(),
                request.amountMinorUnits(),
                request.currency(),
                Instant.now());

        try {
            String serializedResponse = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(
                    redisKey,
                    DONE_PREFIX + payloadFingerprint + ":" + serializedResponse,
                    KEY_TTL);
            return response;
        } catch (JsonProcessingException exception) {
            // The short-lived PROCESSING marker expires automatically if serialization fails.
            throw new IllegalStateException("Could not store the idempotent response", exception);
        }
    }

    private PaymentResponse readExisting(String redisKey, String requestedFingerprint) {
        String existing = redis.opsForValue().get(redisKey);
        if (existing == null) {
            // The reservation may have expired between SET NX and this read.
            throw new RequestInProgressException();
        }

        if (existing.startsWith(DONE_PREFIX)) {
            int fingerprintStart = DONE_PREFIX.length();
            int separator = existing.indexOf(':', fingerprintStart);
            if (separator < 0) {
                throw new IllegalStateException("Stored idempotency response is malformed");
            }

            String storedFingerprint = existing.substring(fingerprintStart, separator);
            if (!storedFingerprint.equals(requestedFingerprint)) {
                throw new IdempotencyKeyConflictException();
            }

            try {
                return objectMapper.readValue(
                        existing.substring(separator + 1), PaymentResponse.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored idempotency response cannot be read", exception);
            }
        }

        if (existing.startsWith(PROCESSING_PREFIX)) {
            String storedFingerprint = existing.substring(PROCESSING_PREFIX.length());
            if (!storedFingerprint.equals(requestedFingerprint)) {
                throw new IdempotencyKeyConflictException();
            }
        }
        throw new RequestInProgressException();
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 200 characters");
        }
    }

    private String fingerprint(PaymentRequest request) {
        try {
            return sha256(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not fingerprint the request payload", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
