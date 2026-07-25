package com.payment;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the full Spring context boots with every module wired together,
 * backed by real Postgres, Kafka and Redis provided by {@link AbstractIntegrationTest}.
 */
class PaymentApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
