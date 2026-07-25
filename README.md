# Payment Service

A sample payment service built with **Spring Boot 4.1** and **Java 25**, designed to demonstrate **Hexagonal Architecture (Ports & Adapters)** with **two separate and independent bounded contexts** communicating asynchronously through Kafka.

The service receives a payment request over REST, publishes it to Kafka, and a consumer processes the message and persists it in PostgreSQL. Reads are served through a Redis cache.

Each bounded context is a **separate Maven module**, so the independence between them is enforced by the build itself — not merely by convention. A dedicated **architecture-tests** module turns the hexagonal rules into automated **fitness functions** that fail the build on violation.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Maven Module Layout](#maven-module-layout)
- [The Two Bounded Contexts](#the-two-bounded-contexts)
- [Architecture Tests (Fitness Functions)](#architecture-tests-fitness-functions)
- [Hexagonal Architecture Explained](#hexagonal-architecture-explained)
- [Project Structure](#project-structure)
- [What Each Layer Means](#what-each-layer-means)
- [Request Flow](#request-flow)
- [Technology Stack](#technology-stack)
- [Running the Application](#running-the-application)
- [Observability](#observability)
- [Testing](#testing)
- [Build Notes](#build-notes)
- [API Endpoints](#api-endpoints)
- [Testing with Swagger](#testing-with-swagger)

---

## Architecture Overview

This project applies **Hexagonal Architecture** (also known as **Ports & Adapters**), an architectural style whose central idea is to isolate the **domain** (business logic) from the **infrastructure** (frameworks, databases, message brokers, HTTP).

The domain sits at the center of a hexagon. Everything outside — REST controllers, Kafka producers/consumers, JPA repositories, Redis — plugs into the domain through **ports** (interfaces defined by the domain) that are fulfilled by **adapters** (concrete implementations that know about a specific technology).

The single most important rule: **dependencies always point inward.** Adapters depend on the domain; the domain never depends on adapters. The domain does not know Kafka, PostgreSQL, or HTTP exist. This makes the business logic independent, testable in isolation, and resistant to changes in infrastructure.

On top of that hexagonal core, the project is split into **Maven modules** so that the boundary between the two bounded contexts is guaranteed at compile time: `payment-submit` and `payment-process` cannot reference each other's code, because neither declares a dependency on the other. A separate test module then asserts the internal layering rules automatically.

---

## Maven Module Layout

The project is a **multi-module Maven build**. A parent (aggregator) POM at the root declares the modules and centralizes versions and the build plugin configuration.

```
payment/                         # parent / aggregator POM (packaging: pom)
│
├── payment-submit/              # BOUNDED CONTEXT 1 — receive & forward (stateless)
├── payment-process/             # BOUNDED CONTEXT 2 — consume & persist (owns state)
├── payment-bootstrap/           # composition root — main class, config, packaging
└── payment-architecture-tests/  # fitness functions — enforce the rules at build time
```

Responsibilities and dependencies of each module:

| Module | Depends on | Contains |
|--------|-----------|----------|
| `payment-submit` | *(neither context module)* | The full `submit` hexagon: domain, application service, REST + Kafka producer adapters. |
| `payment-process` | *(neither context module)* | The full `process` hexagon: domain, application services, REST query + Kafka consumer + JPA/Redis persistence adapters. |
| `payment-bootstrap` | `payment-submit`, `payment-process` | `PaymentApplication` (the `@SpringBootApplication`), `application.yaml`, `schema.sql`, and the Spring Boot Maven plugin that produces the runnable fat jar. |
| `payment-architecture-tests` | `payment-submit`, `payment-process` *(test scope)* | ArchUnit rules that assert the hexagonal layering and the context isolation. Contains no production code. |

**Why this matters:** the two context modules have **no dependency on each other**. The compiler will refuse any accidental import from `submit` into `process` or vice versa. What convention asked for before ("never share domain classes"), the build now enforces. If the `process` side ever needs to become a standalone microservice, it can be lifted out with its module intact — there was never a compile-time link to sever.

Only `payment-bootstrap` depends on both, because its job is precisely to **compose** them into a single deployable application. It is also the only module that carries the Spring Boot plugin: a repackaged fat jar cannot be used as a library dependency, so it must live at the composition root and nowhere else.

> The parent POM keeps a single `com.payment` group, and every module keeps the `com.payment` package prefix. This is what allows the `@SpringBootApplication` in `payment-bootstrap` to component-scan classes that physically live in the other two module jars.

---

## The Two Bounded Contexts

A defining characteristic of this project is that it models **two distinct bounded contexts**, each with its own model, its own ports, its own lifecycle — and now its own module. They are deliberately kept **independent** and never share domain classes.

### 1. `submit` context — receive and forward (`payment-submit`)

- **Stateless.** It has no database access and holds no state.
- Its job is to receive a payment request, validate it, generate a `PaymentId`, publish a `PaymentRequestedEvent` to Kafka, and return `202 Accepted`.
- Its lifecycle **ends** the moment the message is handed off to Kafka. It "publishes and forgets".
- It owns the **Kafka producer** and the **topic creation** (`KafkaProducerConfig`) — creating the topic is the responsibility of the side that writes to it.

### 2. `process` context — consume and persist (`payment-process`)

- **Owns the state.** It is the only side that touches PostgreSQL.
- It consumes the Kafka message, reconstructs the payment in its own model, applies processing logic (e.g. status transitions), and persists it.
- Reads (by UUID, and paginated listing) are also served by this context, accelerated by a Redis cache.
- It owns the **Kafka consumer**, including retry/DLT handling, and all **persistence** (JPA + Redis).

### Why the contexts do not share code

Because these are genuinely separate bounded contexts, each one has its **own copy** of the value objects (`PaymentId`, `PaymentInstrument`, `PaymentMethod`, `FundingType`). This duplication is **intentional**, not an oversight.

The only bridge between the two contexts is the **Kafka message** — a JSON contract made of primitive fields (strings, numbers). Neither context imports classes from the other; with the modular split, neither *can*. The `submit` side translates its domain into the event; the `process` side translates the event into its own domain.

This buys a concrete benefit: if the `process` side ever needs to become a separate microservice/deployment, it can be extracted without touching `submit`, because there was never a compile-time dependency between them. The price paid — duplicated value objects — buys that independence, and the module boundary makes the price non-negotiable.

---

## Architecture Tests (Fitness Functions)

The module split guarantees the boundary **between** contexts at compile time. But the rules **inside** each context — the domain staying pure, adapters not reaching into one another, `@Entity` never leaking out of persistence — are conventions the compiler alone does not enforce. The `payment-architecture-tests` module closes that gap with **[ArchUnit](https://www.archunit.org/)**: plain JUnit tests that read the compiled bytecode and assert architectural rules. A violation fails the build, exactly like a broken unit test.

This is a deliberately lighter tool than more modules. Splitting further by technical layer (one module for REST, one for Kafka, one for persistence) would fight the split-by-context that already exists and reintroduce cross-context coupling through shared technical modules. A test module does not: it depends on both contexts in **test scope**, inspects them, and ships no production code.

### Rules enforced

**`HexagonalLayeringTest`** — the inward-pointing dependency rule, applied inside every context:

- the domain depends on **nothing** infrastructural — not adapters, not application services, not Spring, JPA, Kafka, Hibernate, or Jackson;
- application services do not depend on adapters;
- adapters do not reach into each other (REST must not touch Kafka or persistence directly — they talk through ports);
- `@Entity` classes live **only** in `adapter.persistence.model`, so the JPA entity never leaks into the domain;
- port contracts are interfaces (the event/command records that sit next to a port are data, and are excluded).

**`BoundedContextIsolationTest`** — the context boundary, asserted in both directions:

- `submit` must not depend on `com.payment.process..`;
- `process` must not depend on `com.payment.submit..`.

The isolation test is redundant with the module boundary *today* — the build already forbids the dependency. It is kept as a second, explicit line of defense that also **documents the intent**: if the modules were ever merged, or a shared module introduced, this test would still catch a cross-context import. It is what guarantees the duplicated value objects remain independent copies rather than a shortcut waiting to happen.

### Running them

The tests run as part of the normal build — no infrastructure required, since they only inspect bytecode:

```bash
mvn -pl payment-architecture-tests test
```

Or as part of the full reactor with `mvn test` / `mvn verify` from the root. Because a rule violation fails the build, wiring `mvn verify` into CI is enough to keep the architecture from eroding over time.

---

## Hexagonal Architecture Explained

The hexagon has two kinds of ports, distinguished by **who calls whom**:

### Driving ports (inbound) — `port.in`

Interfaces that represent **entry points into the domain**. Something on the outside (a REST controller, a Kafka consumer) calls the domain through them.

- `SubmitPaymentUseCase` — the entry point of the `submit` context
- `ProcessPaymentUseCase` — the entry point of the `process` context (for processing a message)
- `GetPaymentUseCase` — the entry point for reads

### Driven ports (outbound) — `port.out`

Interfaces that represent **things the domain needs from the outside**, named by *capability*, never by technology. The domain says "publish this event" or "save this payment" without knowing it's Kafka or PostgreSQL underneath.

- `PaymentEventPublisher` — "publish a payment event" (fulfilled by Kafka)
- `PaymentRepository` — "save / find a payment" (fulfilled by JPA + PostgreSQL)

### Adapters

Concrete classes that either **call** a driving port or **implement** a driven port:

| Adapter | Module | Type | Role |
|---------|--------|------|------|
| `PaymentController` | submit | Driving | Receives HTTP POST, calls `SubmitPaymentUseCase` |
| `PaymentQueryController` | process | Driving | Receives HTTP GET, calls `GetPaymentUseCase` |
| `PaymentKafkaConsumer` | process | Driving | Receives Kafka message, calls `ProcessPaymentUseCase` |
| `PaymentKafkaProducer` | submit | Driven | Implements `PaymentEventPublisher` (publishes to Kafka) |
| `PaymentPersistenceAdapter` | process | Driven | Implements `PaymentRepository` (PostgreSQL + Redis cache) |

Note the symmetry: **driving adapters call ports; driven adapters implement ports.** A controller and a Kafka consumer are structurally the same kind of thing — both are entry points that invoke a use case. Neither implements an interface.

---

## Project Structure

Each context module follows the same internal hexagonal layout (`domain` → `application` → `adapter`). At the **root of the project** there is a `docker/` folder containing `docker-compose` files that provision all the required infrastructure — **Redis, Kafka, and PostgreSQL** — with a single root `docker-compose.yml` that brings the whole environment up at once (see [Running the Application](#running-the-application)).

```
payment/                                        # parent / aggregator POM
│
├── payment-submit/                             # ── MODULE: BOUNDED CONTEXT 1 (stateless) ──
│   └── com.payment.submit
│       ├── domain                              #   value objects (owned by submit)
│       │   ├── PaymentId.java
│       │   ├── PaymentInstrument.java
│       │   ├── PaymentMethod.java
│       │   ├── FundingType.java
│       │   └── SubmitPaymentCommand.java       #   input carried into the use case
│       ├── application
│       │   └── SubmitPaymentService.java       #   implements SubmitPaymentUseCase
│       ├── port
│       │   ├── in
│       │   │   └── SubmitPaymentUseCase.java            # driving port (entry point)
│       │   └── out
│       │       ├── PaymentEventPublisher.java          # driven port (publish to Kafka)
│       │       └── PaymentRequestedEvent.java          # the neutral cross-context message
│       └── adapter
│           ├── rest                            #   DRIVING adapter: HTTP
│           │   ├── PaymentController.java       #   POST /api/v1/payments → SubmitPaymentUseCase
│           │   ├── dto
│           │   │   ├── PaymentRequest.java
│           │   │   ├── PaymentAcceptedResponse.java
│           │   │   └── PaymentStatus.java
│           │   └── validator
│           │       ├── ValidPaymentInstrument.java
│           │       └── PaymentInstrumentValidator.java
│           └── kafka                           #   DRIVEN adapter: Kafka producer
│               ├── PaymentKafkaProducer.java   #   implements PaymentEventPublisher
│               ├── PaymentEventPublishingException.java
│               └── config
│                   └── KafkaProducerConfig.java #   topic creation (owned by the producer side)
│
├── payment-process/                            # ── MODULE: BOUNDED CONTEXT 2 (owns state) ──
│   └── com.payment.process
│       ├── domain                              #   value objects (duplicated on purpose)
│       │   ├── Payment.java                    #   the aggregate
│       │   ├── PaymentId.java
│       │   ├── PaymentInstrument.java
│       │   ├── PaymentMethod.java
│       │   ├── FundingType.java
│       │   ├── PaymentStatus.java              #   PROCESSING / COMPLETED / FAILED
│       │   ├── PaymentPage.java                #   keyset pagination result
│       │   ├── PaymentNotFoundException.java
│       │   └── ProcessPaymentCommand.java
│       ├── application
│       │   ├── ProcessPaymentService.java      #   implements ProcessPaymentUseCase
│       │   └── GetPaymentService.java          #   implements GetPaymentUseCase
│       ├── port
│       │   ├── in
│       │   │   ├── ProcessPaymentUseCase.java          # driving port (consume)
│       │   │   └── GetPaymentUseCase.java              # driving port (read)
│       │   └── out
│       │       └── PaymentRepository.java              # driven port (persist / read)
│       └── adapter
│           ├── rest                            #   DRIVING adapter: HTTP reads
│           │   ├── PaymentQueryController.java  #   GET /api/v1/payments → GetPaymentUseCase
│           │   ├── PaymentExceptionHandler.java #   domain errors → HTTP status codes
│           │   └── dto
│           │       ├── PaymentResponse.java
│           │       └── PaymentPageResponse.java
│           ├── kafka                           #   DRIVING adapter: Kafka consumer
│           │   ├── PaymentKafkaConsumer.java    #   @RetryableTopic + @DltHandler → ProcessPaymentUseCase
│           │   └── dto
│           │       └── PaymentMessage.java      #   the JSON envelope read from the topic
│           └── persistence                     #   DRIVEN adapter: PostgreSQL + Redis
│               ├── PaymentPersistenceAdapter.java  # implements PaymentRepository (+ cache annotations)
│               ├── PaymentJpaRepository.java       # Spring Data JPA repository
│               ├── model
│               │   └── PaymentEntity.java          # JPA @Entity (never leaves this package)
│               └── config
│                   ├── DataSourceConfig.java
│                   ├── RedisCacheConfig.java       # Redis cache manager (Jackson 3 typing)
│                   └── PaymentIdMixin.java          # keeps the domain free of Jackson annotations
│
├── payment-bootstrap/                          # ── MODULE: composition root ──
│   ├── com.payment
│   │   └── PaymentApplication.java             #   @SpringBootApplication entry point
│   └── src
│       ├── main/resources
│       │   ├── application.yaml                #   all runtime configuration lives here
│       │   └── schema.sql
│       └── test
│           ├── java/com/payment
│           │   ├── AbstractIntegrationTest.java #   Testcontainers base (Postgres, Kafka, Redis)
│           │   └── PaymentApplicationTests.java #   context-load test on real infrastructure
│           └── resources
│               └── application.yaml            #   test profile (schema.sql + validate)
│
└── payment-architecture-tests/                 # ── MODULE: fitness functions (test only) ──
    └── src/test/java/com/payment/architecture
        ├── HexagonalLayeringTest.java          #   domain purity, layer direction, @Entity confinement
        └── BoundedContextIsolationTest.java    #   submit ⇎ process
```

---

## What Each Layer Means

The layering below is identical inside each context module.

### `domain` — the core of the hexagon

Pure business model and contracts. **Contains no framework or infrastructure code.** No `@Entity`, no `@RestController`, no Kafka, no SQL. If you deleted every adapter, this layer would still compile and its rules would still hold.

- **value objects & aggregate** — `PaymentId`, `PaymentInstrument`, `Payment`, etc. are immutable records. `PaymentInstrument` enforces invariants in its constructor (e.g. PayPal must not carry a funding type; cards must). Invalid objects cannot be constructed.
- **`port.in`** — driving ports: the use-case interfaces the outside world calls.
- **`port.out`** — driven ports: capability interfaces the domain needs the outside world to fulfill.
- **`Command` / `Event`** — `Command` objects carry input *into* a use case; `Event` objects (past tense, e.g. `PaymentRequestedEvent`) are the neutral message that crosses the Kafka boundary.

### `application` — use case orchestration

Implements the driving ports. This is where a use case is coordinated: generate an ID, build an event, call the publisher; or load a payment, apply a transition, call the repository.

These services are **agnostic to how they were triggered** — `SubmitPaymentService` does not know it was called via REST. It only receives a `Command`. That is why it lives here and not inside the REST adapter.

### `adapter` — the infrastructure edge

The only layer that knows about specific technologies. It translates between the outside world and the domain in both directions:

- **`rest`** — turns HTTP into domain calls, and domain results/errors into HTTP responses. DTOs and validation live here so the domain is never exposed on the wire.
- **`kafka`** — turns Kafka messages into domain calls (consumer, in `process`) and domain events into Kafka messages (producer, in `submit`).
- **`persistence`** (process only) — turns domain objects into JPA entities and back. The `PaymentEntity` is package-private and **never leaves** this package; the domain works only with the `Payment` aggregate. Redis serialization is configured here to emit clean JSON with no type markers, keeping Jackson annotations out of the domain via a mix-in.

**Dependency direction (the golden rule):** `adapter` → `application` → `domain`. Never the reverse. No class in `domain` imports anything from `adapter` — and no module imports another context's module.

---

## Request Flow

The full lifecycle of a payment across both contexts (and both context modules):

```
   ┌──────────── payment-submit (BOUNDED CONTEXT 1, stateless) ─────────────────┐
   │                                                                            │
   1. POST /api/v1/payments
      → PaymentController (REST driving adapter)
      → SubmitPaymentUseCase  (port.in)
      → SubmitPaymentService  (application)  ── generates PaymentId
      → PaymentEventPublisher (port.out)
      → PaymentKafkaProducer  (driven adapter)  ── publishes JSON
      → returns 202 Accepted + Location header
   │                                                                            │
   └────────────────────────────────┬───────────────────────────────────────── ┘
                                     │  Kafka topic  (the only bridge: JSON contract)
   ┌─────────────────────────────────▼──── payment-process (CONTEXT 2, stateful) ┐
   │                                                                            │
   2. PaymentKafkaConsumer (driving adapter, @RetryableTopic + @DltHandler)
      → reconstructs the message into the process model
      → ProcessPaymentUseCase  (port.in)
      → ProcessPaymentService  (application)
      → PaymentRepository      (port.out)
      → PaymentPersistenceAdapter (driven adapter) ── INSERT into PostgreSQL
                                                     ── @CachePut into Redis
   │                                                                            │
   3. GET /api/v1/payments/{uuid}
      → PaymentQueryController → GetPaymentUseCase → GetPaymentService
      → PaymentRepository → PaymentPersistenceAdapter
        └─ @Cacheable: first read hits PostgreSQL and populates Redis;
           subsequent reads are served from Redis
   │                                                                            │
   └────────────────────────────────────────────────────────────────────────── ┘
```

Resilience on the consumer side: `@RetryableTopic` retries transient failures with exponential backoff — creating companion `*-retry-*` topics automatically — and messages that exhaust all attempts are routed to a **Dead Letter Topic** handled by `@DltHandler`.

---

## Technology Stack

| Concern | Technology |
|---------|-----------|
| Language / Runtime | Java 25 |
| Framework | Spring Boot 4.1 |
| Build | Maven (multi-module) |
| Web | Spring MVC + Bean Validation |
| Messaging | Apache Kafka (Spring Kafka, with Retry Topic + DLT) |
| Persistence | PostgreSQL + Spring Data JPA / Hibernate |
| Cache | Redis (Spring Cache, Jackson 3 serialization) |
| API Docs | springdoc-openapi (Swagger UI) |
| JSON | Jackson 3 (`tools.jackson`) |
| Architecture testing | ArchUnit (JUnit 5) |
| Integration testing | Testcontainers (Postgres, Kafka, Redis) |
| Observability | OpenTelemetry (`spring-boot-starter-opentelemetry`) → OTLP; Micrometer with OTel semantic conventions |
| Logs / correlation | Logback → OpenTelemetry appender → Loki, correlated by `trace_id` |
| Telemetry backend | Grafana LGTM — Loki, Tempo, Prometheus, Grafana (dev only) |

---

## Running the Application

### Prerequisites — infrastructure via Docker

All required infrastructure is provided as **`docker-compose`** files inside the **`docker/` folder at the root of the project**. Each backing service lives in its own subfolder, and a **single root `docker-compose.yml`** ties them together so the whole environment comes up with one command:

```
docker/
├── docker-compose.yml                 # root file — brings up everything (Compose project: dev-infra)
├── postgres/docker-compose.yml        # PostgreSQL + pgAdmin
├── kafka/docker-compose.yml           # Kafka (KRaft, single node) + Kafka-UI
├── redis/docker-compose.yml           # Redis + RedisInsight
└── observability/docker-compose.yml   # Grafana LGTM (OpenTelemetry backend) + provisioned dashboard
```

The root file redefines nothing — it uses Compose's **`include`** directive to pull in the three per-service files:

```yaml
name: dev-infra

include:
  - postgres/docker-compose.yml
  - kafka/docker-compose.yml
  - redis/docker-compose.yml
  - observability/docker-compose.yml
```

Running everything as **one Compose project** (rather than three isolated stacks) means all services share a single network, resolve each other by service name, and are managed together — up, down, logs, and status in a single command.

**Start everything** from the `docker/` folder:

```bash
cd docker
docker compose up -d          # start Postgres, Kafka, Redis, Grafana LGTM (+ their UIs)
docker compose ps             # status / health of all services
docker compose logs -f kafka  # follow the logs of a single service
docker compose down           # stop everything (data volumes kept)
docker compose down -v        # stop everything and wipe the data volumes
```

The three services the application connects to:

| Service | Address | Notes |
|---------|---------|-------|
| PostgreSQL | `localhost:5432` | database `appdb`, user / password `app` / `app` |
| Kafka broker | `localhost:9092` | KRaft mode, single broker |
| Redis | `localhost:6379` | password `app` (`--requirepass`) |

Each stack also ships a **management UI**, useful for inspecting state while developing:

| UI | URL | Login |
|----|-----|-------|
| pgAdmin | [http://localhost:5050](http://localhost:5050) | desktop mode — no login prompt |
| Kafka-UI | [http://localhost:8082](http://localhost:8082) | — |
| RedisInsight | [http://localhost:5540](http://localhost:5540) | — |
| Grafana | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` — telemetry, see [Observability](#observability) |

> **Compose version:** the `include` directive requires **Docker Compose v2.20+** (any recent Docker Desktop / Engine). On older versions, either upgrade or combine the files explicitly: `docker compose -f postgres/docker-compose.yml -f kafka/docker-compose.yml -f redis/docker-compose.yml up -d`.

> Make sure the ports above are free before starting, and that the credentials in the compose files match the ones in `payment-bootstrap/src/main/resources/application.yaml` (datasource user/password and Redis password). You can still bring up a single stack on its own when you only need one dependency — e.g. `docker compose -f docker/redis/docker-compose.yml up -d`.

### Build all modules

From the **project root** (where the parent POM lives):

```bash
mvn clean install
```

This builds all modules in the correct order (Maven resolves it from the dependency graph), runs the architecture tests, and produces the runnable jar in `payment-bootstrap/target/`. If any architectural rule is violated, the build fails here.

### Start the application

Run it through the bootstrap module:

```bash
mvn -pl payment-bootstrap spring-boot:run
```

or, after `mvn install`, from the produced jar:

```bash
java -jar payment-bootstrap/target/payment.jar
```

The application starts on **http://localhost:8080**.

> **Note on hot reload:** the bootstrap module includes `spring-boot-devtools`. When running from an IDE with *Build Automatically* enabled, saving a source file triggers an automatic restart.

---

## Observability

The service is fully instrumented for **observability** — metrics, traces, and logs — using **OpenTelemetry**. Everything is exported over **OTLP** to a local **Grafana LGTM** stack (Loki, Tempo, Prometheus, Grafana) that runs as part of the dev infrastructure, and a custom Grafana dashboard is provisioned automatically as the home page.

### Dependencies

Added to `payment-bootstrap`:

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-actuator` | Health / info / metrics endpoints |
| `spring-boot-starter-opentelemetry` | OpenTelemetry API + auto-configuration |
| `io.micrometer:micrometer-registry-otlp` | Exports **metrics** over OTLP |
| `io.micrometer:micrometer-tracing-bridge-otel` | Exports **traces** over OTLP |
| `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` | Ships **logs** over OTLP |

### Configuration (`application.yaml`)

OTLP export points at the collector on `localhost:4318`, and Kafka trace propagation is enabled on both sides:

```yaml
spring:
  kafka:
    template:
      observation-enabled: true    # tracing on the producer side (submit)
    listener:
      observation-enabled: true    # tracing on the consumer side (process)

management:
  tracing:
    sampling:
      probability: 1.0             # dev: 100%; lower in production (e.g. 0.1)
  otlp:
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
    logging:
      export:
        schedule-delay: 5s
        otlp:
          endpoint: http://localhost:4318/v1/logs
```

### Metrics in the OpenTelemetry naming (`ObservabilityMetricsConfig`)

`com.payment.config.ObservabilityMetricsConfig` re-registers the JVM / process metric binders using Micrometer's **OpenTelemetry semantic-convention** meter conventions, so metrics carry OTel-standard names and attributes (e.g. `jvm.memory.used` with `jvm.memory.type`) instead of Micrometer's native naming. This is what makes the OpenTelemetry Grafana dashboards match the data. Each binder bean has a `@ConditionalOnMissingBean` default in Spring Boot, so these **replace** the defaults with no duplicate metrics.

A consequence worth knowing: a few metric names differ from the Micrometer defaults — threads are `jvm_thread_count` (not `jvm_threads_live`), classes are `jvm_class_count_classes`, and process CPU is `jvm_cpu_recent_utilization` (not `process_cpu_usage`).

### The Kafka payoff — one trace across two contexts

Because observation is enabled on the Kafka producer and consumer, the trace context travels in the message header. A single payment therefore produces **one end-to-end trace** spanning `POST → produce → consume → INSERT → cache`, crossing both bounded contexts. Without it, the trace would "die" at the broker.

### Logs → Loki (with trace correlation)

Application logs — including the Tomcat framework logs (`org.apache.catalina`, `org.apache.coyote`, `org.apache.tomcat`, enabled at `INFO`) — are shipped to **Loki** through the **OpenTelemetry Logback appender**, and correlated with traces by `trace_id`. Three pieces make this work:

1. **`logback-spring.xml`** declares an `OTEL` appender (`io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender`) attached to the root logger, alongside a `CONSOLE` appender whose pattern includes `trace_id=%X{trace_id} span_id=%X{span_id}` — so every console line and every log record carries its trace/span context.
2. **`com.payment.config.OpenTelemetryLogbackConfiguration`** installs the OpenTelemetry SDK instance into that appender at startup (`@PostConstruct` → `OpenTelemetryAppender.install(openTelemetry)`). This wiring is required because Logback builds the appender before the Spring context exists, so the OTel instance has to be injected once it is available.
3. **`application.yaml`** sets the OTLP logs endpoint (`management.opentelemetry.logging.export.otlp.endpoint`) with a 5-second batch delay.

The result: in Grafana you can open a trace in Tempo and jump straight to its correlated logs in Loki. (Separately, Tomcat's **access log** is written to files under `logs/` via `server.tomcat.accesslog` — that one is file-based and independent of the Loki pipeline.)

### Where the instrumentation lives (hexagonal note)

Observability is cross-cutting infrastructure, so it stays at the edges. HTTP, Kafka, and JDBC spans are created automatically in the adapters, where the technology already lives — the **domain never sees any of it**, and the `HexagonalLayeringTest` stays green. Both observability config classes live in the bootstrap module (composition root), never in a bounded context or the domain.

### Backend + provisioned dashboard

The Grafana LGTM stack runs as a dev container in `docker/observability/`, wired into the root compose. It bundles the OpenTelemetry Collector + Loki (logs) + Tempo (traces) + Prometheus (metrics) + Grafana, receiving OTLP on `4317`/`4318` and serving the UI on `3000`.

A custom dashboard — **"Payment — JVM (OTel semconv)"** (14 panels) — is **provisioned automatically** and set as the Grafana home page (`GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`), so it appears the moment you open `localhost:3000`, with no manual import:

```
docker/observability/
├── docker-compose.yml
└── grafana/
    ├── dashboards/payment-jvm-dashboard.json         # the dashboard (14 panels)
    └── provisioning/dashboards/payment-provider.yaml # file provider that loads it
```

It covers JVM memory (heap / non-heap by pool, utilization), threads, classes, CPU, GC, HTTP (request rate, error rate, latency), the HikariCP connection pool, and log events. Drop any additional dashboard JSON into `grafana/dashboards/` and it is picked up on the next container start.

> The `grafana/otel-lgtm` image is for **development / demo only** — not production. In production, point the same OTLP config at your real collector / managed backend.

### Local URLs

With the infrastructure up (`docker compose up -d` from `docker/`) and the app running, these are the local endpoints:

| UI / Endpoint | URL | What it's for |
|---------------|-----|---------------|
| **Grafana** (telemetry) | [http://localhost:3000](http://localhost:3000) | Metrics, traces, logs. Login `admin` / `admin`. Opens on the Payment JVM dashboard. |
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) | Exercise the REST adapter (submit / query payments). |
| **Kafka UI** | [http://localhost:8082](http://localhost:8082) | Browse topics, messages, consumer groups. |
| **Postgres UI** (pgAdmin) | [http://localhost:5050](http://localhost:5050) | Inspect tables, run SQL. |
| **Redis UI** (RedisInsight) | [http://localhost:5540](http://localhost:5540) | Inspect cached keys / TTLs. |
| **Actuator** | [http://localhost:8081/actuator](http://localhost:8081/actuator) | Health, info, metrics (management port `8081`). |

### Grafana Explore — handy queries

Beyond the dashboard, **Grafana → Explore** lets you run ad-hoc queries. For metrics, pick the **Prometheus** data source, switch the editor to **Code**, and start typing a prefix to use the autocomplete:

| Query (Prometheus) | Shows |
|--------------------|-------|
| `jvm_memory_used_bytes{jvm_memory_type="heap"}` | Heap memory used, per pool (`jvm_memory_pool_name`) |
| `jvm_memory_committed_bytes{jvm_memory_type="heap"}` | Heap committed (utilization baseline under G1) |
| `jvm_cpu_recent_utilization` | Process CPU utilization |
| `jvm_thread_count` | Live JVM threads |
| `jvm_class_count_classes` | Loaded classes |
| `rate(jvm_gc_pause_milliseconds_count[5m])` | GC pause rate |
| `http_server_requests_milliseconds_count` | HTTP request count (after you call the API) |
| `rate(http_server_requests_milliseconds_count{status=~"5.."}[5m])` | HTTP 5xx rate |
| `hikaricp_connections_active` | Active DB connections |
| `logback_events_total` | Log events by level |

For logs, pick the **Loki** data source and query by service, e.g. `{service_name="payment"}` — or open a trace in **Tempo** and follow the trace-to-logs link to see the correlated log lines.

> Metric names follow **OpenTelemetry semantic conventions** because of `ObservabilityMetricsConfig`, and HTTP timers use the `_milliseconds` suffix. When in doubt, the Explore autocomplete lists exactly what exists.

### Observability in tests

Integration tests run against Testcontainers (Postgres, Kafka, Redis) but **no collector**, so the `test` profile turns OTLP export off to avoid "failed to export" noise. And a fast, infrastructure-free fitness function — `ObservabilityMetricsConfigTest` — asserts the JVM binders emit OTel semantic-convention names, so the contract cannot silently regress.

---

## Testing

The project has two independent kinds of automated test, with very different costs.

### Architecture tests — no infrastructure

The `payment-architecture-tests` module (see [Architecture Tests](#architecture-tests-fitness-functions)) only inspects compiled bytecode. It needs no database, broker, or Docker — it runs in a couple of seconds:

```bash
mvn -pl payment-architecture-tests test
```

### Integration tests — real infrastructure via Testcontainers

The context-loading test in `payment-bootstrap` boots the entire Spring application with all modules wired together. Rather than pointing it at an in-memory substitute, it starts **real Postgres, Kafka, and Redis in disposable Docker containers** using [Testcontainers](https://testcontainers.com/).

This is a deliberate choice over H2. An in-memory database makes tests pass, then diverges from Postgres exactly where this project is most sensitive: identity generation, SQL dialect, and the keyset-pagination query. The whole point of the test is to catch a mismatch before production — testing against a *different* engine defeats it. The containers use the same image versions as the `docker/` compose files (`postgres:18.4`, `apache/kafka:4.3.1`, `redis:8.8.0`), so the test environment mirrors production.

Spring Boot's `@ServiceConnection` wires each container into the context automatically — the datasource URL and credentials, `spring.kafka.bootstrap-servers`, and the Redis host/port are all derived from the running containers, so there is no manual property plumbing. The containers are declared `static` in a shared `AbstractIntegrationTest` base class, so they start **once** and are reused across every integration test that extends it.

> **Kafka container class:** the base uses `org.testcontainers.kafka.KafkaContainer`, which supports the `apache/kafka` image used by the compose setup. The older `org.testcontainers.containers.KafkaContainer` is deprecated and targets the Confluent `cp-kafka` image — passing it an `apache/kafka` image fails an image-compatibility check at startup, so the newer class is the correct one here.

**Requirement:** a Docker daemon must be available on the machine running the tests (local dev or CI runner). No `docker compose up` is needed — Testcontainers starts and tears the containers down itself.

```bash
mvn -pl payment-bootstrap test     # starts containers, boots the context, verifies wiring
```

### Everything together

```bash
mvn verify
```

runs both kinds across the whole reactor. Because a violated architecture rule or a failed context load fails the build, wiring `mvn verify` into CI is what keeps the architecture and the wiring from silently eroding over time. The only CI prerequisite is a Docker-capable runner for the Testcontainers step.

---

## Build Notes

A few harmless things you will see in the build output, called out so they don't look like problems:

- **`JAR will be empty - no content was marked for inclusion!`** on `payment-architecture-tests` — expected. That module holds only test code, so its production jar is legitimately empty. It can be silenced with `<skip>true</skip>` on the jar plugin, but there is no harm in leaving it.
- **`The following options were not recognized by any processor: mapstruct.*`** on every module — the parent POM configures the MapStruct annotation processor, but the project does not actually use MapStruct (all mappings are hand-written). The warning is cosmetic; removing the MapStruct `dependencyManagement` entry and its two `annotationProcessorPaths` entries from the parent POM (keeping only Lombok) makes it disappear.
- **`sun.misc.Unsafe` / Lombok warnings** — emitted by Lombok's annotation processor on recent JDKs. Cosmetic, resolved by future Lombok releases.
- **`Mockito self-attaching` / dynamic agent warnings** — emitted by Mockito's inline mock maker under Java 25. Cosmetic; can be silenced by adding Mockito as an explicit `-javaagent`.

---

## API Endpoints

Base path: `/api/v1/payments`

### 1. Submit a payment — `POST /api/v1/payments`

Receives a payment request, validates it, and publishes it to Kafka for asynchronous processing. Returns immediately with `202 Accepted` — the payment is **not yet persisted** at this point.

**Request body:**

```json
{
  "orderId": "78005",
  "amount": 5539.00,
  "currency": "EUR",
  "method": "VISA",
  "fundingType": "CREDIT"
}
```

| Field | Type | Rules |
|-------|------|-------|
| `orderId` | string | required, not blank |
| `amount` | number | required, must be > 0 |
| `currency` | string | required, exactly 3 characters (ISO 4217) |
| `method` | enum | required — `VISA`, `MASTERCARD`, or `PAYPAL` |
| `fundingType` | enum | `CREDIT` or `DEBIT` — **required for cards, must be omitted for `PAYPAL`** |

The `method` / `fundingType` combination is checked by a **cross-field validator** (`@ValidPaymentInstrument`): a card without a funding type, or a PayPal *with* one, is rejected.

**Responses:**

- `202 Accepted` — payment accepted for processing. Includes a `Location: /api/v1/payments/{uuid}` header and a body with the generated `paymentId` and status `ACCEPTED`.
- `400 Bad Request` — validation failed. Body is an RFC 9457 `ProblemDetail` listing the invalid fields.

**Example success body:**

```json
{
  "paymentId": "1bc66998-cb30-4b5f-a29b-fdac9e11cfc0",
  "status": "ACCEPTED",
  "acceptedAt": "2026-07-19T19:40:27.285238Z"
}
```

---

### 2. Get a payment by UUID — `GET /api/v1/payments/{uuid}`

Returns a single payment by its business identifier (the `paymentId` returned at submission). This is the read that is **accelerated by the Redis cache**: the first call hits PostgreSQL and populates Redis; subsequent calls for the same UUID are served from cache.

**Path variable:** `uuid` — the payment UUID.

**Responses:**

- `200 OK` — the payment.
- `404 Not Found` — no payment exists with that UUID.

**Example response:**

```json
{
  "paymentId": "1bc66998-cb30-4b5f-a29b-fdac9e11cfc0",
  "orderId": "78005",
  "amount": 5539.00,
  "currency": "EUR",
  "method": "VISA",
  "fundingType": "CREDIT",
  "status": "COMPLETED",
  "processedAt": "2026-07-19T19:40:27.285238Z"
}
```

---

### 3. List payments (keyset pagination) — `GET /api/v1/payments`

Returns a page of payments using **keyset (cursor) pagination** rather than offset pagination. Keyset pagination performs consistently regardless of how deep into the dataset you go, and is immune to items shifting between pages as new records are inserted.

**Query parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `cursor` | no | — | Opaque (Base64) cursor returned by the previous page. Omit it to fetch the first page. |
| `limit` | no | `20` | Number of items per page. |

**How to page:** call once without a cursor; the response includes a `nextCursor`. Pass that value as `cursor` on the next call. When `nextCursor` is `null`, there are no more pages.

**Example response:**

```json
{
  "items": [
    {
      "paymentId": "1bc66998-cb30-4b5f-a29b-fdac9e11cfc0",
      "orderId": "78005",
      "amount": 5539.00,
      "currency": "EUR",
      "method": "VISA",
      "fundingType": "CREDIT",
      "status": "COMPLETED",
      "processedAt": "2026-07-19T19:40:27.285238Z"
    }
  ],
  "nextCursor": "MQ=="
}
```

The cursor is **opaque** — it is a Base64 token, not a raw sequential id. Clients should treat it as a blind value and simply echo it back, without interpreting its contents.

---

## Testing with Swagger

The service exposes an interactive **Swagger UI** (via springdoc-openapi) where you can browse and execute every endpoint directly from the browser — no external HTTP client needed.

Swagger UI is the **human-facing entry point into the REST driving adapters** — `PaymentController` (the `submit` side) and `PaymentQueryController` (the `process` side). Every operation listed there is one of those adapters invoking a driving port (`SubmitPaymentUseCase`, `GetPaymentUseCase`), so exercising the API through Swagger *is* exercising the driving edge of the hexagon. It is the quickest way both to **test** the endpoints and to **understand** the REST adapter itself: what it exposes, and how each HTTP operation maps to the use case underneath — without writing a single line of client code.

**Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

**OpenAPI spec (raw JSON):** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

> The shorter `/swagger-ui.html` still works too — springdoc simply redirects it to `/swagger-ui/index.html` above.

### Suggested test walkthrough

1. Open Swagger UI at the URL above.
2. **`POST /api/v1/payments`** — expand it, click *Try it out*, paste the example request body, and execute. Copy the `paymentId` from the `202` response.
3. Wait a moment for the Kafka consumer to process and persist the message.
4. **`GET /api/v1/payments/{uuid}`** — paste the `paymentId` and execute. You should get the persisted payment. Run it a second time — this call is now served from the Redis cache.
5. **`GET /api/v1/payments`** — execute without a cursor to get the first page, then copy `nextCursor` into the `cursor` field and execute again to page forward.

### Things worth trying to see the architecture react

- Submit with `method: "PAYPAL"` **and** a `fundingType` → `400` with a validation error (the cross-field validator rejects the combination).
- Submit a card (`VISA`/`MASTERCARD`) **without** `fundingType` → `400` for the same reason.
- `GET` a random/unknown UUID → `404 Not Found`.
