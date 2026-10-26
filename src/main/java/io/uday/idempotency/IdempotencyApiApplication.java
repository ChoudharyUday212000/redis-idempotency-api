package io.uday.idempotency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IdempotencyApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdempotencyApiApplication.class, args);
    }
}
