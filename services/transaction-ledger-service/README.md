# Transaction Ledger Service

Part of the Yongdrel Gyenshog virtual lottery platform. Jira: **SCRUM-253** (subtasks SCRUM-293 to SCRUM-297).
Owner: Wangchuk Gyeltshen Zangpo.

An **append-only** record of every money movement (wallet debits/credits, payments, refunds).
Entries are only ever inserted: there is no update or delete endpoint, and the JPA entity is `@Immutable`.

| | |
|---|---|
| Port | `8098` |
| Database | MySQL `yongdrel_ledger` (schema managed by Flyway) |
| Stack | Spring Boot 4.1.1, Java 17, Spring Security (JWT RS256), Flyway |

## API

| Method | Path | Caller | Behaviour |
|---|---|---|---|
| `POST` | `/api/ledger/entries` | **Internal services only** (header `X-Internal-Api-Key`) | `201` new entry; `200` same `referenceId` already recorded with the same details (safe retry); `409` same `referenceId` with different details; `400` invalid body |
| `GET` | `/api/ledger/me?page=0&size=20` | Any logged-in user (Bearer JWT) | The caller's own entries, newest first. Account = JWT `sub`. Max page size 100 |
| `GET` | `/actuator/health` | Anyone | Health check |

Everything else (including any `PUT`/`PATCH`/`DELETE`) is refused.

Through the API Gateway (`:8080`) only `GET /api/ledger/me` is routed. The `POST` endpoint is deliberately
not exposed through the gateway; other services call it directly on the internal network.

### Example: record an entry (internal)

```
POST /api/ledger/entries
X-Internal-Api-Key: <LEDGER_INTERNAL_API_KEY>
Content-Type: application/json

{
  "accountId": "1",
  "referenceId": "wallet-op-001",
  "sourceType": "WALLET",
  "direction": "DEBIT",
  "amount": 50.00,
  "currency": "BTN",
  "description": "Ticket purchase",
  "occurredAt": "2026-11-09T10:00:00Z"
}
```

| Field | Rules |
|---|---|
| `accountId` | Required, max 100. The user ID (same value as the JWT `sub`) |
| `referenceId` | Required, max 100, **unique**. The source transaction ID (e.g. wallet operation, payment, refund ID) |
| `sourceType` | `WALLET`, `PAYMENT` or `REFUND` |
| `direction` | `DEBIT` (money out) or `CREDIT` (money in) |
| `amount` | Required, greater than 0, max 4 decimal places |
| `currency` | 3 uppercase letters, e.g. `BTN` |
| `description` | Optional, max 255 |
| `occurredAt` | Optional ISO-8601 instant; defaults to the time it is recorded |

Error responses are JSON with `status`, `error` code (`VALIDATION_FAILED`, `MALFORMED_REQUEST`,
`DUPLICATE_REFERENCE_CONFLICT`), `message`, and for validation errors a `fields` map. No stack traces are returned.

## Data integrity

- `UNIQUE (reference_id)` in MySQL backs up the idempotency check, so even simultaneous retries create only one row
  (covered by an 8-thread concurrency test).
- `CHECK` constraints: `amount > 0`, `direction IN ('DEBIT','CREDIT')`.
- Money is `DECIMAL(19,4)` / `BigDecimal`, never floating point.
- Single-entry ledger (one row per movement), not full double-entry.

## Configuration

| Environment variable | Purpose |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | MySQL login (user `yongdrel`) |
| `JWT_PUBLIC_KEY_PATH` | Path to the RS256 public key (`infrastructure/keys/public.pem`) |
| `LEDGER_INTERNAL_API_KEY` | Shared secret for `POST /api/ledger/entries`. If empty, **no one** can write (fails closed) |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | RabbitMQ login |

Real values live in `infrastructure/.env` (git-ignored). `infrastructure/.env.example` lists the names only.
Generate a key with, for example, `openssl rand -hex 32`.

## Running

### 1. One-time: create the database

MySQL only creates `yongdrel_template` on first start. From the repository root (PowerShell):

```
"CREATE DATABASE IF NOT EXISTS yongdrel_ledger; GRANT ALL PRIVILEGES ON yongdrel_ledger.* TO 'yongdrel'@'%'; FLUSH PRIVILEGES;" | docker exec -i yongdrel-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

Flyway creates the `ledger_entries` table automatically on first start.

### 2a. With Docker Compose

From the repository root:

```
cd services\transaction-ledger-service
.\mvnw.cmd clean package
cd ..\..
docker compose -f infrastructure/compose.yml --env-file infrastructure/.env up -d --build transaction-ledger-service
curl.exe -i http://localhost:8098/actuator/health
```

### 2b. Locally with Maven (PowerShell)

From the repository root, load `.env` into the current window, then start:

```
Get-Content infrastructure\.env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $n, $v = $_ -split '=', 2; Set-Item "env:$n" $v }
$env:DB_USERNAME = "yongdrel"
$env:DB_PASSWORD = $env:MYSQL_PASSWORD
$env:JWT_PUBLIC_KEY_PATH = ($PWD.Path -replace '\\','/') + "/infrastructure/keys/public.pem"
cd services\transaction-ledger-service
.\mvnw.cmd spring-boot:run
```

## Tests

```
cd services\transaction-ledger-service
.\mvnw.cmd test
```

27 tests: unit (`LedgerServiceTest`), web + security (`LedgerControllerSecurityTest`), and integration against a
real MySQL 8.4 started by **Testcontainers** (`LedgerMySqlIntegrationTest`, `TransactionLedgerServiceApplicationTests`).
Docker must be running. No `.env` or key file is needed for tests.

**Windows + Docker Desktop note:** if tests fail with *"Could not find a valid Docker environment"*, run
`docker context ls`. If the active endpoint is `npipe:////./pipe/dockerDesktopLinuxEngine`, create
`%USERPROFILE%\.testcontainers.properties` containing:

```
docker.host=npipe:////./pipe/dockerDesktopLinuxEngine
```

## Not yet implemented

- Consuming financial events from RabbitMQ (handbook section 7). Writes currently come through the internal HTTP endpoint.
- Wallet and Refund services calling this ledger (planned in SCRUM-252 / SCRUM-254).
