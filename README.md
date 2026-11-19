# Monash Course Registration System — Phase 1 + Phase 2 FR8 + FR11

A course registration experience for Monash University students: browse the semester catalog,
register and drop courses inside the registration window, and manage a personal timetable.
Phase 2 adds prerequisite validation (a course can only be registered when the student's completion
history contains every prerequisite, and the missing courses are named when it does not) and
**waitlist management** (join a full course's FIFO queue, see your live position, get enrolled
automatically when a seat opens, and receive in-app notifications).

- **Frontend:** React 18 + TypeScript + Vite + Tailwind CSS (academic, "fancy university" styling)
- **Backend:** Java 21 / Spring Boot 3.3 (Spring Web, Data JPA, Security, JWT), H2 in-memory
- **Tests:** JUnit 5 + MockMvc
- **Docs:** specs in [`docs/specs`](docs/specs), QA evidence in [`docs/qa`](docs/qa), reviews in [`docs/reviews`](docs/reviews)

See [`ORCHESTRATION.md`](ORCHESTRATION.md) for the frozen architecture, API contract, and agent ownership.

## Run locally

```bash
# backend → http://localhost:8080
cd backend && mvn spring-boot:run

# frontend → http://localhost:5173
cd frontend && npm install && npm run dev
```

Demo student: `32012345` / `Password123!`

### Configuration

| Env var | Default | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | random per start | HS256 signing key (≥32 chars). Set it to keep tokens valid across restarts. |
| `REGISTRATION_START` | `2026-10-01T00:00:00` | Registration window opens (ISO-8601, local time). |
| `REGISTRATION_END` | `2026-12-20T23:59:59` | Registration window closes. |
| `ACTIVE_SEMESTER` | `2026-S2` | Only this semester's courses can be registered. |

Data is H2 in-memory and reseeded on every start. The H2 console is disabled by
default; enable it only locally with `--spring.profiles.active=dev`.

## Test

```bash
cd backend && mvn test
cd frontend && npm run build
```

## Scope

Phase 1: FR1 login · FR2 view/search courses · FR3 register · FR4 drop · FR5 view registered courses ·
FR6 capacity validation · FR7 registration period control.

Phase 2: FR8 prerequisite validation (spec: [`docs/specs/12-fr8-prerequisites.md`](docs/specs/12-fr8-prerequisites.md))
and FR11 waitlist management (spec: [`docs/specs/13-fr11-waitlist.md`](docs/specs/13-fr11-waitlist.md)).

Demo prerequisite history: Aisha (`32012345`) has completed `FIT1008` and `MTH1020`; Daniel
(`32067890`) has completed `FIT1008` and `FIT1047`; Priya (`32011111`) has completed none of these courses.
`FIT2004` requires `FIT1008`, `FIT3077` requires both `FIT1008` and `FIT1047`.

Demo waitlist: `FIT3182` is full and starts with Aisha at position 1 and Priya at position 2.
Sign in as Daniel and drop `FIT3182` — Aisha is enrolled automatically and receives a promotion
notification, and Priya moves to position 1. The notification bell is in the application header.
