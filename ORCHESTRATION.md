# Orchestration & Frozen Contracts

Phase 1 build of the Monash Course Registration System.
This file is the single source of truth for agent boundaries, branches, and the API contract.
Do not change frozen decisions without the orchestrator's explicit approval.

## Roles & ownership

| Agent | Owns | Must not touch |
| --- | --- | --- |
| planner | `docs/specs/**` | `backend/**`, `frontend/**` |
| coder-backend | `backend/**` | `frontend/**`, `docs/specs/**` |
| coder-frontend | `frontend/**` | `backend/**`, `docs/specs/**` |
| tester | `backend/src/test/**`, `docs/qa/**` | production code (`backend/src/main/**`, `frontend/src/**`) |
| reviewer | `docs/reviews/**` | production code, tests |
| git-publisher | git/gh operations only | file contents |

If a tester/reviewer finds a defect, they report it; the owning coder fixes it.

## Frozen tech decisions

- Backend: Java 21 (compiled on JDK 23), Spring Boot 3.3.5, Maven, Spring Web + Data JPA + Security,
  H2 in-memory (`jdbc:h2:mem:registration`, `ddl-auto: create-drop`), JWT (jjwt 0.12.6), BCrypt passwords.
- Frontend: React 18 + TypeScript + Vite 5 + Tailwind CSS 3.4 + framer-motion + lucide-react +
  react-router-dom 6 + axios. Dev server proxies `/api` to `http://localhost:8080`.
- Tests: JUnit 5 + Spring Boot Test + MockMvc. No Testcontainers, no external services.
- Auth: `Authorization: Bearer <jwt>`; JWT subject = studentId; 120 min expiry.
- API base path: `/api`. Frontend must never hardcode `http://localhost:8080`.

## Frozen UI direction ("fancy university")

- Palette: Monash blue `#006DAE`, navy `#002B49`, ink `#0A1A2F`, gold `#C9A227`, mist `#F5F7FA`.
- Type: `Fraunces` (display/serif) + `Inter` (body), loaded from Google Fonts.
- Feel: academic prestige — deep navy gradient hero, subtle grain/mesh, gold rules, glass cards,
  elegant motion (fade/slide, hover lift), generous whitespace. No cartoon styling, no generic
  bootstrap look, no emoji-as-icons (use lucide icons).
- Must be responsive (desktop + mobile) and keyboard accessible; visible focus states.
- Print stylesheet for schedule (FR5 print).

## Data model (MVP+)

- `Student`: id (uuid), studentId (unique, e.g. `32012345`), name, email, program, passwordHash.
- `Course`: id (uuid), code (unique), name, creditHours, instructor, capacity, availableSeats,
  semester (e.g. `2026-S2`), description, faculty, schedule (child table: day, startTime, endTime, location).
- `Registration`: id (uuid), student, course, registeredAt. Unique constraint `(student_id, course_id)`.
- `AuditLog`: id, studentId, courseCode, action (`REGISTER`/`DROP`/`REGISTER_REJECTED`), outcome,
  detail, createdAt. Written for every register/drop attempt (NFR4).
- No admin UI in Phase 1.

## Frozen API contract

All errors use: `{"error":{"code":"...","message":"...","timestamp":"ISO-8601"}}`
with the correct HTTP status. Validation errors: 400 `VALIDATION_ERROR`.

### Auth
- `POST /api/auth/login` body `{studentId, password}`
  - 200 `{token, student:{studentId,name,email,program}}`
  - 401 `INVALID_CREDENTIALS`
- `GET /api/auth/me` (auth) -> `{studentId,name,email,program}`

### Catalog
- `GET /api/semesters` -> `[{code,name,active}]`
- `GET /api/courses?semester=2026-S2&q=<text>` (auth)
  -> `[{id,code,name,creditHours,instructor,capacity,availableSeats,semester,faculty,description,schedule:[{day,startTime,endTime,location}]}]`
  - `q` matches code OR name, case-insensitive, trimmed; blank/absent = all.
  - Default semester = configured active semester; only that semester's courses.
- `GET /api/courses/{id}` (auth) -> same object; 404 `COURSE_NOT_FOUND`.

### Registration
- `GET /api/registration-period` -> `{semester,startAt,endAt,open,serverTime}`
  - `open` = serverTime within [startAt, endAt].
- `GET /api/registrations` (auth) -> `{semester, totalCredits, maxCredits:24, items:[{registrationId, registeredAt, course:{...}}]}`
- `POST /api/registrations` (auth) body `{courseId}` -> 201 `{registrationId, message, course, availableSeats}`
  - 403 `REGISTRATION_CLOSED` when outside period
  - 409 `COURSE_FULL` when availableSeats = 0 (no record created, seat count unchanged)
  - 409 `ALREADY_REGISTERED` when duplicate
  - 409 `CREDIT_LIMIT_EXCEEDED` when totalCredits + course.creditHours > 24
  - 404 `COURSE_NOT_FOUND`
- `DELETE /api/registrations/{registrationId}` (auth) -> 200 `{message, availableSeats}`
  - 403 `REGISTRATION_CLOSED`, 403 `NOT_OWNER`, 404 `REGISTRATION_NOT_FOUND`
- `GET /api/registrations/export` (auth) -> `text/csv` attachment `course-list.csv`
  with header `Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location`.

### Concurrency requirement
Seat decrement/increment must be atomic under concurrent registration (pessimistic row lock or
optimistic version + retry). Tests will hammer this with parallel requests; 10 seats must yield
exactly 10 successful registrations and `availableSeats = 0`.

## Seed data (backend must provide)

- 3 students, password `Password123!`:
  - `32012345` / Aisha Rahman / aisha.rahman@student.monash.edu / BCS
  - `32067890` / Daniel Tan / daniel.tan@student.monash.edu / BSE
  - `32011111` / Priya Nair / priya.nair@student.monash.edu / BBIS
- 12–16 courses in `2026-S2` (varied faculties, credit 3/6, one course with capacity 1 for full-course
  tests, one course at capacity 0 seeded as full), plus a few in `2026-S1` that must NOT appear.
- At least one course with >1 schedule slot.

## Branching & git

- `main` — protected by convention; only merged via PR after KB approval.
- `docs/phase-1-specs` — planner output (PR #1).
- `feat/phase-1` — branched from `docs/phase-1-specs`; implementation + tests + reviews (PR #2, stacked).
- Conventional commits (`feat:`, `test:`, `docs:`, `chore:`). No secrets, no `node_modules`, no `target`.
- git-publisher never merges; it opens PRs and asks KB for approval.

## Definition of done (per feature)

1. Spec exists in `docs/specs` with FR/NFR IDs and acceptance criteria.
2. Production code implements it; backend compiles (`mvn -q -DskipTests package`).
3. `mvn test` green; frontend `npm run build` green.
4. Reviewer verdict `APPROVED` with no blocking issues in `docs/reviews/`.
5. Committed on the feature branch with a conventional commit message.
