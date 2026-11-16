# Wallet Service

Part of the Yongdrel Gyenshog virtual lottery platform. Jira: **SCRUM-252**.
Owner: Wangchuk Gyeltshen Zangpo.

One BTN wallet per user. Users spend from their own wallet; only trusted services (or an ADMIN) can add money.
The balance can **never go negative**, and every operation is recorded in the Transaction Ledger.

| | |
|---|---|
| Port | `8097` |
| Database | MySQL `yongdrel_wallets` (schema managed by Flyway) |
| Depends on | Transaction Ledger (`POST /api/ledger/entries`) |
| Stack | Spring Boot 4.1.1, Java 17, Spring Security (JWT RS256), Flyway |

## API

| Method | Path | Who | Behaviour |
|---|---|---|---|
| `GET` | `/api/wallets/me` | Any user JWT | The caller's wallet; created with balance 0 on first use |
| `POST` | `/api/wallets/debit` | Any user JWT (own wallet only) | Header **`Idempotency-Key`** required. `201` applied; `200` same key already applied; `409` same key with a different amount/type; `422 INSUFFICIENT_FUNDS` |
| `POST` | `/api/wallets/credit` | **Internal services** (`X-Internal-Api-Key`) **or ADMIN** JWT | Header `Idempotency-Key` required. Normal users get `403` |
| `GET` | `/actuator/health` | Anyone | Health check |

No update or delete endpoints. Through the API Gateway (`:8080`) only `/api/wallets/me` and `/api/wallets/debit`
are routed; `credit` is called by other services directly on the internal network.

### Examples

```
POST /api/wallets/debit
Authorization: Bearer <user JWT>
Idempotency-Key: order-123-payment
Content-Type: application/json

{ "amount": 30.00, "description": "Ticket purchase" }
```

```
POST /api/wallets/credit
X-Internal-Api-Key: <WALLET_INTERNAL_API_KEY>
Idempotency-Key: refund-036f3028
Content-Type: application/json

{ "ownerId": "11111111-1111-4111-8111-111111111111", "amount": 40.00, "description": "Refund" }
```

Amounts must be greater than 0 with at most 4 decimals. Errors are JSON with `status`, `error`
(`VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INSUFFICIENT_FUNDS`, `IDEMPOTENCY_KEY_CONFLICT`) and `message`.

## Data integrity

- **Row lock:** each credit/debit locks the wallet row (`SELECT ... FOR UPDATE`), so concurrent operations on one
  wallet run one at a time; a debit is only applied if the balance covers it.
- **Database guards:** `CHECK (balance >= 0)`, `UNIQUE (owner_id)` (one wallet per user) and
  `UNIQUE (wallet_id, request_key)` (each Idempotency-Key applied once).
- **Money types:** `DECIMAL(19,4)` / `BigDecimal`, never floating point.
- **Ledger:** after commit, each operation is posted to the Ledger as `WALLET` / `CREDIT` or `DEBIT` with reference
  `wallet-op-{id}`. If the Ledger is down, the operation is marked `ledger_recorded = false` and a background job
  re-sends it every 30 seconds (`app.wallet.ledger-sync.*`).

## Configuration

| Environment variable | Purpose |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | MySQL login |
| `JWT_PUBLIC_KEY_PATH` | RS256 public key (`infrastructure/keys/public.pem`) |
| `WALLET_INTERNAL_API_KEY` | Secret for `POST /api/wallets/credit`. If empty, only ADMIN can credit |
| `LEDGER_SERVICE_URL` | Ledger base URL (default `http://localhost:8098`) |
| `LEDGER_INTERNAL_API_KEY` | Same key the Ledger expects |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | RabbitMQ login |

Real values go in `infrastructure/.env` (git-ignored); `infrastructure/.env.example` lists the names.

## Running

### 1. One-time: create the database (PowerShell, repository root)

```
"CREATE DATABASE IF NOT EXISTS yongdrel_wallets; GRANT ALL PRIVILEGES ON yongdrel_wallets.* TO 'yongdrel'@'%'; FLUSH PRIVILEGES;" | docker exec -i yongdrel-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

### 2a. Docker Compose (repository root)

```
cd services\wallet-service
.\mvnw.cmd clean package
cd ..\..
docker compose -f infrastructure/compose.yml --env-file infrastructure/.env up -d --build wallet-service
curl.exe -i http://localhost:8097/actuator/health
```

### 2b. Locally with Maven (PowerShell, repository root)

```
Get-Content infrastructure\.env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $n, $v = $_ -split '=', 2; Set-Item "env:$n" $v }
$env:DB_USERNAME = "yongdrel"; $env:DB_PASSWORD = $env:MYSQL_PASSWORD
$env:JWT_PUBLIC_KEY_PATH = ($PWD.Path -replace '\\','/') + "/infrastructure/keys/public.pem"
cd services\wallet-service
.\mvnw.cmd spring-boot:run
```

## Tests

```
cd services\wallet-service
.\mvnw.cmd clean test
```

29 tests: unit (`WalletServiceTest`), web + security (`WalletControllerSecurityTest`), and integration against a
real MySQL 8.4 via **Testcontainers** (`WalletMySqlIntegrationTest`, `WalletServiceApplicationTests`), including
10 concurrent debits from one wallet (balance never below 0) and concurrent retries with the same key.
Docker must be running. On Windows, if Testcontainers cannot find Docker, see
`services/transaction-ledger-service/README.md`.

## Known limitations

- Refund and Prize Claim do not yet credit wallets automatically (they can, via `POST /api/wallets/credit`).
- Single currency (`BTN`), set by `app.wallet.currency`.
- No `WalletCredited` / `WalletDebited` RabbitMQ events yet; other services read history from the Ledger.
