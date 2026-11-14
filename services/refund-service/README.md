# Refund Service

Part of the Yongdrel Gyenshog virtual lottery platform. Jira: **SCRUM-254** (subtasks SCRUM-298 to SCRUM-302).
Owner: Wangchuk Gyeltshen Zangpo.

Refunds a **CONFIRMED** payment, fully or in parts, without ever refunding more than was paid.
Each successful refund is recorded as a `REFUND` / `CREDIT` entry in the Transaction Ledger.

| | |
|---|---|
| Port | `8099` |
| Database | MySQL `yongdrel_refunds` (schema managed by Flyway) |
| Depends on | Payment Service (`GET /api/payments/{id}`), Transaction Ledger (`POST /api/ledger/entries`) |
| Stack | Spring Boot 4.1.1, Java 17, Spring Security (JWT RS256), Flyway |

## API

All endpoints need a user JWT (`Authorization: Bearer ...`). The user is always taken from the JWT `sub`.

| Method | Path | Behaviour |
|---|---|---|
| `POST` | `/api/refunds` | Header **`Idempotency-Key`** required. `201` new refund; `200` same key already handled (same refund returned); `409` same key with a different payment/amount; `422` over-refund or payment not `CONFIRMED`; `404` payment not found or not yours; `503` Payment Service unreachable |
| `GET` | `/api/refunds/{id}` | Owner or `ADMIN`; anyone else gets `404` |
| `POST` | `/api/refunds/{id}/retry` | Retries a `FAILED` refund (owner or `ADMIN`); `409` if not `FAILED` |
| `GET` | `/actuator/health` | Public health check |

There are no update or delete endpoints. Through the API Gateway (`:8080`), `/api/refunds/**` is routed here.

### Example

```
POST /api/refunds
Authorization: Bearer <user JWT>
Idempotency-Key: 7f1c2a90-order-123-refund-1
Content-Type: application/json

{ "paymentId": "4451ba8c-f559-4ac4-b6fd-7d06f84e275c", "amount": 40.00, "reason": "Event cancelled" }
```

| Field | Rules |
|---|---|
| `paymentId` | Required UUID of a payment in the Payment Service |
| `amount` | Required, greater than 0, max 4 decimal places |
| `reason` | Optional, max 255 |

Error bodies are JSON with `status`, `error` (`VALIDATION_FAILED`, `MALFORMED_REQUEST`, `NOT_FOUND`, `OVER_REFUND`,
`PAYMENT_NOT_REFUNDABLE`, `IDEMPOTENCY_KEY_CONFLICT`, `INVALID_REFUND_STATE`, `DEPENDENCY_UNAVAILABLE`) and `message`.

## How a refund works

1. **Idempotency:** the same user + `Idempotency-Key` always maps to one refund (`UNIQUE (user_id, idempotency_key)`).
2. **Payment check:** calls the Payment Service with the caller's own JWT. The payment must exist, belong to the caller
   and be `CONFIRMED`.
3. **No over-refund:** the payment's row in `payment_refund_balances` is locked (`SELECT ... FOR UPDATE`) while the
   amount is reserved, so concurrent refunds are handled one at a time. A database `CHECK` (`reserved_amount <= paid_amount`)
   is a final safety net.
4. **Provider + ledger:** the mock refund provider is called, then the Ledger entry is posted with reference
   `refund-{id}` (idempotent). Status becomes `COMPLETED`.
5. **Failures are kept:** if the provider or Ledger fails, the refund stays `FAILED` with `failureReason`, and the amount
   stays reserved so it cannot be refunded twice. `POST /api/refunds/{id}/retry` repeats step 4.

Statuses: `PENDING` -> `COMPLETED` or `FAILED`; `FAILED` -> `COMPLETED` (retry).

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | `yongdrel` / none | MySQL login |
| `JWT_PUBLIC_KEY_PATH` | none | RS256 public key (`infrastructure/keys/public.pem`) |
| `PAYMENT_SERVICE_URL` | `http://localhost:8086` | Payment Service base URL |
| `LEDGER_SERVICE_URL` | `http://localhost:8098` | Transaction Ledger base URL |
| `LEDGER_INTERNAL_API_KEY` | none | Same key the Ledger expects (from `infrastructure/.env`) |
| `REFUND_PROVIDER_MODE` | `SUCCESS` | `FAIL` makes the mock provider fail (to test retry) |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | | RabbitMQ login |

## Running

### 1. One-time: create the database (PowerShell, repository root)

```
"CREATE DATABASE IF NOT EXISTS yongdrel_refunds; GRANT ALL PRIVILEGES ON yongdrel_refunds.* TO 'yongdrel'@'%'; FLUSH PRIVILEGES;" | docker exec -i yongdrel-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

### 2a. Docker Compose (repository root)

```
cd services\refund-service
.\mvnw.cmd clean package
cd ..\..
docker compose -f infrastructure/compose.yml --env-file infrastructure/.env up -d --build refund-service
curl.exe -i http://localhost:8099/actuator/health
```

### 2b. Locally with Maven (PowerShell, repository root)

```
Get-Content infrastructure\.env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $n, $v = $_ -split '=', 2; Set-Item "env:$n" $v }
$env:DB_USERNAME = "yongdrel"; $env:DB_PASSWORD = $env:MYSQL_PASSWORD
$env:JWT_PUBLIC_KEY_PATH = ($PWD.Path -replace '\\','/') + "/infrastructure/keys/public.pem"
cd services\refund-service
.\mvnw.cmd spring-boot:run
```

The Payment Service (port 8086) and Transaction Ledger (port 8098) must also be running for real refunds.

## Tests

```
cd services\refund-service
.\mvnw.cmd clean test
```

30 tests: unit (`RefundServiceTest`), web + security (`RefundControllerSecurityTest`), and integration against a real
MySQL 8.4 via **Testcontainers** (`RefundMySqlIntegrationTest`, `RefundServiceApplicationTests`), including
10 concurrent refunds against one payment (never more than paid) and concurrent retries with the same key.
Docker must be running. On Windows, if Testcontainers cannot find Docker, see the note in
`services/transaction-ledger-service/README.md`.

## Known limitations

- The Payment Service is not yet in `infrastructure/compose.yml`; the refund container expects it at
  `http://payment-service:8086`.
- The Payment's own status is not changed to `REFUNDED` (the Payment Service has no endpoint for it yet).
- The provider is a mock (handbook: "mock/sandbox provider").
- No `RefundProcessed` RabbitMQ event yet; other services learn about refunds via the Ledger.
