## Initial state:

Server (server-java) — Spring Boot 3.2, Hibernate ddl-auto=update (no migration tool, so schema changes are just entity field additions).
- Event: id, title, start, duration — no capacity, no pricePerPerson
- Booking: id, firstName, lastName, event — no participantCount, customerEmail, notes, status, and no waitlist concept
- EventController: only GETs (list, by id, by date). No create/update/delete.
- BookingController: only GET /bookings/event/{id}. POST is a stubbed TODO.
- BookingService.createBooking: exists but takes just firstName/lastName — no capacity check, no waitlist logic, no cancel, no promotion.
- One test file (EventServiceTest), nothing for bookings.

Client (client-angular) — Angular 17 standalone.
- Event/Booking models mirror the thin backend models (no capacity/participantCount/status fields).
- CalendarComponent: renders a day view, but onEventClick is a TODO stub — no booking form, no availability indicator, no booking management view, no dashboard route. Routing only has '' → calendar.
- EventService (Angular): no createBooking method yet.

Difficulty read, easiest/most-foundational → hardest

1. Data model additions (capacity, pricePerPerson on Event; participantCount, customerEmail, notes, status on Booking) — trivial, no migrations needed since ddl-auto=update just adds columns.
2. Capacity-aware booking creation (POST endpoint + DTO + confirm-vs-waitlist logic in BookingService) — the core rule (currentBookings + participantCount ≤ capacity) is simple arithmetic; this is the highest-leverage feature since #2 (availability), #4 (waitlist) both depend on it existing.
3. Calendar availability indicator (green/yellow/red) — easy once the Event API exposes occupancy; mostly a template/CSS change.
4. Booking form UI (participant count, remaining seats, confirmed/waitlisted feedback) — small modal/component, depends on #2.
5. Cancel + waitlist promotion — a bit more logic (find oldest waitlisted, check fit, promote) but self-contained in BookingService.
6. Booking management view (new route/component, table with confirmed + waitlisted sections, cancel action) — more UI surface area, depends on #2 and #5.
7. Daily dashboard — needs a new aggregation endpoint (events + bookings for today, summed) and a new view; straightforward but the most "extra" piece, and lowest urgency relative to the overbooking-prevention story.


*First Decision After first run of Phase 1-3*:
- The local Postgres 'peek_challenge' DB already has 300 seeded events (0 real bookings) from a prior schema, without the new capacity/pricePerPerson columns. Starting the server with NOT NULL columns added via Hibernate's ddl-auto=update will fail against those existing rows.
    - Choices can be:
    1. to Drop & reseed with new schema (capacity, pricePerPerson, sample bookings). Since all current data is just disposable seed fixtures.
    2. Dont touch DB, Assume test and compilation will pass, then run/reseeed manually.
    - *EDIT*: I've chosen Drop & reseed, since is new data needed.
- Added DEFAULT_CAPACITY and DEFAULT_PRICE to DataSeeder, as well with some bookings to test those limited/waitlist scenarios.


## Phase 1 implementation: data model, capacity-aware booking creation, calendar indicator

### What was built

**Backend**
- `Event` gained `capacity` (`Integer`, `@NotNull @Positive`) and `pricePerPerson` (`BigDecimal`, `@NotNull @DecimalMin("0.0")`, `precision=10, scale=2`).
- `Booking` gained `participantCount` (`Integer`, `@Min(1)`), `customerEmail` (`@Email @NotBlank`), `notes` (nullable, free text), and `status` (`BookingStatus` enum: `CONFIRMED | WAITLISTED | CANCELLED`, defaults to `CONFIRMED`).
- Removed `Event.getBookingCount()` — it counted bookings of *any* status (including cancelled), which is misleading, was unused anywhere in the codebase, and is superseded by the new confirmed-only occupancy calculation.
- New `EventResponse` DTO (`dto/EventResponse.java`) computes `currentBookings`, `availableSeats`, and `availabilityStatus` (`AVAILABLE | LIMITED | FULL`) per the PRD thresholds (>50% remaining / 1–50% / 0%). `EventController`'s three GET endpoints now return this DTO instead of the raw entity.
- New `CreateBookingRequest` / `BookingResponse` DTOs. `POST /api/bookings` validates the request, computes `SUM(participantCount) WHERE status = CONFIRMED` for the event, and confirms the booking if `confirmed + participantCount <= capacity`, otherwise waitlists it. The response always includes a human-readable `message` so the outcome is unambiguous to a caller.
- `BookingRepository` gained two aggregate queries: `sumParticipantsByEventIdAndStatus` (single event, used by booking creation) and `sumParticipantsByEventIdsAndStatus` (batched `GROUP BY`, used by `EventService` when returning lists of events) — see complexity note below.
- `DataSeeder` now sets `capacity`/`pricePerPerson` (required by the new NOT NULL columns) and additionally seeds 3 sample bookings across today's first three events so the app shows one `AVAILABLE`, one `LIMITED`, and one `FULL` (+1 `WAITLISTED`) event out of the box.

**Frontend**
- `Event`/`Booking`/`CreateBookingRequest` models updated to match the new API shape; added `BookingResponse` and the `AvailabilityStatus`/`BookingStatus` string union types.
- Calendar event tiles now render a colored left border + rounded badge (green `AVAILABLE`, amber `LIMITED`, red `FULL`) and an "X / Y booked" line, driven directly by the fields the API already returns — no client-side computation of availability, so the client and server can never disagree on the status.

### Decisions, with pros & cons

1. **Expose response DTOs instead of serializing JPA entities directly.**
   - Pros: decouples the API contract from persistence details; avoids accidentally serializing the lazy `bookings` collection or triggering N+1 lazy-loads during Jackson serialization; lets the availability calculation live in one small, unit-testable static method (`EventResponse.from`).
   - Cons: an extra mapping layer to keep in sync when either the entity or the API contract changes; two parallel shapes to reason about.

2. **Compute occupancy with a SQL aggregate query, batched for list endpoints, rather than iterating `Event.bookings` in memory.**
   - Pros: no risk of loading full booking collections per event; a single `GROUP BY` query answers occupancy for an entire list of events.
   - Cons: adds a `BookingRepository` dependency to `EventService`, coupling the two domains a bit more tightly than a "pure" layered design would prefer.
   - **Complexity:** listing `N` events now costs **1 round-trip** for the events plus **1 aggregate query** returning at most `N` rows (`O(1)` round-trips, `O(N)` rows) — versus the naive per-event approach of `N` separate `SUM` queries (`O(N)` round-trips). Fetching a single event by id costs 1 extra `O(1)` query for its own sum.

3. **Recompute the confirmed-seat sum from the DB on every booking write, instead of keeping a denormalized counter on `Event`.**
   - Pros: always correct relative to the current row-level state; nothing can drift out of sync when bookings are later cancelled/promoted (relevant for the next phase).
   - Cons: **known race condition** — two concurrent `POST /api/bookings` for the same event can both read "N seats left" before either commits, and both get confirmed, breaking the "0 overbooking incidents" success metric under real concurrent load. Not fixed in this phase (see Trade-offs).
   - **Complexity:** `O(1)` additional indexed query (`event_id` is a FK, so the `WHERE` clause is index-backed) per booking write.

4. **Availability thresholds implemented as a pure function, not stored on the entity.**
   - Pros: always reflects live data (PRD explicitly requires this, "not a static field"); trivially unit-testable without a DB.
   - Cons: none identified — this is strictly better than a stored/cached field here given the freshness requirement.

### Assumptions
- Kept `firstName`/`lastName` on `Booking` alongside the new `customerEmail` — the PRD's data model notes only call out `customerEmail` as new, but the booking management view AC requires a "guest name" column, so the original name fields are retained rather than replaced.
- `Event` create/update/delete endpoints remain out of scope (still TODO stubs) — not required by any in-scope PRD item for this phase; the seeder is the only writer of events for now.
- Waitlist ordering will use the existing `createdAt` timestamp once promotion/display logic is built (next phase) — no separate "position" column needed.

### Trade-offs / known limitations (would revisit with more time)
- **No concurrency control on booking creation.** The safe fix is either a `SELECT ... FOR UPDATE` on the event row inside the transaction, or a DB-level check constraint/trigger enforcing the capacity invariant. Skipped to stay within scope of "first 3 items"; flagging it explicitly since it directly threatens the PRD's #1 success metric (zero overbooking) under concurrent traffic.
- **No migration tool.** The repo relies entirely on `ddl-auto=update`; there's no Flyway/Liquibase to express "add NOT NULL column with backfill" against real data. Handled here by dropping and reseeding the local dev DB (300 disposable seed rows, 0 real bookings, confirmed with the user first) — would not be viable against a real dataset.
- **`GET /api/bookings/event/{id}` still returns raw `Booking` entities**, not a `BookingResponse` — inconsistent with the new POST endpoint. Left alone since it wasn't in scope for this phase; should be unified when the booking management view is built.
- **No Angular booking form / client `createBooking` method yet.** The POST endpoint was validated via curl only; wiring it into the UI is next.

### How it was tested
- `mvn test`: all 4 tests pass (H2 in-memory DB, `test` profile).
- `ng build`: compiles cleanly with the updated models and template bindings.
- Manual end-to-end check against local Postgres with seeded data (via curl): confirmed `AVAILABLE`/`LIMITED`/`FULL` compute correctly for the three seeded states; `POST /api/bookings` returns `CONFIRMED` when capacity allows and `WAITLISTED` (with a clear `message`) when it doesn't; malformed requests return `400`.
- Could not capture an actual browser screenshot in this sandboxed environment (no display available) — relied on API-level verification plus a clean Angular build instead of a visual check.
