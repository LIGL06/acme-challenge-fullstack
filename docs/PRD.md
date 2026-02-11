# PRD: Operator Booking & Capacity Management

## Background & Context

Peek is a SaaS platform used by tour and activity operators to run their businesses. Operators log into our back-office to manage their schedule, track incoming bookings, and stay on top of how full their tours are on any given day.

Right now, operators can see their events in a calendar view and a very basic booking form exists. But there's a critical gap: **operators have no visibility into capacity**. They can't see how full a tour is, they can't prevent overbooking, and when a popular tour fills up, customers have no way to express interest beyond simply not booking.

This is one of our most requested features from operators. We've validated it through 12 operator interviews and NPS feedback, and it's blocking several enterprise deals.

---

## Goals

1. Give operators real-time visibility into event capacity and occupancy
2. Prevent overbooking at the system level
3. Provide a waitlist mechanism so operators capture demand even when tours are full
4. Give operators a simple way to manage and cancel bookings
5. Surface a daily summary so operators can quickly understand how their day looks

**Success Metrics (for post-launch):**
- Overbooking incidents drop to 0
- Waitlist conversion rate ≥ 20%
- Operator daily active usage increases 15%

---

## Personas

**Primary: Alex, Tour Operator**
Alex runs a small wine-tasting business with 4–6 tours per day, each limited to 12 guests. Today Alex uses spreadsheets alongside Peek to track capacity. The goal is to make Peek the single source of truth so Alex can stop double-managing data.

**Secondary: Guest (Future scope)**
Not in scope for this release, but future iterations will expose availability to end customers on the booking widget.

---

## Scope

### In Scope

#### 1. Event Capacity

Events need a maximum capacity (number of seats/participants). Operators should be able to see at a glance how many seats remain on any event.

**Acceptance Criteria:**
- Each event has a `capacity` field (positive integer, required)
- Each event has a `pricePerPerson` field (decimal, required)
- The calendar view displays a visual availability indicator on each event tile:
  - **Green**: more than 50% seats available
  - **Yellow / "Limited"**: 1–50% seats remaining
  - **Red / "Full"**: 0 seats remaining (capacity reached)
- The availability indicator reflects real-time booking data (not a static field)

---

#### 2. Bookings with Participant Count

When a booking is created, the customer provides how many participants are in their party. This affects how capacity is consumed.

**Acceptance Criteria:**
- A booking has a `participantCount` field (positive integer, minimum 1)
- A booking has a `customerEmail` field (valid email, required)
- A booking has an optional `notes` field (free text)
- A booking has a `status` field: one of `CONFIRMED`, `CANCELLED`, `WAITLISTED`
- When creating a booking, the system checks if `currentBookings + participantCount ≤ capacity`
  - If yes: booking is created with status `CONFIRMED`
  - If no: booking is created with status `WAITLISTED` (see Waitlist section)
- The booking form shows the remaining available seats before submission
- The API returns a clear error/indicator when a booking lands on the waitlist vs. is confirmed

---

#### 3. Booking Management View

Operators need a dedicated view to see all bookings for an event and take action on them.

**Acceptance Criteria:**
- Clicking on an event in the calendar opens (or navigates to) a booking management view for that event
- The view displays:
  - Event name, date, time, capacity, current occupancy (e.g., "8 / 12 booked")
  - A table of confirmed bookings with: guest name, email, participant count, booking status, booking date
  - A separate section showing waitlisted bookings in order of creation (waitlist position)
- Each confirmed booking has a **Cancel** action
- Cancelling a booking:
  - Sets the booking status to `CANCELLED`
  - Frees the participant slots
  - Automatically promotes the next waitlisted booking to `CONFIRMED` if enough capacity becomes available
- Cancelled and confirmed bookings should be visually distinct (e.g., strikethrough or different row style)

---

#### 4. Waitlist

When a tour is full, the system should capture additional demand and automatically convert it when capacity opens.

**Acceptance Criteria:**
- If a booking request exceeds remaining capacity, the booking is saved with status `WAITLISTED` instead of rejected outright
- The booking confirmation response clearly communicates `WAITLISTED` status to the user
- When a confirmed booking is cancelled, the system:
  1. Frees the slots
  2. Finds the oldest `WAITLISTED` booking for that event
  3. If `participantCount` of that waitlisted booking ≤ newly freed slots, promote it to `CONFIRMED`
  4. If multiple waitlisted bookings exist, promote the next one in line; skip if the next one's party size still doesn't fit
- Waitlist position is visible in the booking management view

---

#### 5. Daily Dashboard

Operators need a quick overview of how their day looks without having to click into each event.

**Acceptance Criteria:**
- A dashboard view (could be the default landing page or a separate route) shows a summary for today:
  - Total events today
  - Total confirmed bookings today (sum of participantCounts across all confirmed bookings for today's events)
  - Total available seats remaining today
  - Number of waitlisted bookings today
- Each event in today's list is shown with: title, time, occupancy (e.g., "8 / 12"), status badge (Available / Limited / Full), and number of waitlisted guests
- The dashboard is readable at a glance — think "morning briefing card"

---

### Out of Scope (for this release)

- Email/SMS notifications to waitlisted guests upon promotion
- Customer-facing booking widget or public availability page
- Recurring events or event series management
- Payment processing or revenue tracking
- Role-based access control / multi-user accounts
- Mobile-native application

---

## Open Questions

1. Should we cap waitlist size, or allow unlimited entries? (Current assumption: unlimited)
2. Should partially-fitting waitlist entries be skipped or split? (Current assumption: skip and try next)
3. What happens if an operator reduces capacity below current bookings? (Out of scope for now — prevent it or warn?)
4. Should `pricePerPerson` be surfaced in the booking management view as a revenue estimate?

---

## Data Model Notes

> These are directional suggestions. Engineering is expected to validate and improve on these.

**Event (additions):**
- `capacity: int` — max number of participants
- `pricePerPerson: decimal` — price per participant

**Booking (additions/changes):**
- `participantCount: int` — number of participants in this booking
- `customerEmail: string` — contact email
- `notes: string` (nullable) — free text
- `status: enum` — `CONFIRMED | CANCELLED | WAITLISTED`
- `createdAt: timestamp` — used for waitlist ordering

---

## Wireframe Notes

No high-fidelity designs provided. Engineering should make reasonable UX decisions. We expect the implementation to be functionally complete and usable, not pixel-perfect. Clarity and usability matter more than polish.

