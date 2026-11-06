# Seat Reservation: Design Write-up

## Atomic reservation decision

PostgreSQL is the system of record. A reservation runs inside one database
transaction and takes pessimistic write locks before deciding whether it can
succeed.

The transaction first locks the user row. This serializes concurrent reservation
attempts from the same user, so the per-show seat limit and the user's idempotency
keys cannot race with another request from that user. It then loads every requested
seat using `PESSIMISTIC_WRITE`, ordered by seat ID. Concurrent buyers targeting the
same seat therefore wait for the same database row; after the winner commits, the
next transaction observes the seat as confirmed and receives a `409` decline.

Multi-seat requests are all-or-nothing. Every requested seat is locked and checked
before any of them is changed. If one seat is missing or unavailable, the
transaction is rolled back and no seat in the request is reserved. Sorting locks by
seat ID gives all reservation transactions the same lock order, reducing deadlock
risk when requests overlap.

The database also enforces one seat number per show with
`uk_seat_show_number`. Money is stored and calculated as integer paise.

## Per-user limit

After locking the user, the service counts that user's confirmed seats for the
show. Because every concurrent request for that user must acquire the same user
lock first, two requests cannot independently pass the limit check and then both
commit. A request that would exceed the configured limit returns `409` without
changing any seats.

## Idempotency

The idempotency key is stored on the reservation. The database unique constraint
`uk_reservation_user_idempotency` enforces one reservation per user and key.

Because the user row is locked before the existing-key lookup, concurrent retries
for the same user are serialized. A retry with the same key, show, and ordered seat
list returns the original reservation without creating another reservation or
moving seats. Reusing the key for another show or seat request returns `409`.

Keys are scoped to a user, not globally, so different users may use the same key.
Within one user's account, a key is intentionally single-use across all shows.

## Cancellation model

This implementation uses immediate confirmation plus explicit cancellation; it
does not currently create expiring holds. Only the reservation owner can cancel,
because the user identity comes from the validated JWT rather than the request
body.

Cancellation locks the user and reservation, then locks the associated seats in
the same deterministic order used during reservation. It verifies that every seat
is still confirmed for that user before releasing them. The reservation remains as
cancelled history while the seats return to available and can be booked again.
Repeated cancellation returns the already-cancelled reservation.

## Consistency versus availability

The service chooses consistency for reservation decisions. It does not accept a
booking unless it can complete the transaction against the PostgreSQL primary. If
the database is unavailable or partitioned from an application instance, that
instance fails readiness and must not continue accepting reservations.

This reduces availability during a database outage, but avoids split-brain sales.
Application instances are otherwise stateless and can be scaled horizontally as
long as they share the same database and JWT configuration.

## Security

Authentication uses short-lived RS256 JWTs. The token subject contains the database
user ID, and roles are taken from signed claims. Passwords are stored only as BCrypt
hashes. Public registration always creates a normal user; only an authenticated
administrator can create another administrator or create a show.

The first administrator is a deployment-provisioning concern and is not inserted
by Flyway. Production credentials and signing keys must come from a secret manager,
not from source-controlled migrations or container layers.

## Observability

The service exposes separate liveness and readiness endpoints. Readiness includes a
database check; liveness reports only the application's internal state.

Prometheus metrics include:

- confirmed reservations;
- declined reservations tagged with `seat_taken` or `per_user_limit`;
- idempotent replays;
- a database-backed gauge of currently available seats.

Confirmed and replay counters are incremented only after transaction commit. The
available-seat gauge returns `NaN` if its database query fails instead of reporting
a misleading value.

Console logs use structured JSON. Every request receives or accepts a safe
`X-Request-ID`, returns it to the caller, and logs the request method, path, response
status, and duration without logging authorization headers or request bodies.

At 2 a.m. I would alert on readiness failures, sustained 5xx responses, database
connection-pool exhaustion, increasing lock-wait latency, and reconciliation
failures. I would dashboard throughput, latency percentiles, confirmation and
decline rates, idempotent replays, available seats, and PostgreSQL health. A high
seat-taken decline rate during an on-sale event may be expected; 5xx responses or a
reconciliation mismatch are not.

## Schema and deployment

Flyway owns schema evolution and Hibernate only validates mappings. Migrations are
written to be safe when executed repeatedly. The application has a multi-stage
Dockerfile based on Java 25 and runs as a non-root user. The image contains only the
main application code and runtime dependencies.

## AI usage

I used Codex as a collaborative implementation and review tool. I directed the
product decisions: explicit cancellation, authenticated identity, production-grade
behavior, incremental changes, idempotent migrations, and the rule that no Java
tests should be added to test folders.

Codex helped trace the existing code, identify concurrency risks, propose small
implementation steps, edit the selected files, compile the application, shape the
error responses, and add security, metrics, structured logging, Flyway, Docker, and
documentation. It selected some concrete implementation details, including MDC log
fields, metric names, migration constraints and indexes, and the multi-stage image
layout. I reviewed the decisions and kept responsibility for understanding and
explaining the resulting behavior.

## What I would do next

The remaining delivery work is:

1. provision the first administrator through a secure deployment operation;
2. deploy the container and PostgreSQL to a public environment;
3. run the included burst client at progressively higher load against the live URL;
4. tune the database pool, transaction and lock timeouts from measured burst data;
5. map expected lock-timeout and serialization failures to clean domain responses;
6. add dashboards and alert rules, including an independent reconciliation check;
7. add signing-key rotation, login rate limiting, and operational credential
   rotation.

The local application has compiled and packaged successfully. Public deployment and
the 20,000-request concurrency verification are not yet complete, so the live-load
correctness and zero-5xx target are not claimed here.
