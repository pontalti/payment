# Payment Service

A sample payment service built with **Spring Boot 4.1** and **Java 25**, designed to demonstrate **Hexagonal Architecture (Ports & Adapters)** with **two separate and independent bounded contexts** communicating asynchronously through Kafka.

The service receives a payment request over REST, publishes it to Kafka, and a consumer processes the message and persists it in PostgreSQL. Reads are served through a Redis cache.

Each bounded context is a **separate Maven module**, so the independence between them is enforced by the build itself — not merely by convention.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Maven Module Layout](#maven-module-layout)
- [The Two Bounded Contexts](#the-two-bounded-contexts)
- [Hexagonal Architecture Explained](#hexagonal-architecture-explained)
- [Project Structure](#project-structure)
- [What Each Layer Means](#what-each-layer-means)
- [Request Flow](#request-flow)
- [Technology Stack](#technology-stack)
- [Running the Application](#running-the-application)
- [API Endpoints](#api-endpoints)
- [Testing with Swagger](#testing-with-swagger)

---

## Architecture Overview

This project applies **Hexagonal Architecture** (also known as **Ports & Adapters**), an architectural style whose central idea is to isolate the **domain** (business logic) from the **infrastructure** (frameworks, databases, message brokers, HTTP).

The domain sits at the center of a hexagon. Everything outside — REST controllers, Kafka producers/consumers, JPA repositories, Redis — plugs into the domain through **ports** (interfaces defined by the domain) that are fulfilled by **adapters** (concrete implementations that know about a specific technology).

The single most important rule: **dependencies always point inward.** Adapters depend on the domain; the domain never depends on adapters. The domain does not know Kafka, PostgreSQL, or HTTP exist. This makes the business logic independent, testable in isolation, and resistant to changes in infrastructure.

On top of that hexagonal core, the project is split into **three Maven modules** so that the boundary between the two bounded contexts is guaranteed at compile time: `payment-submit` and `payment-process` cannot reference each other's code, because neither declares a dependency on the other.

---

## Maven Module Layout

The project is a **multi-module Maven build**. A parent (aggregator) POM at the root declares the three modules and centralizes versions and the build plugin configuration.

```
payment/                         # parent / aggregator POM (packaging: pom)
│
├── payment-submit/              # BOUNDED CONTEXT 1 — receive & forward (stateless)
├── payment-process/             # BOUNDED CONTEXT 2 — consume & persist (owns state)
└── payment-bootstrap/           # composition root — main class, config, packaging
```

Responsibilities and dependencies of each module:

| Module | Depends on | Contains |
|--------|-----------|----------|
| `payment-submit` | *(neither other module)* | The full `submit` hexagon: domain, application service, REST + Kafka producer adapters. |
| `payment-process` | *(neither other module)* | The full `process` hexagon: domain, application services, REST query + Kafka consumer + JPA/Redis persistence adapters. |
| `payment-bootstrap` | `payment-submit`, `payment-process` | `PaymentApplication` (the `@SpringBootApplication`), `application.yaml`, `schema.sql`, and the Spring Boot Maven plugin that produces the runnable fat jar. |

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

Each context module follows the same internal hexagonal layout (`domain` → `application` → `adapter`). At the **root of the project** there is a `docker/` folder containing a `docker-compose` file that provisions all the required infrastructure — **Redis, Kafka, and PostgreSQL** — so the whole environment can be started with a single command (see [Running the Application](#running-the-application)).

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
└── payment-bootstrap/                          # ── MODULE: composition root ──
    ├── com.payment
    │   └── PaymentApplication.java             #   @SpringBootApplication entry point
    └── src/main/resources
        ├── application.yaml                    #   all runtime configuration lives here
        └── schema.sql
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

---

## Running the Application

### Prerequisites — infrastructure via Docker

All required infrastructure is provided as a **`docker-compose`** file inside the **`docker/` folder at the root of the project**. It provisions the three backing services the application depends on:

- **PostgreSQL** on `localhost:5432` (database `appdb`)
- **Kafka broker** on `localhost:9092`
- **Redis** on `localhost:6379` (with the password configured in `application.yaml`)

Start the full stack with a single command:

```bash
cd docker
docker compose up -d
```

This brings up Redis, Kafka, and PostgreSQL in the background. To stop and remove them:

```bash
docker compose down
```

> Make sure the ports above are free before starting, and that the credentials in `docker-compose` match the ones in `payment-bootstrap/src/main/resources/application.yaml` (datasource user/password and Redis password).

### Build all modules

From the **project root** (where the parent POM lives):

```bash
mvn clean install
```

This builds `payment-submit`, `payment-process`, and `payment-bootstrap` in the correct order (Maven resolves it from the dependency graph) and produces the runnable jar in `payment-bootstrap/target/`.

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
