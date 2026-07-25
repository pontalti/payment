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

Each context module follows the same internal hexagonal layout (`domain` → `application` → `adapter`). At the **root of the project** there is a `docker/` folder containing `docker-compose` files that provision all the required infrastructure — **Redis, Kafka, and PostgreSQL** — so the whole environment can be started for local runs (see [Running the Application](#running-the-application)).

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

---

## Running the Application

### Prerequisites — infrastructure via Docker

All required infrastructure is provided as **`docker-compose`** files inside the **`docker/` folder at the root of the project**. It provisions the three backing services the application depends on:

- **PostgreSQL** on `localhost:5432` (database `appdb`)
- **Kafka broker** on `localhost:9092`
- **Redis** on `localhost:6379` (with the password configured in `application.yaml`)

Start each service with `docker compose up -d` from its folder under `docker/`, then stop them with `docker compose down`.

> Make sure the ports above are free before starting, and that the credentials in the compose files match the ones in `payment-bootstrap/src/main/resources/application.yaml` (datasource user/password and Redis password).

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

**Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**OpenAPI spec (raw JSON):** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

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
