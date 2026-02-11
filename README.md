# Full-Stack Engineering Challenge

At Peek, we build software for tour and activity operators to run their businesses. One essential feature is the back-office where operators schedule events, view orders, and track how full their tours are on any given day.

This challenge uses **Angular** for the frontend and **Java / Spring Boot / Hibernate / PostgreSQL** for the backend.

## Format

- **Kickoff (5 min):** We'll walk you through this repository, the existing code, and what we're asking you to build.
- **Challenge (2 hours):** You have 2 hours to implement as much of the feature as you can.
- **Debrief (30 min):** After the session, we'll continue building a quick feature together.

## How We Expect You to Work

This is an AI-first engineering exercise — you are required to use AI tools. Bring whatever you work with daily: Claude Code, Cursor, Copilot, ChatGPT, or anything else. We want to see how you use them and the choices you make along the way.

## The Challenge

We're giving you a **Product Requirements Document (PRD)** instead of a list of tasks.

**Read it here: [docs/PRD.md](docs/PRD.md)**

The PRD describes a new feature from the product team's perspective. Your job is to read it, decide what to build and in what order, and implement it.

There are intentional gaps and open questions in the PRD, just like in real product work. Use your judgment. If something is ambiguous, make a decision and move on; note your assumptions in your submission.

> **Don't try to build everything.** The PRD describes more than 2 hours of work even with AI. A focused, working slice with sound design decisions beats a half-built everything. We care more about the quality of your judgment than the quantity of your output.

## The Existing Codebase

### Server (`server-java/`)

A Spring Boot 3.2 / Java 21 application with:

- `Event` and `Booking` JPA entities
- `EventRepository` and `BookingRepository`
- `EventService` and `BookingService` with existing business logic
- `EventController` with GET endpoints for events
- `BookingController` scaffolded
- A data seeder that populates sample events (run with `seed` profile)
- Docker Compose setup for PostgreSQL + app

### Client (`client-angular/`)

An Angular 17+ standalone application with:

- `EventService` for HTTP calls to the REST API
- A `CalendarComponent` with a day-view calendar
- Basic routing and SCSS styling

## Setup

### Prerequisites

Java, Maven, and Node versions are pinned in [`.tool-versions`](.tool-versions) at the repo root. Install them in one shot with [mise](https://mise.jdx.dev/) or [asdf](https://asdf-vm.com/):

```sh
# mise (recommended)
mise install

# or asdf
asdf install
```

If you prefer to install manually, use the versions listed in `.tool-versions` as your reference.

**Postgres 16** is not in `.tool-versions` — pick whichever option you prefer:

- **Docker (easiest):** `cd server-java && docker compose up -d db` — starts Postgres 16 on `localhost:5432` with the right database and credentials already configured.
- **Native install:** install Postgres 16 yourself (Homebrew, apt, asdf-postgres, etc.) and create a `peek_challenge` database. The app expects `localhost:5432`, user `postgres`, password `postgres` by default; override via `PGHOST` / `PGUSER` / `PGPASSWORD` env vars if you've set things up differently.

### Server

```sh
cd server-java
# (make sure Postgres is running — see Prerequisites above)
mvn spring-boot:run -Dspring-boot.run.profiles=seed
```

The app listens on `http://localhost:8080`.

**Run tests:**

```sh
mvn test
```

**Docker (optional, full stack):** if you'd rather not run anything natively, `docker-compose up --build` brings up Postgres and the Spring Boot app together. Local dev is the expected path for the challenge.

### Client

```sh
cd client-angular
npm install
npm start
```

Visit: `http://localhost:4200`

## Existing API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/events` | List all events |
| GET | `/api/events/{id}` | Get event by ID |
| GET | `/api/events/date/{date}` | Get events for a specific date (YYYY-MM-DD) |
| GET | `/api/bookings/event/{eventId}` | Get bookings for an event |
| POST | `/api/bookings` | Create a booking |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Angular 17+, TypeScript, RxJS, SCSS |
| Backend | Java 21, Spring Boot 3.2, Spring Data JPA, Lombok |
| Database | PostgreSQL 16 |
| Build | Maven, npm |
| Containerization | Docker, Docker Compose (optional) |

## Submission

1. Create your own **private GitHub repo** (do not fork this one) and share access with `martInatpeek`.
2. Make logical, incremental commits so we can follow your progression.
3. Include a `NOTES.md` in the root of your repo covering:
   - **Assumptions:** Decisions you made where the PRD was ambiguous.
   - **Prioritization:** What you built, what you skipped, and why.
   - **Trade-offs:** What you'd do differently with more time.

## Questions?

We'll be on the Zoom call muted with camera off throughout the session — unmute if you have a question or hit a blocker. We're happy to screen share if that's helpful.

You can also reach us at [martin@peek.com](mailto:martin@peek.com).
