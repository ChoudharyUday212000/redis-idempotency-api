# Redis Idempotency API

A Spring Boot 3 / Java 17 API demonstrating how a backend can handle retries with Redis-backed idempotency keys. It is designed around payment-request semantics, but it does **not** call a payment gateway or move real money.

## Behavior

- The first request reserves an idempotency key in Redis using SET-if-absent and a TTL.
- The API stores the created response with a fingerprint of the request payload.
- An identical retry receives the same payment response and ID.
- Reusing the key with a different payload returns HTTP 422.
- A duplicate request that arrives while the first is being processed returns HTTP 409.
- Invalid request bodies return HTTP 400.
- Redis keys expire after 10 minutes in this example.

## Stack

Java 17, Spring Boot, Spring Web, Bean Validation, Spring Data Redis, Jackson, Maven, Docker Compose and JUnit/Mockito.

## Run locally

Requirements: Docker with Compose and JDK 17+.

~~~bash
docker compose up --build
~~~

The API listens on port 8080 and Redis on port 6379. Health endpoint: GET http://localhost:8080/actuator/health.

To run only tests:

~~~bash
mvn test
~~~

## Example request

~~~bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: checkout-2026-0001' \
  -d '{"customerReference":"order-123","amountMinorUnits":19900,"currency":"INR"}'
~~~

Send the same request again with the same key and payload. The response should reuse the original payment ID. Try changing the amount while keeping the key; the API returns 422.

## Design notes

- Raw idempotency keys are hashed before becoming Redis key names.
- Request payload fingerprints prevent the same key from silently representing two different requests.
- The reservation TTL bounds stale PROCESSING markers if the process exits unexpectedly.
- Tests mock Redis so the default CI build does not require a running Redis service.

## Important production gaps

The example generates a response in memory; it does not persist a payment or contact a gateway. For production, persist the payment and idempotency record in a durable store, use an outbox or equivalent for downstream events, define recovery for abandoned PROCESSING markers, and make persistence side effects atomic. Add authentication, rate limits, key ownership/tenant scoping, metrics and operational alerts before exposing it to clients.
