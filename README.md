# Hotel Reservation Service

A small Java 17 backend for hotel room reservations: availability, dynamic pricing, a reservation life cycle,
cancellation refunds, loyalty points, a REST API and persistence in an embedded H2 database (plain files in a folder).
Developed as the system under test for the SEN4013 Software Verification and Validation course project.
AI tool usage is documented in the AI Declaration of the project report.

## Build and run

Requires JDK 17+ and Maven 3.9+.

```bash
mvn test                 # unit tests + JaCoCo report (target/site/jacoco/index.html)
mvn package              # builds a runnable jar that includes the H2 driver
java -jar target/hotel-reservation-service-1.0.0.jar                 # port 8080, data in ./data
java -jar target/hotel-reservation-service-1.0.0.jar 8081 /tmp/hotel # other port and data folder
java -jar target/hotel-reservation-service-1.0.0.jar 8080 memory     # no persistence
# mutation testing
mvn test-compile org.pitest:pitest-maven:mutationCoverage
```

Data is stored in H2 database files (`hotel.mv.db`) inside the data folder (default `./data`, created on start).
Delete the folder to reset everything. Rooms are fixed in code: 101-104 SINGLE, 201-206 DOUBLE, 301-302 SUITE.

For tests you can use `ReservationService.inMemory(...)` (no database) or `Database.inMemory("name")` with the JDBC
repositories (real SQL, no files).

## Requirements (specification used for verification)

Currency amounts are `BigDecimal`, scale 2, rounding HALF_UP. A "night" is identified by the date it starts on.

### Guests
- **G-01** Name is required, at most 50 characters after trimming. Otherwise `INVALID_NAME`.
- **G-02** Email must match `local@domain.tld` (letters, digits, `._%+-` locally; TLD of 2+ letters). Otherwise `INVALID_EMAIL`.
- **G-03** Email is stored trimmed and lower-case and must be unique ignoring case. Otherwise `DUPLICATE_EMAIL`.
- **G-04** Loyalty tier is derived from points: BRONZE 0+, SILVER 500+, GOLD 1500+, PLATINUM 4000+.

### Pricing (P)
- **P-01** Base nightly rates: SINGLE 80.00, DOUBLE 120.00, SUITE 250.00.
- **P-02** Season factor per night: PEAK (20 Dec - 5 Jan inclusive) x1.50; HIGH (15 Jun - 15 Sep inclusive) x1.30;
  LOW (1 Nov - 15 Dec and 6 Jan - 15 Mar inclusive) x0.90; otherwise x1.00.
- **P-03** Friday and Saturday nights are multiplied by a further 1.15. Each night's rate is rounded to 2 decimals before summing.
- **P-04** Capacity: SINGLE 1, DOUBLE 3, SUITE 4 guests. Fewer than 1 or more than capacity is `INVALID_GUESTS`.
- **P-05** Two guests are included; each additional guest costs 20.00 per night.
- **P-06** Length-of-stay discount on the subtotal (room charge + extra guest charge): 3-6 nights 5%, 7-13 nights 10%, 14+ nights 15%.
- **P-07** Loyalty discount on the subtotal: SILVER 3%, GOLD 5%, PLATINUM 8%.
- **P-08** Promo codes (case-insensitive, trimmed; blank means none; unusable codes raise `INVALID_PROMO`):
  - `WELCOME10`: 10% of subtotal, only if the guest has no completed stays.
  - `SUMMER20`: 20% of subtotal, only if the stay has at least 3 nights and all nights fall between 1 Jun and 31 Aug of one year.
  - `FLAT50`: 50.00 off, only if the subtotal is at least 200.00.
- **P-09** Percentage discounts are added together (not compounded) and then the flat amount is added.
- **P-10** The total discount never exceeds 25% of the subtotal.
- **P-11** VAT is 10% of (subtotal - discount). A tourism fee of 1.50 per guest per night is added; it is neither taxed nor discounted.
- **P-12** Total = subtotal - discount + VAT + tourism fee. A quote without a guest id uses the BRONZE tier and treats the guest as not first-time (so `WELCOME10` is rejected).

### Reservations (R)
- **R-01** Check-in must be before check-out, else `INVALID_DATES`.
- **R-02** Check-in must not be before today, else `PAST_DATE`.
- **R-03** At most 30 nights, else `STAY_TOO_LONG`.
- **R-04** Check-in at most 365 days after today, else `TOO_FAR_IN_ADVANCE`.
- **R-05** A reservation gets the lowest-numbered free room of the requested type; if none, `NO_AVAILABILITY`.
- **R-06** A room is occupied during nights [check-in, check-out). Reservations in status PENDING, CONFIRMED or CHECKED_IN occupy their room; a stay may start on the day another ends.
- **R-07** A new reservation is PENDING and expires 30 minutes after creation.
- **R-08** `confirm` only from PENDING. If the 30 minutes have passed the reservation becomes EXPIRED and `INVALID_STATE` is raised.
- **R-09** `expireStalePending` moves every stale PENDING reservation to EXPIRED and returns how many.
- **R-10** `checkIn` only from CONFIRMED, from the check-in date up to (not including) the check-out date.
- **R-11** `checkOut` only from CHECKED_IN. The guest earns floor(total / 10) points and one completed stay.
- **R-12** `noShow` only from CONFIRMED and only after the check-in date; refund is 0.00.
- **R-13** `changeDates` only from PENDING or CONFIRMED. Same validation as creation; the same room must be free (ignoring the reservation itself); the stay is re-priced with the same promo code and `priceAdjustment` = new total - old total.

### Cancellation (C)
- **C-01** Only PENDING or CONFIRMED reservations can be cancelled, else `INVALID_STATE`.
- **C-02** A PENDING reservation (nothing paid) is cancelled with refund 0.00.
- **C-03** For CONFIRMED reservations, with d = days between today and check-in: d >= 7 refunds 100%, 3 <= d <= 6 refunds 50%, 1 <= d <= 2 refunds 25%, d <= 0 refunds 0%.
- **C-04** PLATINUM guests get a 100% refund whenever d >= 3.

### Persistence (D)
- **D-01** Guests and reservations survive a restart when the file database is used.
- **D-02** The email column is unique in the database as well; a violation is reported as `DUPLICATE_EMAIL`.
- **D-03** Every state-changing use case is one transaction: if it fails, none of its changes remain. `checkOut`
  (reservation status, guest points and completed stays) is the multi-table example.
- **D-04** The schema enforces `check_in < check_out`, `guests >= 1`, non-negative points, valid status values and
  a foreign key from reservations to guests (see `src/main/resources/schema.sql`).
- **D-05** Ids are generated from database sequences (`G-n`, `R-n`) and are not reused after a rollback.
- **D-06** Timestamps are stored with millisecond precision.

## REST API

Parameters may be sent in the query string or as `application/x-www-form-urlencoded` POST body. Dates are `yyyy-MM-dd`.

| Method and path | Parameters | Success |
|---|---|---|
| `POST /guests` | name, email | 201 guest |
| `GET /guests/{id}` | | 200 guest |
| `GET /rooms/available` | type, checkIn, checkOut | 200 list of rooms |
| `GET /quote` | type, checkIn, checkOut, guests, [guestId], [promo] | 200 quote |
| `POST /reservations` | guestId, type, checkIn, checkOut, guests, [promo] | 201 reservation |
| `GET /reservations/{id}` | | 200 reservation |
| `POST /reservations/{id}/confirm` `/checkin` `/checkout` `/cancel` `/noshow` | | 200 reservation |
| `POST /reservations/{id}/dates` | newCheckIn, newCheckOut | 200 reservation |

Errors are `{"error": CODE, "message": "..."}`: 404 for `GUEST_NOT_FOUND` and `RESERVATION_NOT_FOUND`; 409 for
`NO_AVAILABILITY`, `INVALID_STATE`, `DUPLICATE_EMAIL`; 400 for other business errors and for missing or malformed
parameters (`BAD_REQUEST`); 404 `NOT_FOUND` for unknown routes.

## Example

```bash
curl -X POST localhost:8080/guests -d "name=Ada Lovelace&email=ada@example.com"
curl "localhost:8080/quote?type=DOUBLE&checkIn=2027-03-20&checkOut=2027-03-24&guests=2&guestId=G-1&promo=WELCOME10"
curl -X POST localhost:8080/reservations -d "guestId=G-1&type=DOUBLE&checkIn=2027-03-20&checkOut=2027-03-24&guests=2"
curl -X POST localhost:8080/reservations/R-1/confirm
```

## Source layout

- `model/`: Guest, Room, Reservation, Quote, enums, `BookingException`
- `service/PricingService`: all pricing rules (P-*)
- `service/ReservationService`: use cases (G-*, R-*, C-*); depends only on the repository interfaces
- `repository/`: `GuestRepository`, `ReservationRepository`, `TransactionRunner` and the in-memory implementations
- `repository/jdbc/`: `Database` (connections, transactions, schema), `JdbcGuestRepository`, `JdbcReservationRepository`
- `api/`: `ApiServer` (JDK `HttpServer`) and a minimal JSON writer
- `src/main/resources/schema.sql`: tables, constraints, indexes, sequences
