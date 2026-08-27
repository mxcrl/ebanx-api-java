# EBANX API — Java 21

Same exercise as the Node version, rebuilt in plain Java 21 with **zero
external dependencies** — just the JDK. That's a deliberate choice: no
Maven/Gradle setup needed, `javac`/`java` is enough.

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
  Main.java                    entrypoint — starts the server on $PORT (default 3000)
  domain/                      business logic — no HTTP concepts appear here
    Account.java                mutable balance (AtomicLong, thread-safe)
    AccountRepository.java      in-memory storage (ConcurrentHashMap)
    AccountNotFoundException.java
    MalformedEventException.java
    Event.java                  sealed interface: permits Deposit, Withdraw, Transfer
    Deposit.java / Withdraw.java / Transfer.java   records implementing Event
    EventParser.java            turns raw JSON into a validated Event (or throws)
    AccountService.java         deposit/withdraw/transfer rules + exhaustive event dispatch
  http/                         transport layer — translates HTTP <-> domain
    Json.java                   dependency-free JSON parser/serializer
    QueryParser.java
    HttpSupport.java            shared request/response helpers
    ResetHandler.java / BalanceHandler.java / EventHandler.java
    Router.java
    ServerFactory.java          composition root (wires domain + HTTP together)
src/test/java/com/ebanx/
  EndToEndTest.java             starts a real server on an ephemeral port and hits it over HTTP
```

## Why it's built this way (resilience notes)

- **Sealed `Event` hierarchy + exhaustive `switch`**: `Event` permits exactly
  `Deposit`, `Withdraw`, `Transfer`. `AccountService.apply` switches over it
  without a `default` branch — if a new event type is ever added and someone
  forgets to handle it here, **the project won't compile**. That's a compiler-
  enforced guardrail against a whole class of "silently does nothing" bugs.
- **Validation lives in one place** (`EventParser`): every malformed-input
  path — missing type, unknown type, missing field, non-numeric or
  non-integer amount — is checked before any domain logic runs, and always
  throws the same `MalformedEventException`, which the HTTP layer maps to a
  single, predictable `400`.
- **Every HTTP handler is wrapped in try/catch/finally**: an unexpected bug
  in one request becomes a `500` response, never a crashed server or a
  connection the client hangs waiting on forever (`exchange.close()` always
  runs).
- **Thread-safety by construction**: `Account.balance` is an `AtomicLong`
  and `AccountRepository` is backed by `ConcurrentHashMap` with
  `computeIfAbsent`, so two concurrent requests touching the same account
  (e.g. two deposits racing to create it) can't corrupt state or double-create
  an account. The server also runs each request on its own JDK 21 virtual
  thread (`Executors.newVirtualThreadPerTaskExecutor()`), so one slow request
  can't starve the others.
- **Domain has zero HTTP knowledge**: `AccountService` and `EventParser`
  never see an `HttpExchange` or a status code — they throw
  `AccountNotFoundException` / `MalformedEventException`, and only the
  `http` package decides what those become on the wire. That's what lets
  `EndToEndTest` (or a future proper unit test) exercise the rules directly.
- **Hand-rolled but real JSON parsing**: `Json.java` is a genuine
  recursive-descent parser (handles escapes, nesting, numbers correctly)
  rather than string-splitting hacks, so malformed bodies fail predictably
  instead of doing something undefined.

## Running it

Requires JDK 21.

```bash
# compile
javac -d out $(find src/main/java -name "*.java")

# run (defaults to port 3000; override with PORT env var)
java -cp out com.ebanx.Main
```

## Running the tests

```bash
javac -d out -cp out $(find src/test/java -name "*.java")
java -cp out com.ebanx.EndToEndTest
```

This starts a real `HttpServer` on an ephemeral port and drives it with
`java.net.http.HttpClient` — no mocking, no test framework dependency —
covering the 9 example scenarios from the assignment plus a handful of
edge cases (negative balances, malformed JSON, unknown event type, missing
fields, non-integer amounts).
