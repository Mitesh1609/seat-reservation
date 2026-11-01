# Seat Reservation

Spring Boot project foundation with PostgreSQL configuration, domain entities, and booking DTOs.

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
