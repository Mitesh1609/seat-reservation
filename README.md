# Seat Reservation

A Spring Boot and PostgreSQL service for reserving assigned seats correctly under
contention. The database is the source of truth: concurrent requests use row locks,
deterministic lock ordering, transactions, and unique constraints to prevent
double-selling, per-user limit races, and duplicate idempotent operations.

The detailed design and current limitations are documented in
[WRITEUP.md](WRITEUP.md).

## Current status

Implemented:

- user registration and RS256 JWT authentication;
- administrator-only show and administrator creation;
- atomic, all-or-nothing multi-seat reservation;
- per-user seat limits and idempotent retries;
- owner-only explicit cancellation;
- show state with per-seat status and reconciliation counts;
- liveness, database-backed readiness, Prometheus metrics, and JSON request logs;
- Flyway-managed PostgreSQL schema;
- a non-root, multi-stage Docker image.

Still pending before submission:

- public deployment and live URL;
- one-command concurrency burst script;
- live burst verification and production tuning;
- dashboards or public log access.

## Technology

- Java 25
- Spring Boot 4.1
- Spring Data JPA and Spring Security
- PostgreSQL
- Flyway
- Micrometer and Prometheus
- Maven Wrapper

## Correctness model

- Requested seats are locked with `PESSIMISTIC_WRITE` in ascending database ID
  order.
- The user row is locked before checking the user's limit and idempotency key.
- Multi-seat requests are all-or-nothing. If one seat is unavailable, none are
  reserved.
- The same user and idempotency key return the original reservation only when the
  show and seats match; a different request returns `409`.
- Cancellation verifies ownership and the current seat assignment while holding
  locks before making seats available again.
- Money is always represented as integer paise.

## Run locally

### Prerequisites

- JDK 25
- PostgreSQL
- PowerShell 7 for the included local RSA-key script

Create an empty PostgreSQL database:

```sql
CREATE DATABASE seat_reservation;
```

Generate a local JWT signing pair:

```powershell
./scripts/New-LocalJwtKeys.ps1
```

The script writes the keys under the Git-ignored `.local/jwt/` directory and prints
the two resource values required below.

Configure these environment variables in the shell or IntelliJ run configuration:

| Variable | Required | Default or example |
| --- | --- | --- |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/seat_reservation` |
| `DB_USERNAME` | No | `postgres` |
| `DB_PASSWORD` | Yes | PostgreSQL password |
| `JWT_PRIVATE_KEY` | Yes | `file:///C:/absolute/path/to/private.pem` |
| `JWT_PUBLIC_KEY` | Yes | `file:///C:/absolute/path/to/public.pem` |
| `JWT_ISSUER` | No | `seat-reservation` |
| `JWT_AUDIENCE` | No | `seat-reservation-api` |
| `JWT_ACCESS_TOKEN_TTL` | No | `15m`, maximum `1h` |

Start the service:

```powershell
./mvnw.cmd spring-boot:run
```

Flyway creates the schema on the first startup. Hibernate validates the migrated
schema and never creates or updates it.

### Provision the first administrator

The application intentionally does not store default administrator credentials in
Git or Flyway. For local development, register a user through the public endpoint,
then promote that account using a trusted database session:

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'admin';
```

Log in again so the newly issued JWT contains the `ADMIN` role. That administrator
can create further administrators through the protected API. In production, this
promotion must be an audited deployment operation using secret-managed credentials.

## API

The local base URL is `http://localhost:8080`.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/web/v1/user/create` | Public | Register a normal user |
| `POST` | `/web/v1/auth/login` | Public | Obtain a bearer token |
| `POST` | `/web/v1/user/create-admin` | ADMIN | Create another administrator |
| `POST` | `/web/v1/show/create` | ADMIN | Create a show and its seats |
| `GET` | `/web/v1/show/{id}` | Authenticated | Read seats and reconciliation counts |
| `POST` | `/web/v1/shows/{id}/reserve` | Authenticated | Reserve one or more seats |
| `POST` | `/web/v1/reservations/{id}/cancel` | Owner | Cancel a reservation |

Protected requests require:

```text
Authorization: Bearer <access_token>
```

### Register and log in

Register:

```json
{
  "username": "mitesh",
  "password": "example-password-123"
}
```

Send the body to `POST /web/v1/user/create`, then send the same body to
`POST /web/v1/auth/login`. A successful login returns:

```json
{
  "access_token": "<signed JWT>",
  "token_type": "Bearer",
  "expires_in": 900
}
```

Passwords must contain at least 8 characters and at most 72 UTF-8 bytes. Only a
salted BCrypt hash is stored.

### Create a show

Send to `POST /web/v1/show/create` with an administrator token:

```json
{
  "name": "friday-night",
  "seats": ["A1", "A2", "A3"],
  "price_paise": 25000,
  "per_user_limit": 4
}
```

### Reserve seats

Send to `POST /web/v1/shows/{show_id}/reserve`:

```json
{
  "seats": ["A1", "A2"],
  "idempotency_key": "checkout-8bb671ad"
}
```

Success returns `201`. A taken seat, exceeded user limit, or conflicting reuse of
an idempotency key returns `409`. Invalid input returns `400`.

### Cancel a reservation

The reservation owner sends:

```text
POST /web/v1/reservations/{reservation_id}/cancel
```

Cancellation is idempotent: cancelling an already-cancelled reservation returns its
current cancelled state. A user cannot cancel another user's reservation.

API errors contain only the HTTP status and message:

```json
{
  "status": 409,
  "message": "Seats are not available"
}
```

## Health and metrics

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health/liveness` | Internal application liveness |
| `/actuator/health/readiness` | Readiness including PostgreSQL connectivity |
| `/actuator/prometheus` | Prometheus exposition |

Application metrics include:

```text
seat_reservation_reservations_confirmed_total
seat_reservation_reservations_declined_total{reason="seat_taken"}
seat_reservation_reservations_declined_total{reason="per_user_limit"}
seat_reservation_idempotent_replays_total
seat_reservation_seats_available
```

Console logs are JSON. Each response contains an `X-Request-ID`; callers may supply
a safe request ID or allow the service to generate one. Completion logs contain the
request ID, method, path, response status, and duration.

## Database migrations

Migrations live under `src/main/resources/db/migration`. Every migration must be
safe to execute repeatedly. Flyway validates migration names and its destructive
clean operation is disabled.

If an older local database was created by Hibernate before Flyway was introduced,
it has no Flyway history. Reset that disposable local database once, or use a
deliberate baseline procedure if its data must be retained.

## Container image

Build the image:

```powershell
docker build -t seat-reservation .
```

The Dockerfile builds the executable jar in a Java 25 build stage, copies it into a
Java 25 runtime image, and runs with a non-root numeric user. Tests and local secret
files are excluded from the container build context.

At runtime, provide the database variables and mount the RSA key files as read-only
container secrets. Set `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` to their mounted
`file:` locations. The container listens on port `8080`.

## Build verification

Package the main application without compiling or running test sources:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

The current source packages successfully. The Docker image has not been built on
this workstation because Docker is not installed here.
