# Monash Course Registration System — Phase 1 + Phase 2 FR8/FR9/FR10/FR11

A course registration experience for Monash University students: browse the semester catalog,
register and drop courses inside the registration window, and manage a personal timetable.
Phase 2 adds prerequisite validation (every prerequisite must be in the student's completion history,
with missing courses named), timetable clash detection (a course whose weekly slots overlap a course
the student already holds is rejected, with the conflicting courses and times named), a configured
maximum credit-hour limit per semester with the remaining available credits shown before and after
registration, and **waitlist management** (join a full course's FIFO queue, see your live position,
get enrolled automatically when a seat opens, and receive in-app notifications).

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
| `REGISTRATION_MAX_CREDITS` | `24` | FR10 maximum credit points per student in the active semester. Set `18` to reproduce the FR10 example. |

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

Phase 2: FR8 prerequisite validation (spec: [`docs/specs/12-fr8-prerequisites.md`](docs/specs/12-fr8-prerequisites.md)) ·
FR9 timetable clash detection (spec: [`docs/specs/13-fr9-timetable-clash.md`](docs/specs/13-fr9-timetable-clash.md)) ·
FR10 maximum credit-hour validation with a configured semester limit and remaining-credit warnings
(spec: [`docs/specs/14-fr10-max-credit-hours.md`](docs/specs/14-fr10-max-credit-hours.md)) ·
FR11 waitlist management (spec: [`docs/specs/15-fr11-waitlist.md`](docs/specs/15-fr11-waitlist.md)).

Demo prerequisite history: Aisha (`32012345`) has completed `FIT1008` and `MTH1020`; Daniel
(`32067890`) has completed `FIT1008` and `FIT1047`; Priya (`32011111`) has completed none of these courses.
`FIT2004` requires `FIT1008`, `FIT3077` requires both `FIT1008` and `FIT1047`.

Demo timetable clash: register Aisha in `FIT2004` (Tuesday 10:00–12:00), then try `FIT1051`
(Tuesday 09:00–11:00) — the second registration is rejected and both classes are named.

Credit-limit demo: run the backend with `REGISTRATION_MAX_CREDITS=18`, register three courses
totalling 15 credits, then try a 6-credit course — registration is rejected and the student is told
3 credits remain.

Demo waitlist: `FIT3182` is full and starts with Aisha at position 1 and Priya at position 2.
Sign in as Daniel and drop `FIT3182` — Aisha is enrolled automatically and receives a promotion
notification, and Priya moves to position 1. The notification bell is in the application header.
