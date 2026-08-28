# Future Plan — PRD Audit & Remediation Backlog

**Audited:** 2026-08-28 · **Commit:** `419b4a9` (develop) · **Scope:** `docs/PRD.md` vs. `client-angular` + `server-java`

## How this was produced

Six parallel auditors were run over the codebase — one per PRD feature area, plus dedicated backend-bug, frontend-bug, and testing/ops lenses — and every finding was then handed to an independent verifier instructed to *refute* it. 57 findings survived; 43 were confirmed as written and 14 were corrected (severity or mechanism restated) by the verification pass. Several were proven by mutation testing (deliberately breaking the production code and confirming the suite still passed) or by empirical repro rather than by reading alone. The date bug and the production-build bug in P0 below were additionally reproduced by hand.

**Limits of this audit:** no browser/click-through testing (no display in this environment), no load or concurrency testing against a real database, and no review of the deployment target. The concurrency finding is a code-level race, not an observed production incident.

---

## Verdict

**Every acceptance criterion in the PRD's five in-scope feature sections is implemented.** Sections 1–5 (Event Capacity, Bookings with Participant Count, Booking Management View, Waitlist, Daily Dashboard) all ship working, and the two "missing feature" findings both map to PRD *Open Questions* — which are explicitly unanswered questions, not requirements.

What remains is **correctness, robustness, and operational readiness**, not feature work. Four issues are serious enough to block a real deployment:

| # | Issue | Impact |
|---|---|---|
| 1 | Calendar computes dates in UTC | Wrong day's events shown to most of the world, daily |
| 2 | Unbounded `participantCount` | One request permanently 500s the calendar *and* dashboard |
| 3 | No locking on booking writes | Overbooking — the PRD's #1 success metric is "zero overbooking" |
| 4 | Production build hardcodes `localhost:8080` | App cannot talk to any non-local backend |

### PRD compliance matrix

| PRD section | Status | Notes |
|---|---|---|
| 1. Event Capacity | ✅ Implemented | Thresholds correct; **but** the calendar can show the wrong *day* (P0-1) |
| 2. Bookings w/ Participant Count | ✅ Implemented | **but** no upper bound on `participantCount` (P0-2), `notes` capped at 255 (P1-1) |
| 3. Booking Management View | ✅ Implemented | All criteria met, incl. cancel + visual distinction |
| 4. Waitlist | ✅ Implemented | Promotion algorithm verified correct by test; ordering has no tiebreaker (P2) |
| 5. Daily Dashboard | ✅ Implemented | **but** server-side "today" can disagree with the calendar's (P1-4) |
| Goal 2 "prevent overbooking at the system level" | ⚠️ **Not enforced** | No lock, no `@Version`, no DB constraint (P0-3) |
| Open Q1 (waitlist cap) | Answered | Unlimited, per PRD's own assumption |
| Open Q2 (partial fits) | Answered | Skip-and-try-next, implemented + tested |
| Open Q3 (reduce capacity below bookings) | ⬜ Unanswered | No event-update path exists; `updateEvent` silently drops `capacity` |
| Open Q4 (revenue estimate) | ⬜ Unanswered | `pricePerPerson` shown only on the booking form |

---

## P0 — Ship blockers

### P0-1 · Calendar sends UTC dates, so the wrong day loads
**`client-angular/src/app/components/calendar/calendar.component.ts:78`**

`formatDate()` is `date.toISOString().split('T')[0]` — that converts a *local* Date to a *UTC* calendar date. It is the sole producer of the `yyyy-MM-dd` used for `GET /api/events/date/{date}` (line 44) and for the `?date=` param passed to the manage/book views (line 117). Meanwhile the header renders `toLocaleDateString()` (local), and the return path parses `new Date(param + 'T00:00:00')` (local midnight). **The write path is UTC and the read path is local — they are not inverses.**

Reproduced on this machine (`America/Monterrey`, UTC−6) and under `TZ=Asia/Tokyo`:

```
UTC−6, local 18:00+  → header "Friday, August 28"  but fetches 2026-08-29   ← wrong day's events + availability
UTC+9, any time      → ?date=2026-08-29 round-trips back as 2026-08-28      ← day shifts backward on return
```

Every operator west of UTC hits this daily after ~17:00–18:00 local; every operator east of UTC hits the round-trip shift at any hour. Nothing surfaces an error — the badges just describe the wrong day.

**Fix (S):** build the string from local components (`getFullYear()`/`getMonth()+1`/`getDate()`, zero-padded) so it is the exact inverse of the parse. Better: stop holding a `Date` for a calendar day at all — keep `yyyy-MM-dd` as the source of truth and derive a `Date` only for display. Add a unit test pinning the round-trip under a fixed non-UTC offset.

### P0-2 · Unbounded `participantCount` overflows the capacity check and bricks every occupancy read
**`dto/CreateBookingRequest.java:23`, `service/BookingService.java:97`, `repository/BookingRepository.java:21`, `repository/EventOccupancy.java:5`**

`participantCount` is validated with `@NotNull @Min(1)` and **no upper bound** — client or server. The capacity check is 32-bit: `confirmedParticipants + request.participantCount() <= event.getCapacity()`. The occupancy aggregates narrow SQL's `SUM` (a `bigint`) into `int` / `Integer`.

`POST /api/bookings` with `participantCount: 2147483647` therefore:
1. overflows the check to a negative number, so it passes → booking saved **CONFIRMED**, overbooked, with no concurrency involved;
2. then makes `GET /api/events`, `/api/events/{id}`, `/api/events/date/{d}` **and** `/api/dashboard` all throw on the `Long`→`int` conversion → **HTTP 500**.

The calendar and dashboard are dead from that moment, and the management view can't load either (its `forkJoin` includes `getEventById`) — **so there is no UI path left to cancel the poisoning booking.** It has to be removed in SQL. This is a one-request denial of service on unauthenticated input.

**Fix (S):** add `@Max` to the DTO and entity (a sane ceiling, e.g. 1000), do the comparison in `long` arithmetic, and widen the aggregates to `long`/`Long` so no single row can make a read endpoint throw. Mirror the max in the Angular form.

### P0-3 · No concurrency control on any booking write path
**`service/BookingService.java:92-109` (create) and `:62-83` (promote)** · *already documented in NOTES.md as an accepted trade-off*

`createBooking` does an unguarded read-then-write: read the confirmed sum → compare to capacity → save. There is no `@Lock` on the event lookup, no `@Version` on `Event`, and no DB constraint (`ddl-auto=update` emits none). Under Postgres' default READ COMMITTED, two concurrent transactions both see the pre-insert sum.

> Capacity 12, 10 confirmed. Two `participantCount=2` requests (or one double-click) arrive together. Both read 10, both evaluate `10+2 <= 12`, both insert CONFIRMED → **14/12**. `EventResponse.from` clamps `availableSeats` to 0 and reports FULL, so the overbooking is *silently absorbed* rather than surfaced.

`promoteWaitlist` has the same shape. This directly contradicts Goal 2 and the "overbooking incidents drop to 0" success metric.

**Fix (M):** take a `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the event row inside both `createBooking` and `cancelBooking` so all capacity arithmetic for one event serializes — or add `@Version` + retry. A DB-level check constraint is the belt-and-braces backstop. Add a regression test running two `createBooking` calls on separate transactions asserting the confirmed sum never exceeds capacity.

### P0-4 · Production build hardcodes `http://localhost:8080`
**`client-angular/angular.json` (production config), `src/environments/environment.prod.ts`**

Two compounding bugs, both verified:
1. `angular.json`'s `production` configuration has **no `fileReplacements`** entry — `environment.prod.ts` is never substituted. Grepping the repo for `environment.prod` returns zero references: it is dead code that actively misleads.
2. Even if it were wired, `environment.prod.ts` *also* says `apiUrl: 'http://localhost:8080'`.

Anyone who edits `environment.prod.ts` and runs `ng build` ships a bundle that still calls `localhost:8080` — every request fails outside the developer's laptop.

**Fix (S):** add the `fileReplacements` mapping and point the prod `apiUrl` at a relative base (e.g. `''`, so calls go to `/api/...` behind a reverse proxy). Or delete `environment.prod.ts` so nothing implies it works.

---

## P1 — Correctness & robustness

**P1-1 · `notes` longer than 255 chars → 500, booking lost (S).** `@Column(name="notes")` has no `length`, so Hibernate maps `varchar(255)`; the DTO has no `@Size` and the textarea has no `maxlength`. A pasted 300-char dietary note throws `DataIntegrityViolationException` → the form shows the generic "Something went wrong" and no booking is created. Enforce one limit at all three layers (note: `ddl-auto=update` will not widen an existing column — see P2 migrations).

**P1-2 · Events starting at exactly 00:00 are invisible (S).** `EventRepository.java:15` uses `e.start > :startDate` (strict) while `EventService.getEventsByDate` passes `date.atStartOfDay()`. A midnight tour never appears in the day view, is excluded from every dashboard aggregate, and can't be reached at all. Change to `>=` (half-open, which is what the caller already assumes given it passes next-day-midnight as the exclusive upper bound) + boundary test.

**P1-3 · Seeder produces an empty "today" on weekends (S).** `startDate.plusWeeks(week).with(day)` sets the day *within the current ISO week*, so it can move backwards — seeding on a Saturday puts all of week 0 in the past and nothing on the seed date. `seedSampleBookings` then bails (`if (todaysEvents.size() < 3) return;`), so **zero bookings** are created anywhere. A reviewer cloning the repo on a weekend sees an empty calendar, an all-zero dashboard, and no demo data for any feature. Use a forward-only adjuster (`nextOrSame`) or anchor sample bookings to the first three *upcoming* events.

**P1-4 · Dashboard's "today" is the JVM's, not the operator's (M).** `DashboardService.java:27` calls `LocalDate.now()` with no zone; the shipped `Dockerfile` sets no `TZ`, so the container is UTC. An operator in UTC+9 opening the dashboard at 08:00 local gets *yesterday's* briefing — for the whole morning, which is exactly the window the feature exists for. Combined with P0-1, the calendar and dashboard can disagree about what day it is. Introduce a configurable `app.timezone` + injected `Clock` (also makes it testable), and/or accept an optional `?date=`.

**P1-5 · Calendar day-nav has no request cancellation (S).** `loadEvents()` subscribes directly with no `switchMap` and no unsubscribe; clicking Next twice quickly leaves two requests in flight and **last-response-wins**, so the header can say Aug 30 while the grid shows Aug 29. Route loads through a `Subject` + `switchMap`, or use a sequence token.

**P1-6 · Calendar tiles and dashboard rows are keyboard-inoperable (S).** Both are `<div (click)>` with no `tabindex`, `role`, or keydown handling — and they are the *only* path to the management view, and therefore to the Cancel action and the booking form. A keyboard-only operator cannot reach any of it. Make them real `<button>`s (or add `role`/`tabindex`/keydown + `aria-label`).

---

## P2 — Test & CI gaps

The backend has 15 passing tests; **the frontend has zero**. Three gaps were proven by mutation — the production code was deliberately broken and the suite still went green:

| Mutation applied | Result |
|---|---|
| `remainingRatio > 0.5` → `>= 0.5` | ✅ all 15 pass — the AVAILABLE/LIMITED boundary is untested |
| Reverse waitlist promotion order | ✅ all 15 pass — existing tests can't distinguish order |
| `getEventsByDate(today)` → `getAllEvents()` | ✅ all 15 pass — dashboard's "today" scoping is untested |

- **P2-1 (S)** Pin the availability thresholds with a parameterized test over the pure static `EventResponse.from` — capacity 10 at 0/4/5/9/10 booked → AVAILABLE/AVAILABLE/LIMITED/LIMITED/FULL. No DB needed.
- **P2-2 (S)** Pin waitlist ordering: two *same-size* waitlisted bookings where only one fits, asserting by id that the earlier one is promoted.
- **P2-3 (S)** Pin multi-promotion: capacity 10, waitlist 4/9/5/1, cancel the 10 → expect promote/skip/promote/promote landing at exactly 10/10, never over.
- **P2-4 (S)** Pin dashboard date-scoping: add a tomorrow event and assert it contributes to nothing.
- **P2-5 (M)** **No HTTP-layer tests exist at all** — no MockMvc, no `@WebMvcTest`. Status codes, `@Valid` enforcement, and the JSON contract the Angular client depends on are entirely unverified. Drop `@Valid` from the controller and nothing fails, but the client's `err.status === 400` branch silently starts showing the wrong message. Add one `@AutoConfigureMockMvc` class covering 201-confirmed / 201-waitlisted / 400-invalid / 404-unknown-event / cancel.
- **P2-6 (M)** **Zero frontend tests**, and `ng test` currently *fails* rather than reporting an empty suite (karma is wired but no spec matches). Start with the pure logic: `formatDate` round-trip (would have caught P0-1), the booking form's custom validators, and a `BookingManagementComponent` spec for the confirmed/cancelled vs. waitlisted split and the cancel→refresh flow.
- **P2-7 (S)** **No CI of any kind** — no `.github/`, no pipeline file anywhere. Nothing runs `mvn test` or `ng build` automatically. Add a two-job workflow; note `ng test` and `npm run lint` both currently exit non-zero, so fix or omit them initially.

---

## P3 — Tech debt & polish

**Backend**
- **Schema management (M):** `ddl-auto=update`, no Flyway/Liquibase, no SQL in the repo. Note the corrected failure mode: adding a NOT NULL column doesn't fail at *startup* — the ALTER is silently swallowed and the app dies at the first query. `update` also ignores column widening (blocking P1-1's fix) and renames. Add Flyway + a V1 baseline, set `ddl-auto=validate` for non-test profiles.
- **No `@ControllerAdvice` (M):** every 4xx returns Spring's default, information-free body with no field errors, so the client can only show generic messages. Add a small `ApiError` record handling `MethodArgumentNotValidException`.
- **Waitlist tiebreaker (S):** promotion orders by `createdAt` only. `@PrePersist` uses `LocalDateTime.now()`, so two bookings created in the same tick can tie, and the display query (`findByEventId`) has *no* `ORDER BY` at all — displayed position can disagree with promotion order. Add `...OrderByCreatedAtAscIdAsc` on both sides.
- **Lombok `@Data` on bidirectional entities (S):** `Booking.event` ↔ `Event.bookings` with no `@ToString.Exclude` → `toString()` recurses to `StackOverflowError`. Exclude both sides, or drop `@Data` for `@Getter/@Setter` + id-based equals.
- **Dead/inert code (S):** `CorsConfig`'s `CorsConfigurationSource` bean is never consumed (no Spring Security, no `CorsFilter`) — real CORS comes from the three `@CrossOrigin` annotations. Pick one mechanism. `EventService.updateEvent`/`deleteEvent` are unreachable, and `updateEvent` silently drops `capacity`/`pricePerPerson` — a live landmine if it's ever wired up (see Open Q3).
- **Unpaginated `GET /api/events` (M):** returns every row (~300 seeded) and fans out into an unbounded `IN` clause. The client never calls it; either paginate or remove it.
- **No prod profile (S):** one properties file with `show-sql=true`, hardcoded port, and default DB credentials.

**Frontend**
- **DST arithmetic (S):** `previousDay`/`nextDay` add ±`24*60*60*1000`; local days are 23/25 hours across DST transitions. Use `setDate(getDate()±1)`.
- **Events outside 08:00–20:00 are hidden (S):** `hours` is hardcoded 8–20 and `getEventPosition` doesn't clamp or filter — an early/late event renders off-grid with no indication. Overlapping events also stack on top of each other.
- **Post-cancel refresh races (S):** overlapping cancels can let a stale `loadData(false)` response overwrite fresher state; a failed background refresh shows stale occupancy with no indicator.
- **No feedback when a cancellation promotes someone (S):** the promoted booking just silently moves tables on the next refresh — the operator is never told it happened, which is the single most valuable moment in the waitlist feature.
- **Booking form seat counts go stale (S):** the event summary sits outside the `*ngIf="!result"`, so after a successful booking it still shows the pre-booking availability.
- **Notes are collected but never displayed (S):** stored and returned by the API, rendered nowhere. Add a column to the management table.
- **No way to remove a waitlisted booking (S):** the backend supports it (`cancelBooking` handles WAITLISTED correctly and skips promotion); the UI only exposes Cancel on CONFIRMED rows.
- **Dashboard doesn't show its own date and has no refresh (S):** `DashboardResponse.date` is on the wire and asserted in tests, but never bound.
- **Dead code (S):** `EventService.getEvents()` has no callers; `Event.createdAt`/`updatedAt` are declared but never returned by `EventResponse`.
- **No linter/formatter (S):** `npm run lint` fails — the script exists but no `lint` architect target does.

**Repo**
- **Builds are not reproducible (S):** `.gitignore` excludes `package-lock.json` (untracked) and the Maven wrapper. Commit the lockfile and use `npm ci`.

---

## Suggested sequencing

Continuing the project's phase-per-commit convention:

- **Phase 8 — P0 hardening.** P0-1 (+ its regression test) and P0-2 first: both are small, and each is independently capable of making the app show wrong data or stop working. Then P0-4 (one config line). Then P0-3, which is the largest of the four and deserves its own commit with a concurrency test.
- **Phase 9 — P1 correctness.** P1-1 through P1-6. P1-2 and P1-3 are quick wins that make the app demo correctly on any day of the week. P1-4 pairs naturally with P0-1 since both are "whose clock defines today".
- **Phase 10 — Test & CI floor.** P2-1…P2-4 are each a few lines and close mutation-proven holes; do them before P2-5/P2-6. Land CI (P2-7) last so it starts green.
- **Phase 11+ — P3,** opportunistically. The migration tool (P3 schema) should come before any further schema change, and it blocks P1-1's column widening.

**Open Questions to put back to the PRD author:** Q3 (what should happen when capacity is reduced below current bookings — reject, or allow and warn?) and Q4 (should `pricePerPerson` surface as a revenue estimate?). Both are cheap to implement once decided; neither should be guessed at.
