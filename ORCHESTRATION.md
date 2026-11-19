# Orchestration & Frozen Contracts

Phase 1 build of the Monash Course Registration System, plus Phase 2 FR8 prerequisite validation and
FR11 waitlist management. This file is the single source of truth for agent boundaries, branches, and
the API contract.
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

- `Student`: id (uuid), studentId (unique, e.g. `32012345`), name, email, program, passwordHash,
  completedCourses (join table `student_completed_course`: student_id, course_id).
- `Course`: id (uuid), code (unique), name, creditHours, instructor, capacity, availableSeats,
  semester (e.g. `2026-S2`), description, faculty, schedule (child table: day, startTime, endTime,
  location), prerequisites (self join table `course_prerequisite`: course_id, prerequisite_course_id).
- `Registration`: id (uuid), student, course, registeredAt. Unique constraint `(student_id, course_id)`.
- `WaitlistEntry` (FR11): id (bigint identity, queue order), student, course, joinedAt. Unique
  constraint `(student_id, course_id)`. Position is derived (`1 + count(lower ids)`), never stored.
- `Notification` (FR11): id (bigint identity), student, course, type
  (`WAITLIST_JOINED`/`WAITLIST_PROMOTED`), message, createdAt, readAt. In-app only.
- `AuditLog`: id, studentId, courseCode, action (register/drop/waitlist vocabulary in `08` NFR4.2),
  outcome, detail, createdAt. Written for every register/drop/waitlist-join/leave attempt (NFR4), plus
  one `WAITLIST_PROMOTED` row per automatic promotion.
- No admin UI in Phase 1/2.

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
  -> `[{id,code,name,creditHours,instructor,capacity,availableSeats,semester,faculty,description,schedule:[{day,startTime,endTime,location}],prerequisites:[{id,code,name}]}]`
  - `prerequisites` is ordered `code` ASC and is `[]` when the course has none (FR8).
  - `q` matches code OR name, case-insensitive, trimmed; blank/absent = all.
  - Default semester = configured active semester; only that semester's courses.
- `GET /api/courses/{id}` (auth) -> same object; 404 `COURSE_NOT_FOUND`.

### Waitlist (FR11)
- `GET /api/waitlist` (auth) -> `{semester, items:[{waitlistId, joinedAt, position, course:{...}}]}`
  for the active semester, ordered by queue id ASC.
- `POST /api/waitlist` (auth) body `{courseId}` -> 201 `{waitlistId, position, waitlistSize, message, course}`
  - 403 `REGISTRATION_CLOSED`
  - 404 `COURSE_NOT_FOUND` (unknown or non-active semester)
  - 409 `ALREADY_REGISTERED`, 409 `ALREADY_WAITLISTED`
  - 409 `PREREQUISITE_NOT_MET`, 409 `CREDIT_LIMIT_EXCEEDED`
  - 409 `SEAT_AVAILABLE` (course is not full)
  - Check order: auth -> body -> period -> course -> already registered -> already waitlisted ->
    prerequisites -> credit limit -> seats available.
- `DELETE /api/waitlist/{waitlistId}` (auth) -> 200 `{message}`; 404 `WAITLIST_NOT_FOUND` (unknown,
  foreign or non-numeric id). Allowed outside the window.
- `GET /api/notifications` (auth) -> `{unreadCount, items:[{id,type,message,createdAt,read,courseId,courseCode,courseName}]}`
- `POST /api/notifications/{notificationId}/read` (auth) -> 200 `{unreadCount}`; 404 `NOTIFICATION_NOT_FOUND`.

A drop (`DELETE /api/registrations/{id}`) adds the seat back and promotes the first **eligible**
waitlisted student in the same transaction and course lock. Promotion creates a normal registration,
deletes the queue row, writes a notification and a `WAITLIST_PROMOTED` audit row; `availableSeats`
therefore stays 0 when the freed seat is immediately re-taken. A successful direct registration
clears the student's queue row for that course. See `docs/specs/13-fr11-waitlist.md` and decision A-22.

### Registration
- `GET /api/registration-period` -> `{semester,startAt,endAt,open,serverTime}`
  - `open` = serverTime within [startAt, endAt].
- `GET /api/registrations` (auth) -> `{semester, totalCredits, maxCredits:24, items:[{registrationId, registeredAt, course:{...}}]}`
- `POST /api/registrations` (auth) body `{courseId}` -> 201 `{registrationId, message, course, availableSeats}`
  - 403 `REGISTRATION_CLOSED` when outside period
  - 409 `COURSE_FULL` when availableSeats = 0 (no record created, seat count unchanged; the client
    may offer `/api/waitlist` as a separate request)
  - 409 `ALREADY_REGISTERED` when duplicate
  - 409 `PREREQUISITE_NOT_MET` when the student's completed-course history lacks any prerequisite;
    `message` names every missing code, sorted ASC (FR8)
  - 409 `CREDIT_LIMIT_EXCEEDED` when totalCredits + course.creditHours > 24
  - 404 `COURSE_NOT_FOUND`
  - Check order (normative): auth -> body -> period -> course in active semester -> duplicate ->
    prerequisites -> credit limit -> capacity.
- `DELETE /api/registrations/{registrationId}` (auth) -> 200 `{message, availableSeats}`
  - 403 `REGISTRATION_CLOSED`, 403 `NOT_OWNER`, 404 `REGISTRATION_NOT_FOUND`
  - may promote a waitlisted student (FR11): render the returned `availableSeats`, do not assume +1
- `GET /api/registrations/export` (auth) -> `text/csv` attachment `course-list.csv`
  with header `Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location`.

### Concurrency requirement
Seat decrement/increment must be atomic under concurrent registration (pessimistic row lock or
optimistic version + retry). Tests will hammer this with parallel requests; 10 seats must yield
exactly 10 successful registrations and `availableSeats = 0`. FR11 queue mutations reuse the same
course row lock, so concurrent joins get distinct positions and concurrent drops promote exactly one
eligible student each without overselling.

## Seed data (backend must provide)

- 3 students, password `Password123!`:
  - `32012345` / Aisha Rahman / aisha.rahman@student.monash.edu / BCS
  - `32067890` / Daniel Tan / daniel.tan@student.monash.edu / BSE
  - `32011111` / Priya Nair / priya.nair@student.monash.edu / BBIS
- 12–16 courses in `2026-S2` (varied faculties, credit 3/6, one course with capacity 1 for full-course
  tests, one course at capacity 0 seeded as full), plus a few in `2026-S1` that must NOT appear.
- At least one course with >1 schedule slot.
- FR8 prerequisite graph: `FIT2004`/`FIT2099` require `FIT1008`; `FIT3077` requires `FIT1008` and
  `FIT1047`; `MTH1030` requires `MTH1020`. Completion history: `32012345` completed
  `FIT1008` + `MTH1020`; `32067890` completed `FIT1008` + `FIT1047`; `32011111` none.
  No Phase 1 capacity course (`FIT3143`, `FIT3182`, `FIT1045`) has a prerequisite.
- FR11 waitlist demo: `FIT3182` queue = Aisha (`32012345`, position 1), Priya (`32011111`, position 2),
  with two `WAITLIST_JOINED` notifications. Daniel holds FIT3182's only seat; dropping it promotes
  Aisha automatically and moves Priya to position 1. No other seed invariant changes.

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
6. UI changes: PR description carries labelled Before + After evidence captured from the running app.
