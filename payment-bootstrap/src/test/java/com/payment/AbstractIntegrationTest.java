package com.payment;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.RedisContainer;

/**
 * Base class for integration tests that need the full backing infrastructure.
 *
 * <p>Spins up real Postgres, Kafka and Redis in disposable Docker containers — the
 * same engines used in production, not in-memory substitutes. An in-memory database
 * (e.g. H2) would diverge from Postgres exactly where this project is most sensitive:
 * identity generation, SQL dialect, and the keyset-pagination query. Testing against
 * the real engine keeps "passes in CI, fails in prod" from happening by construction.
 *
 * <p>{@code @ServiceConnection} wires each container into Spring Boot automatically:
 * datasource URL/credentials, {@code spring.kafka.bootstrap-servers} and the Redis
 * host/port are all derived from the running containers, so no manual property
 * plumbing is needed.
 *
 * <p>The containers are {@code static}, so they start once and are shared by every
 * test class that extends this base, instead of once per class.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Container
    @ServiceConnection
    static RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:8.8.0"));
}
