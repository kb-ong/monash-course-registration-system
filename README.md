# Monash Course Registration System — Phase 1

A course registration experience for Monash University students: browse the semester catalog,
register and drop courses inside the registration window, and manage a personal timetable.

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

## Phase 1 scope

FR1 login · FR2 view/search courses · FR3 register · FR4 drop · FR5 view registered courses ·
FR6 capacity validation · FR7 registration period control.
