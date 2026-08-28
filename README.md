# EBANX API — Spring Boot (Java 21)

A small in-memory "banking" API, built on **Spring Boot 3.3 + Java 21 (LTS)**.
Java 21 is the current Java LTS release (supported through at least 2031)
with mature, widely-audited tooling — a sensible baseline for a fintech
service. Spring Boot 3.3 targets that same JDK baseline and is a stable,
broadly deployed release line.

## Endpoints

| Method | Path                        | Description                                    |
|--------|-----------------------------|-------------------------------------------------|
| POST   | `/reset`                    | Wipes all in-memory state                        |
| GET    | `/balance?account_id=<id>`  | Returns the balance for an account                |
| POST   | `/event`                    | Applies a `deposit`, `withdraw`, or `transfer`    |

### Business rules

- `deposit` creates the destination account if it doesn't exist yet.
- `withdraw` / `transfer` require the **origin** account to already exist,
  or the response is `404` with body `0`.
- **Withdrawals are allowed to push the balance negative** — that's an
  explicit rule, not a bug.
- A malformed request (bad JSON, unknown `type`, missing required field,
  non-integer `amount`) always returns `400`.

## Project layout

```
src/main/java/com/ebanx/
  EbanxApiApplication.java     Spring Boot entrypoint
  domain/                      business logic — no HTTP concepts appear here
    Account.java                mutable balance (AtomicLong, thread-safe)
    AccountRepository.java      in-memory storage (ConcurrentHashMap), @Repository
    AccountNotFoundException.java
    MalformedEventException.java
    Event.java                  sealed interface: permits Deposit, Withdraw, Transfer
    Deposit.java / Withdraw.java / Transfer.java   records implementing Event
    EventParser.java            turns a parsed JSON body into a validated Event (or throws)
    AccountService.java         deposit/withdraw/transfer rules + exhaustive event dispatch, @Service
  web/                          transport layer — translates HTTP <-> domain
    AccountController.java      @RestController for /reset, /balance, /event
    GlobalExceptionHandler.java @RestControllerAdvice mapping domain exceptions to HTTP responses
src/main/resources/
  application.yml               server.port bound to $PORT (default 3000)
src/test/java/com/ebanx/
  EndToEndTest.java             @SpringBootTest on a random port, driven with TestRestTemplate
```

## Why it's built this way (resilience notes)

- **Sealed `Event` hierarchy + exhaustive `switch`**: `Event` permits exactly
  `Deposit`, `Withdraw`, `Transfer`. `AccountService.apply` switches over it
  without a `default` branch — if a new event type is ever added and someone
  forgets to handle it here, **the project won't compile**.
- **Validation lives in one place** (`EventParser`): every malformed-input
  path — missing type, unknown type, missing field, non-numeric or
  non-integer amount — is checked before any domain logic runs, and always
  throws the same `MalformedEventException`, which `GlobalExceptionHandler`
  maps to a single, predictable `400`.
- **Centralized exception handling**: `GlobalExceptionHandler`
  (`@RestControllerAdvice`) is the only place that decides HTTP status
  codes — `AccountNotFoundException` -> `404`, `MalformedEventException` /
  unparsable JSON -> `400`, anything unforeseen -> `500`. The controller
  itself never touches a status code for the error paths.
- **Thread-safety by construction**: `Account.balance` is an `AtomicLong`
  and `AccountRepository` is backed by `ConcurrentHashMap` with
  `computeIfAbsent`, so two concurrent requests touching the same account
  (e.g. two deposits racing to create it) can't corrupt state or
  double-create an account.
- **Domain has zero HTTP knowledge**: `AccountService` and `EventParser`
  never see a servlet request or a status code — they throw
  `AccountNotFoundException` / `MalformedEventException`, and only the
  `web` package decides what those become on the wire. That's what lets
  the domain classes be unit-tested directly, with no server involved.

## Running it

Requires JDK 21 (Maven wrapper not included — use a locally installed Maven).

```bash
mvn spring-boot:run
```

Listens on port `3000` by default; override with the `PORT` env var.

## Running the tests

```bash
mvn test
```

`EndToEndTest` boots the full Spring context on a random port and drives it
with `TestRestTemplate` — no mocking of the domain layer — covering the 9
example scenarios from the assignment plus a handful of edge cases (negative
balances, malformed JSON, unknown event type, missing fields, non-integer
amounts).

## Building a jar

```bash
mvn package
java -jar target/ebanx-api-java-0.0.1-SNAPSHOT.jar
```
