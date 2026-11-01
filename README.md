# Seat Reservation

## Create a user

With the application running, send `POST http://localhost:8080/web/v1/user/create`
with `Content-Type: application/json`:

```json
{"username": "mitesh", "password": "example-password-123"}
```

Success returns HTTP 201:

```json
{"id": 1, "username": "mitesh", "role": "USER"}
```

Usernames are trimmed, case-sensitive, and limited to 255 characters. Missing or
blank usernames return 400; an existing username returns 409. The database unique
constraint also protects against simultaneous requests for the same username.
The endpoint is public and always assigns the USER role. After creation, use the
login endpoint below to obtain an authentication token.

Passwords are required, must contain at least 8 characters and at most 72 UTF-8
bytes, and are preserved exactly (not trimmed). Only a salted BCrypt hash is
stored in `users.password_hash`. Passwords and hashes are excluded from API
responses and object string representations.

## JWT authentication

Generate development RSA keys once using PowerShell 7:

```powershell
./scripts/New-LocalJwtKeys.ps1
```

The script creates a 3072-bit RSA key pair under the Git-ignored `.local/jwt/`
directory and prints two environment variable assignments. Add both to the same
IntelliJ run configuration as `DB_PASSWORD`:

| Environment variable | Value |
| --- | --- |
| `JWT_PRIVATE_KEY` | `file:///C:/absolute/path/to/private.pem` (PKCS#8 PEM) |
| `JWT_PUBLIC_KEY` | `file:///C:/absolute/path/to/public.pem` (X.509 PEM) |
| `JWT_ISSUER` | Defaults to `seat-reservation` |
| `JWT_AUDIENCE` | Defaults to `seat-reservation-api` |
| `JWT_ACCESS_TOKEN_TTL` | Defaults to `15m`; allowed range: 1 second to 1 hour |

Keys must be externally provisioned and identical on every application instance.
The application fails startup for missing, mismatched, or undersized keys; it
never creates a new signing key automatically. In production, mount keys from a
secret manager, restrict private-key access, and use HTTPS. The local script is
for development. Key rotation currently requires coordinated configuration
changes; replacing the pair invalidates existing tokens.

Send `POST /web/v1/auth/login` with:

```json
{"username": "mitesh", "password": "example-password-123"}
```

Success returns HTTP 200 with cache prevention headers:

```json
{"access_token": "<signed JWT>", "token_type": "Bearer", "expires_in": 900}
```

Incorrect credentials return 401 with a generic failure. Missing or malformed
credentials return 400. JWTs contain the database user ID in `sub`, roles, issuer,
audience, issue time, and expiry. They contain no password or password hash.

Supply `Authorization: Bearer <access_token>` on protected requests. User creation
and login are public; `POST /shows` requires ADMIN; other routes require a valid
token. HTTP Basic and form login are disabled. Public registration cannot assign
ADMIN; administrator provisioning is a separate operation.

The API uses stateless Spring Security authentication: no login sessions, no
sticky routing, and no per-request user database lookup for token validation.
CSRF protection is disabled because authentication uses explicitly supplied
Authorization headers instead of browser-managed session cookies. If cookie
authentication is added, revisit CSRF protection. Controllers can obtain the
authenticated user ID from `@AuthenticationPrincipal Jwt jwt` using
`jwt.getSubject()`; never accept the acting user ID from a request body.

Tokens expire after 15 minutes by default. The JWT timestamp validator allows
Spring Security's default 60-second clock skew. Logout is client-side token
discard; immediate revocation, refresh tokens, login rate limiting, and immediate
role-change propagation are not implemented. Existing tokens retain their roles
until expiration. These controls must be addressed before public production use.

Run the focused HTTP tests (including the security filter and service, with a
mocked repository) using:

```powershell
./mvnw.cmd '-Dtest=UserControllerTests,JwtAuthenticationTests' test
```

The JWT tests generate temporary test-only keys and cover login, identity and
role spoofing, token tampering, expiry, issuer/audience validation, access rules,
statelessness, and verification by an independently configured second instance.

## Local PostgreSQL connection

Prerequisites: JDK 25 and a running PostgreSQL installation.

1. In pgAdmin, connect to your local PostgreSQL server using the password chosen
   during installation.
2. Under Databases, create a database named `seat_reservation`. Alternatively,
   run this SQL from the Query Tool while connected to the `postgres` database:

   ```sql
   CREATE DATABASE seat_reservation;
   ```

3. In IntelliJ, open Run > Edit Configurations, select the application run
   configuration, and add an environment variable named `DB_PASSWORD` with your
   PostgreSQL password. If the environment variable field is hidden, enable it
   under Modify options. Keep credentials in a local, unshared run configuration.
4. Run `SeatReservationApplication`.

The connection uses these settings:

| Environment variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/seat_reservation` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Required; no password stored in the project |

If you installed PostgreSQL on a different port or use another database user,
set `DB_URL` or `DB_USERNAME` in the same run configuration.

For a terminal run, set the same environment variables in your shell, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Spring Boot does not automatically load a `.env` file. Supply the variables
through IntelliJ, the shell, or the deployment platform.

A successful startup should include Hikari connection-pool startup and
`Started SeatReservationApplication`. A password authentication failure means
the supplied username/password needs correcting; a missing database error means
the database creation step has not been completed.

The current local setting is `spring.jpa.hibernate.ddl-auto=create`: restarting
the application drops and recreates the entity tables, including the new password
column. Use migrations and `validate` when moving to production.
