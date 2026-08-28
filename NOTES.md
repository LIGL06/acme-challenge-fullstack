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
- Will run Sonnet 5 with ultracode effort for first run, since time is the blocker here, will do a scrub with Opus 5 for final analysis. 
- The local Postgres 'peek_challenge' DB already has 300 seeded events (0 real bookings) from a prior schema, without the new capacity/pricePerPerson columns. Starting the server with NOT NULL columns added via Hibernate's ddl-auto=update will fail against those existing rows.
    - Choices can be:
    1. to Drop & reseed with new schema (capacity, pricePerPerson, sample bookings). Since all current data is just disposable seed fixtures.
    2. Dont touch DB, Assume test and compilation will pass, then run/reseeed manually.
    - *EDIT*: I've chosen Drop & reseed, since is new data needed.
- Added DEFAULT_CAPACITY and DEFAULT_PRICE to DataSeeder, as well with some bookings to test those limited/waitlist scenarios.
- Changed the alignment on the calendar row, since it was horizontal overflowing, now it's a single line, giving better readability, _pending confirmation on intended alignment._


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

---

## Phase 4: Angular booking form UI

For this phase I used multi-agent orchestration (design proposals → judge → implement → adversarial review) rather than implementing solo, since the user had just turned on a mode that biases toward this for substantive work. Documenting the *process* here too, since it materially shaped the decisions below.

### Process
1. **Design** — 3 agents independently proposed a UX approach for "click an event → book it": (a) a dedicated route `/events/:id/book`, (b) a hand-rolled modal overlay, (c) a docked side/bottom panel. Two of the three (modal, docked-panel) failed on the first run with a transient "connection lost mid-response" infra error, so the first judging pass only had 1 proposal to evaluate (it still produced a well-reasoned, codebase-grounded spec, but the comparison wasn't real).
2. **Implement** — built exactly to the single-proposal spec: `EventService.createBooking()`, a new route + standalone `BookingFormComponent`, `CalendarComponent.onEventClick` wired to navigate there. Verified with its own `ng build` run before returning.
3. **Verify** — 3 agents reviewed the diff through different lenses (PRD/correctness, Angular/TypeScript quality, UX edge cases) and surfaced 8 findings (see below).
4. **Retry** — per explicit instruction, retried the 2 failed design agents with the identical prompts. Both succeeded this time, giving a genuine 3-proposal set.
5. **Rejudge** — re-ran the judge with all 3 proposals *and* full knowledge that the dedicated-route approach was already implemented, built, and reviewed. Instructed to bias toward keeping the existing implementation unless an alternative was "clearly and substantially better," since discarding working, verified code has a real cost. Verdict: **KEEP_EXISTING**.

### Decision: dedicated route (`/events/:id/book`) over modal or docked panel

**Why kept over the modal overlay:**
- No Angular Material/CDK is installed in this project, so a modal means hand-rolling a focus trap, Escape-key handling, backdrop-click handling, and body-scroll locking. The modal proposal's own submitted pros/cons admitted its focus trap "is necessarily simple... not as robust as a library-provided dialog" — real accessibility risk for a rewrite the PRD doesn't actually require (the PRD says "opens (or navigates to)," so either satisfies it).
- The route approach gets the exact same outcome (calendar refreshes on return) for free from the router's destroy/recreate lifecycle — no event-emitter/refresh-service plumbing needed either way, so the modal's main structural argument (avoiding plumbing) doesn't hold up once you actually compare the two.

**Why kept over the docked side/bottom panel:**
- Requires restructuring `CalendarComponent`'s template into a two-region flex layout and adding `ngOnChanges`-based state-reset logic for the "switch between two open events without closing first" case — the panel proposal's own cons list flagged this as "an easy detail to forget/get wrong." That's added surface area in a component that already works, for a UX preference rather than a defect fix.

**Pros of the route approach:**
- Bookmarkable/shareable URL; browser back/forward works for free.
- Router's natural destroy/recreate on navigation-back means the calendar's occupancy badges refresh with zero extra wiring.
- Smallest, most contained diff: one service method, one route entry, one new component, a 2-line change to `onEventClick`, `calendar.component.html`/`.scss` untouched entirely.

**Cons / accepted trade-offs:**
- Not deep-linkable to "come back to this exact booking mid-fill" (fresh navigation always starts the form blank) — acceptable, no PRD requirement for draft persistence.
- Loses the calendar's `selectedDate` on a naive round trip — **mitigated**: `onEventClick` passes the current date as a `?date=` query param, and `CalendarComponent.ngOnInit` restores `selectedDate` from it (falling back to today if absent/invalid) before loading events.
- A full route transition is heavier than a modal for back-to-back bookings on the same event. Accepted as a minor UX cost given the accessibility/complexity trade-off above; not a blocker for this phase's scope.

### Verify findings and how they were resolved
All 8 findings from the 3-lens review were low/medium severity, non-structural (consistent with the rejudge's read of the situation). Applied directly rather than delegating further:

| Finding | Severity | Fix |
|---|---|---|
| First/last name accepted whitespace-only input (backend has `@NotBlank`) | low | Added a `notBlankValidator()` alongside `Validators.required` |
| `participantCount` accepted fractional values (backend field is `Integer`) | low (reported by 2/3 reviewers) | Added an `integerValidator()` alongside `min(1)` |
| `<form>` missing `novalidate`, letting native HTML5 validation race with Reactive Forms | medium | Added `novalidate` |
| Route id/date read once via `route.snapshot` instead of subscribing — stale data if the component instance were ever reused for a different event id | low (reported by 2/3 reviewers) | Switched to `route.paramMap.subscribe(...)`, resetting `event`/`result`/`submitError`/`loadError`/form state on each emission. Not reachable via the shipped calendar→form entry point today (different route configs, so Angular doesn't reuse the instance), but cheap to fix correctly and removes a latent trap for any future direct booking-form-to-booking-form link. |
| `event?.title` in the result panel produced an NG8107 "unnecessary optional chaining" build warning (already narrowed non-null by the ancestor `*ngIf`) | low | Changed to `event.title` |
| No visible affordance while a booking is submitting (button label unchanged, no disabled styling) | medium | Button label switches to "Booking…" while `submitting`; added a global `.btn:disabled { opacity: 0.6; cursor: not-allowed; }` to `styles.scss` (benefits the Cancel/nav buttons too, not just this form) |

### Assumptions
- Kept the booking form's error messages generic per HTTP status (400/404/other) rather than trying to parse and surface the backend's raw Spring validation error body, since that endpoint doesn't return a custom error DTO (see Phase 1 notes) — parsing it would mean coupling the client to an unstable, framework-default error shape.
- Guest name (`firstName`/`lastName`) validation only enforces "not required + not blank," matching the backend; no additional format/length rules were invented client-side.
- The booking form intentionally does **not** block or warn when `event.availableSeats` is 0 — submitting into a full event must still succeed and land as `WAITLISTED` per the PRD, so no client-side pre-check against capacity was added anywhere.

### Trade-offs / known limitations (would revisit with more time)
- No interactive browser testing was possible in this sandboxed environment (no display) — validated via `ng build`, static review, and reasoning through the `?date=` round-trip logic by hand, not by actually clicking through it in a browser. Worth a manual spot-check on a real machine.
- The stale-route-param fix (subscribing instead of snapshot) hardens a currently-unreachable path; if a "book again" or direct booking-to-booking link is ever added, re-verify this still behaves correctly since it wasn't exercised by any real navigation in this pass.
- `GET /api/bookings/event/{id}` (pre-existing, not touched this phase) still returns raw `Booking` entities rather than a DTO — still on the list to unify whenever the booking management view (next phase) is built.

### How it was tested
- `mvn test`: all 4 tests still pass (backend untouched this phase).
- `ng build`: compiles cleanly, zero errors/warnings, after all fixes were applied.
- Verified both `http://localhost:4200/` and `http://localhost:4200/events/13/book` serve correctly via curl, and that `GET /api/events/13` still reflects live occupancy — confirms the client/server wiring is intact end-to-end.
- As in Phase 1, no real browser/click-through testing was possible (no display in this sandbox) — this is the main gap to close with a manual pass.

---

## Between phases: calendar row-alignment fix

The user reported the event block's label (availability badge) and booking counter were unreadable — root cause: `getEventPosition()` sets block height directly to `event.duration` in minutes-as-pixels (60px for a 60-min event), while the content was stacked 3 lines deep (title / time / badge+count) inside `padding: 0.5rem`. That left too little vertical room, so the bottom line was clipped by the block's `overflow: hidden`.

Fixed by collapsing the block to a single flex row (title, badge, booked count), with `min-height: 28px` so short-duration events can't collapse below one readable line, and `flex: 1 1 auto` + ellipsis on the title so it truncates instead of pushing the fixed-width badge/count off-screen. The user then committed their own refinement on top (`2d206dd`) that also dropped the separate time label from the row for a tighter layout — that commit is authoritative; the only follow-up needed was removing the now-orphaned `.event-time` SCSS rule and `formatEventTime()` method (`f1aaf5c`).

---

## Phase 5: Cancel + waitlist promotion (backend)

### What was built
- `BookingRepository.findByEventIdAndStatusOrderByCreatedAtAsc(eventId, status)` — used to fetch waitlisted bookings oldest-first for promotion.
- `BookingService.cancelBooking(id)` — looks up the booking; if already `CANCELLED`, returns it unchanged (idempotent no-op); otherwise sets it to `CANCELLED` and, only if it was previously `CONFIRMED`, runs `promoteWaitlist(event)`.
- `promoteWaitlist(event)` — recomputes `freeSlots = capacity - confirmedParticipants` after the cancellation, then walks the waitlist oldest-first: promotes any candidate whose `participantCount <= freeSlots` (deducting from `freeSlots` as it goes), and **skips** (does not stop on) any candidate that doesn't fit, per the PRD's explicit rule ("skip and try next").
- `POST /api/bookings/{id}/cancel` — returns the updated `BookingResponse` (200) or 404 if the booking doesn't exist.
- Fixed a latent bug in `BookingResponse.from()`: the `message` field only ever distinguished `WAITLISTED` from "everything else," so a cancelled booking would have been reported as "Booking confirmed." Replaced the ternary with an exhaustive `switch` over `BookingStatus` now that `CANCELLED` is a real reachable state through this endpoint.
- Removed the dead `BookingService.deleteBooking()` (hard delete) — it was never wired to any controller route, and cancel (soft delete via status) is the actual operation the PRD calls for.
- 6 new tests in `BookingServiceTest` covering: cancel-unknown-id → empty, cancel-confirmed-with-no-waitlist, cancel-already-cancelled (idempotent), cancel-a-waitlisted-booking (must NOT trigger promotion, since it wasn't occupying confirmed capacity), promote-the-one-that-fits, and skip-oversized-then-promote-the-next-one-that-fits.

### Decision: cancelling an already-cancelled booking is a no-op, not an error

**Pros:**
- No new exception type or global `@ControllerAdvice` needed — this codebase has neither today, and every other service method already communicates "not found" via `Optional`. A no-op keeps that same shape (still `Optional<Booking>`, still 404 only for a genuinely missing id).
- Idempotent by construction: a client that retries a cancel request (double-click, network retry) lands on the same terminal state either way, which is the behavior you generally want for a cancel/DELETE-like action.

**Cons / accepted trade-off:**
- A client can't distinguish "I just cancelled it" from "it was already cancelled" from the response alone (both return 200 with `status: CANCELLED`). Considered returning 409 Conflict instead, but that would require introducing a custom exception + handler for a single edge case with no PRD requirement either way — not worth the extra machinery for this scope. Revisit if the booking management view (next phase) needs to show a specific "already cancelled" toast.

### Decision: cancelling a WAITLISTED booking is allowed, but never triggers promotion

The PRD's cancel-action language is scoped to confirmed bookings ("Each confirmed booking has a **Cancel** action"), but the backend endpoint accepts cancelling any non-cancelled booking. Reasoning: a waitlisted guest changing their mind is a real scenario, and without this, a `WAITLISTED` booking would be a stuck terminal-adjacent state with no way out except promotion. Cancelling one correctly triggers **no** promotion check, since it was never occupying confirmed capacity — freeing zero slots — which is exactly what the `wasConfirmed` guard in `cancelBooking` enforces and what the corresponding test asserts.

### Complexity
- `cancelBooking`: O(1) for the status flip; `promoteWaitlist` is O(w) where w = number of currently-waitlisted bookings for that event, since it may need to walk the full list to find enough right-sized candidates to exhaust the freed slots (or reach the end). No N+1 queries — one `SUM` query for occupancy, one indexed lookup for the waitlist, then in-memory iteration.

### Assumptions
- Unlimited waitlist size (per the PRD's own stated assumption) — no cap enforced anywhere.
- Partially-fitting waitlist entries are skipped, not split or partially promoted — matches the PRD's stated assumption exactly.
- No separate "waitlist position" column: ordering is derived from `createdAt` at read time (this was already the plan noted in Phase 1-3, now put to use).

### Trade-offs / known limitations
- `promoteWaitlist` re-queries `sumParticipantsByEventIdAndStatus` right after the cancelling save rather than computing the freed count arithmetically from the cancelled booking's own `participantCount`. Slightly more DB work (one extra `SUM` query) but immune to drift if this method is ever called from a path where the "before" state isn't precisely known — correctness over a micro-optimization here.
- No optimistic locking / row-level locking on the event or its bookings — two concurrent cancellations for the same event could theoretically both read the same `freeSlots` before either promotion commits, momentarily over-promoting. Not addressed here since the rest of the app has no concurrency control anywhere else either (e.g. `createBooking` has the same race between the occupancy check and the save); consistent with existing scope, but worth flagging if this ever needs to be production-hardened.
- Still backend-only: no cancel button exists in the UI yet. That's the booking management view (next phase), which is exactly where this endpoint gets its first real caller.

---

## Phase 6: Booking management view

Used the same workflow pattern as Phase 4 (single implement pass, then adversarial multi-lens review), but skipped a design-panel step this time — see the navigation decision below, which the PRD already settles explicitly rather than leaving as a judgment call.

### Decision: the calendar's event click now opens the management view, not the booking form

The PRD's own wording creates an apparent conflict with Phase 4: section 3 ("Booking Management View") says "Clicking on an event in the calendar opens (or navigates to) a booking management view for that event," while Phase 4 wired the same click to the booking-creation form. Resolved by re-pointing the primary click at the management view (since the PRD is explicit and unambiguous here, this wasn't treated as a design choice worth running a proposal panel over) and keeping the booking form fully reachable one level down:

- Calendar (`/`) → click event → `/events/:id/manage` (this phase)
- Management view → "+ New Booking" → `/events/:id/book` (Phase 4's form, untouched business logic)
- Booking form → submit or Cancel → back to `/events/:id/manage` (previously went straight back to `/`)
- Booking form's `loadError` case (event 404/failed to load) → still falls back to `/`, since there's no valid event to manage in that case — this is the one place `backToCalendar()` was kept as-is
- Management view → "Back to calendar" → `/`

The `?date=` query-param round-trip Phase 4 already built is threaded through every one of these hops so the operator's selected day survives the whole detour.

**Pros:** matches the PRD's literal acceptance criterion; the booking form's create-a-new-booking flow (guest-facing simulation) stays fully intact and just moved one hop deeper, so none of Phase 4's validation/waitlist-messaging logic needed to change.
**Cons:** one more click to create a booking than before (calendar → manage → book, instead of calendar → book directly). Accepted, since the PRD frames the calendar as an operator tool where seeing current occupancy/bookings first is the more natural default.

### What was built
- **Backend:** `BookingService.getBookingsByEventId` now returns `List<BookingResponse>` (mapped via the existing `BookingResponse.from`) instead of raw `List<Booking>` entities, matching the DTO-at-the-service-boundary convention already used by `EventService` — this closes the gap flagged as a known limitation at the end of Phase 4. `BookingController`'s matching endpoint signature updated; unused `Booking` import removed. Confirmed via a new test that this endpoint returns bookings of **every** status unfiltered (CONFIRMED + CANCELLED + WAITLISTED together) — filtering into "main table" vs. "waitlist" is the client's job, not the API's.
- **Frontend:** new standalone `BookingManagementComponent` (`/events/:id/manage`) — fetches the event and its bookings in parallel via `forkJoin`, splits them into a main table (CONFIRMED + CANCELLED, sorted oldest-first, cancelled rows struck-through/dimmed via a `status-cancelled` class) and a waitlist section (WAITLISTED only, oldest-first, numbered #1/#2/...). Each CONFIRMED row gets a Cancel button that calls the existing `POST /api/bookings/{id}/cancel`. `EventService` gained `cancelBooking()` and `getBookingsByEventId` now types its response as `BookingResponse[]` instead of the old raw `Booking` shape (that interface was deleted as dead code once nothing referenced it). `calendar.component.ts`'s `onEventClick` and `booking-form.component.ts`'s exit points were rewired per the decision above.

### Verify findings and how they were resolved
The 3-lens review (PRD/correctness, Angular/TS quality, UX edge cases) came back clean on PRD compliance, but caught a real bug and a real race condition in the new component, both fixed directly:

| Finding | Severity | Fix |
|---|---|---|
| After a successful cancel, `cancelBooking()` reloaded via `loadData(false)`; if *that* reload failed for any reason, its generic error handler set `cancelError = 'Could not cancel this booking...'` — actively lying about an action that had already succeeded, and leaving the row stuck showing stale CONFIRMED/re-enabled-Cancel state | high | `cancelBooking()` now patches the returned `BookingResponse` directly into `mainBookings` the moment the cancel call itself succeeds (so the row is correct regardless of what the background refresh does), and `loadData`'s error branch only sets a page-level error when `showLoading` is true — a background refresh failure is no longer conflated with a cancel failure |
| Single scalar `cancellingId` meant clicking Cancel on a second row while a first cancel was still in flight would silently clear the first row's "Cancelling…" state (and vice versa on completion) | medium | Replaced with a `cancellingIds: Set<number>`, so each row's in-flight state is tracked independently |

### Assumptions
- The "table of confirmed bookings" from the PRD's section 3 is read as "table of non-waitlisted bookings" (CONFIRMED + CANCELLED together) rather than literally confirmed-only, since the same section separately requires cancelled and confirmed bookings to be visually distinct from each other in that view — that requirement only makes sense if cancelled bookings are still shown there, not removed from the list once cancelled.
- Both the main table and the waitlist section sort oldest-first by `createdAt`. The PRD only specifies ordering for the waitlist (explicitly "in order of creation"); applying the same rule to the main table was a simplicity choice, not a requirement — no strong signal either way for that table.
- No combined "event + bookings" backend endpoint was added; the management view makes two parallel calls (`getEventById`, `getBookingsByEventId`) via `forkJoin`, consistent with how the rest of the app already composes existing endpoints rather than growing new aggregate ones on demand.

### Trade-offs / known limitations
- Same lack-of-concurrency-control caveat as Phase 5: no optimistic locking, so two operators cancelling different bookings for the same event at nearly the same moment could race on the promotion math. Not addressed here, consistent with the rest of the app.
- No interactive browser testing was possible in this sandboxed environment — validated via `mvn test`, `ng build`, and the adversarial review, not by clicking through it. Worth a manual pass, especially the full click-through chain (calendar → manage → book → manage → calendar) and the cancel-then-promotion visual update.
- The booking form's create flow is now one hop further from the calendar than in Phase 4; if user feedback says this is annoying for operators who mostly just want to add a walk-in booking, a quick "+" affordance directly on the calendar tile (bypassing the management view) would be a small, additive follow-up — not built now since it's speculative.

### How it was tested
- `mvn test`: 12/12 pass (4 `EventServiceTest` + 8 `BookingServiceTest`, including 2 new tests added this phase for the DTO-mapping change).
- `ng build --configuration development`: compiles cleanly, 0 errors/0 warnings.
- `git status --short` confirmed the diff was exactly the files this phase was expected to touch — `calendar.component.html`/`.scss` untouched, `DataSeeder` untouched.

### How it was tested
- `mvn test`: 10/10 pass (4 pre-existing `EventServiceTest` + 6 new `BookingServiceTest`), run against the H2 in-memory test profile.
- Frontend untouched this phase — confirmed via `git status` showing only `server-java` files changed.
