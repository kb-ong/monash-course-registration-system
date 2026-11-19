# 11 — Decision Log (continuation of A-series)

**Project:** Monash Course Registration System — Phase 1
**Status:** Decisions of record. These entries are normative.
**Predecessor:** [`00-overview.md`](./00-overview.md) §10 holds A-01 … A-14. This file continues the
same series without renumbering or restating those entries.

A decision here **supersedes** the spec text it names. Where a document still carries the old wording,
that document is defective and this file wins.

> **Gap note — A-15.** The original Phase 1 review asked for an `A-15` covering the active-semester
> scope of registration. It was never written into `00 §10`, leaving A-15 unassigned. To avoid
> renumbering references that already exist in reviews and tests, A-15 stays unassigned and the
> semester-scope decision is recorded as **A-17** below. Do not reuse A-15 for an unrelated decision.

---

## Decisions

| ID | Ambiguity | Resolution | Where |
| --- | --- | --- | --- |
| **A-16** | What is the Phase 1 demo "full course", and how can it be full while C1 holds? | The seeded full course `FIT3182` is **`capacity = 1`, `availableSeats = 0`**, with **exactly 1** persisted registration owned by **another** student (`32067890` Daniel Tan). It is not an empty course with a fabricated zero seat count. **Rationale:** only 3 students are seeded; the `uk_registration_student_course (student_id, course_id)` unique constraint forbids more than one registration per student per course; therefore 40 registrations cannot be fabricated without 40 students. Capacity 1 exercises the same full-path code (`availableSeats = 0` → 409 `COURSE_FULL`) and keeps **C1** — `availableSeats = capacity − activeRegistrations` — true at all times, including at seed time and after a drop/re-register cycle. **Supersedes:** the `capacity = 40` wording in the old A-01 resolution, the `FIT3182` row in `00 §8.2`, and the "capacity 40" given in `06 §8` T-FR6-07. | `00 §8.2`, `06 §8` |
| **A-17** | Registering for a course that exists but belongs to a non-active semester | Returns **404 `COURSE_NOT_FOUND`** (the same status and envelope as an unknown id — no semester oracle), and writes one audit row `REGISTER_REJECTED / FAILURE` with `detail = SEMESTER_NOT_ACTIVE`. The semester predicate is part of the **locking** query (`… where c.id = :id and c.semester = :activeSemester`), not a check performed before the lock, so the semester test and the seat decrement are atomic with respect to a semester change. Drop is unaffected: a registration can only exist for a course that was registrable, and dropping is scoped by registration id. | `00 §5.1` step 2, `00 §9.2`, `08 NFR4.2` E-11 |
| **A-18** | Which schema-level constraints are normative for Phase 1? | Four, in addition to the unique/foreign keys already stated: `course.capacity >= 0`; `course.credit_hours IN (3, 6)`; `course.available_seats BETWEEN 0 AND capacity`; `course_schedule.end_time > start_time`. These are database `CHECK` constraints, not service-layer-only validation, so a bad row cannot be inserted by the seeder or a test fixture. `capacity = 0` is **legal but unused** by the seed (the full demo row is capacity 1 per A-16); rejecting `capacity = 0` at the schema level is a deviation from this decision. `course_schedule.day IN ('MON'…'SUN')` remains normative per `00 §7.4` and is unchanged by this entry. | `00 §7.3`, `00 §7.4` |
| **A-19** | Are attempts rejected by request validation audited? | **Yes.** A malformed, missing or type-invalid attempt that fails validation writes one audit row with `detail = VALIDATION_ERROR`, at the HTTP status the contract gives that failure. `POST /api/registrations` → **400 `VALIDATION_ERROR`** with `REGISTER_REJECTED / FAILURE` (e.g. `courseId` absent, not a string, or not a UUID). `DELETE /api/registrations/{registrationId}` with a non-UUID path value → **404 `REGISTRATION_NOT_FOUND`** with `DROP / FAILURE`: path ids are opaque, so a malformed id is reported exactly like an unknown id (FR4 §3, `00 §9.2`), while the audit row still records `detail = VALIDATION_ERROR` so the two cases remain distinguishable in the trail. `course_code` is `null` when the course cannot be resolved from the request (`00 §7.6` already permits this). Validation rejected **before** the student is known (i.e. `401 UNAUTHORIZED` on a missing/invalid token) is **not** audited — there is no authenticated subject and `audit_log.student_id` is `NOT NULL`; authentication failures belong to the application log (A-10). | `08 NFR4.1`, `08 NFR4.2` E-12/E-13 |
| **A-21** | Phase 2 FR8: what is a prerequisite, what does "completed" mean, and where does the check sit? | Prerequisites are a self-referencing many-to-many edge on `course` (`course_prerequisite`), and completion is a separate `student_completed_course` history keyed by course **id**. A current-semester `registration` never counts as completion. Registration requires every prerequisite id in the history, otherwise **409 `PREREQUISITE_NOT_MET`** with every missing code sorted in `message`; the check is inserted between duplicate and timetable clash (see the subsection below). Course payloads carry `prerequisites: [{id,code,name}]`, ordered `code` ASC. The graph and history are seeded; there is no administration UI (NG3). | `12-fr8-prerequisites.md`, `00 §7.7–7.8`, `00 §8.5`, `00 §9.2`, `08 NFR4.2` E-14 |
| **A-22** | Phase 2 FR9: how is a timetable clash defined, and where does the check sit? | Clash detection is a pairwise overlap of weekly `course_schedule` slots: same `day`, `start < otherEnd` and `otherStart < end` (half-open intervals, so touching boundaries are allowed). It compares the requested course against the student's **active-semester registrations only**, ignores other students, and blocks with **409 `TIMETABLE_CLASH`** naming every conflicting course and overlapping slot. The check sits between prerequisites and the credit limit; registrations are read under a pessimistic lock on the student row so two concurrent overlapping submissions admit at most one. No schema change. | `13-fr9-timetable-clash.md`, `00 §8.6`, `00 §9.2`, `08 NFR4.2` E-15 |
| **A-23** | Phase 2 FR10: where does the semester credit limit live, and how is it surfaced? | The limit is the positive integer `app.registration.max-credits` (env `REGISTRATION_MAX_CREDITS`), default **24**, with one value for the active semester; non-positive values fail startup. Registration allows `total + creditHours <= maxCredits` and otherwise returns **409 `CREDIT_LIMIT_EXCEEDED`** after prerequisites and timetable clash and before capacity, with no row and no seat change; the message names the limit and the remaining credits. `GET /api/registrations` returns `maxCredits` and `remainingCredits = max(0, maxCredits − totalCredits)` alongside `totalCredits`; the SPA uses those fields and never hardcodes a number. The default 24 preserves Phase 1 behaviour; `REGISTRATION_MAX_CREDITS=18` reproduces the FR10 example. | `14-fr10-max-credit-hours.md`, `00 §9.2`, `00 §9.4` E7, `03-fr3-register-course.md`, `05-fr5-view-registered.md`, `09-ui-design-system.md` |

### A-21 — Phase 2 FR8 prerequisite model and check order

**Model.** `course_prerequisite (course_id, prerequisite_course_id)` records "course_id requires
prerequisite_course_id"; `student_completed_course (student_id, course_id)` records history. Both
have composite unique constraints. The check compares **course ids**, not codes, so a prerequisite
and its completion must reference the same catalogue row. A course cannot require itself; longer
cycles are a catalogue-data concern of the out-of-scope administration path. No grade, term or
completion timestamp is modelled in Phase 2 — presence of the row is "completed".

**Check order (normative).** `POST /api/registrations` is now:
auth → body → period → course exists in the active semester (A-17) → duplicate → **prerequisites** →
**timetable clash** → credit limit → capacity (inside the lock). This preserves the Phase 1 relative order of every
existing check and inserts eligibility before the credit and capacity economics. Rationale: a
student who is not eligible should learn that first; hiding it behind `COURSE_FULL` or
`CREDIT_LIMIT_EXCEEDED` would imply that fixing credits or waiting for a seat could help. The
capacity lock and atomic decrement are unchanged.

**Response.** 409 `PREREQUISITE_NOT_MET`; no registration row and no seat change. The frozen
error envelope is unchanged, so the missing course codes are carried in `message`, e.g.
`Cannot register for FIT3077: missing prerequisites FIT1008, FIT1047.` Course payloads also expose
`prerequisites` for pre-registration display.

**Audit.** Exactly one `REGISTER_REJECTED / FAILURE / PREREQUISITE_NOT_MET` row with the resolved
requested course code (NFR4 E-14).

**Seed.** The graph and history are normative in `00 §8.5`: Aisha completed `FIT1008`/`MTH1020`,
Daniel `FIT1008`/`FIT1047`, Priya none; `FIT2004`/`FIT2099` require `FIT1008`, `FIT3077` requires
both, and `MTH1030` requires `MTH1020`. No other seed invariant changes.

### A-22 — Phase 2 FR9 timetable-clash model and check order

**Overlap.** The requested course and each of the student's active-semester registrations are
compared slot-by-slot. Two slots clash when they share a `day` and their time intervals intersect:
`requested.start < registered.end` and `registered.start < requested.end`. Intervals are half-open
`[start, end)`, so `09:00–11:00` and `11:00–13:00` are compatible while `09:00–11:00` and
`10:00–12:00` are not. One overlapping pair is enough to reject, even when the courses have other
non-overlapping slots. No buffer, travel time or location rule is modelled.

**Scope.** Only the authenticated student's own active-semester registrations count; another
student's timetable and prior-semester registrations are irrelevant. The legacy Phase 1 "draw
overlaps side by side" UI is retained for read data only.

**Check order (normative).** `POST /api/registrations` is now:
auth → body → period → course exists in the active semester (A-17) → duplicate → **prerequisites** →
**timetable clash** → credit limit → capacity (inside the lock). A prerequisite rejection is
reported first; a clash is reported before the credit and capacity economics because neither can be
fixed by changing credits or waiting for a seat.

**Response.** 409 `TIMETABLE_CLASH`; no registration row and no seat change. The message names every
conflicting course (ordered by `code` ASC) and the overlapping slot(s), e.g.
`Cannot register for FIT1051: timetable clash with FIT2004 (TUE 10:00-12:00).`

**Audit.** Exactly one `REGISTER_REJECTED / FAILURE / TIMETABLE_CLASH` row with the resolved
requested course code (NFR4 E-15).

**Concurrency.** The course row is still locked first for capacity. A pessimistic write lock on the
student row additionally serialises one student's register attempts, so two concurrently submitted
overlapping courses cannot both pass the check. Lock order is course → student; no other write path
inverts it.

**Seed.** No seed change: the Phase 1 schedule already contains overlaps (`FIT2004`/`FIT1051`,
`FIT2099`/`FIT3077`, `FIT3143`/`MTH1010`), documented in `00 §8.6`.

### A-23 — Phase 2 FR10 configured semester credit limit

**Configuration.** `app.registration.max-credits` is bound by `RegistrationProperties` and validated
with `@Min(1)`; it defaults to 24 and is overridable through `REGISTRATION_MAX_CREDITS`. The value is
read on every register attempt and every registrations read, so a restart with a different value
changes the enforcement and the payload together. It is a deployment/semester setting, not a
per-student or per-program rule (AS5).

**Enforcement.** `RegistrationService` computes the active-semester total server-side and rejects
`total + course.creditHours > maxCredits` with 409 `CREDIT_LIMIT_EXCEEDED` after the FR8 prerequisite
and FR9 timetable-clash checks and before the capacity check. Landing exactly on the limit succeeds;
the failure creates no registration row and consumes no seat, and writes E-05 in NFR4.2. The message
names the configured limit and the remaining credits, e.g.
`Adding FIT3179 (6 credit points) would exceed the 18 credit point limit. You have 3 credit points remaining.`

**Surface.** `GET /api/registrations` carries `totalCredits`, `maxCredits` and
`remainingCredits = max(0, maxCredits − totalCredits)`. The SPA renders the dashboard ring, catalogue
summary, course-detail note, register dialog and schedule meter from those fields; it removes the
literal `24` and only shows the limit/remaining once the response has loaded. A 409 keeps the dialog
open, shows the server message and refetches the registrations so a stale total is corrected.

**Default.** 24 remains the shipped default so the Phase 1 API and tests are unchanged. The FR10
example is exercised by starting with `REGISTRATION_MAX_CREDITS=18`; the configured-limit tests set
that property explicitly.

---

## Supersession record

| Superseded text | Location (historical) | Replaced by |
| --- | --- | --- |
| "Interpreted as `capacity = 40`, `availableSeats = 0`" | `00 §10` A-01 resolution | A-16 (`capacity = 1` + one seeded hold) |
| `FIT3182` seed row `capacity 40 / seats 0` | `00 §8.2` | A-16 (`capacity 1 / seats 0` + 1 registration) |
| `T-FR6-07` given "`FIT3182` capacity 40, seats 0" | `06 §8` | A-16 (`capacity 1`) |
| Failed register on an unknown course always details `COURSE_NOT_FOUND` | `08 NFR4.2` E-03 (implicitly exhaustive) | A-17 (`SEMESTER_NOT_ACTIVE` for a non-active-semester course) |
| Attempts failing validation are not mentioned in the matrix | `08 NFR4.2` (implicitly exhaustive) | A-19 (`VALIDATION_ERROR` rows E-12/E-13) |
| No schema constraints recorded for `course_schedule` | `00 §7.4` (checks stated but not enumerated as a decision) | A-18 |
| `maxCredits` is "always the literal 24" | `05 §2` B3, `00 §9.4` E7 | A-23 (configured `app.registration.max-credits`, default 24, plus `remainingCredits`) |
| "24 credit point limit" in dialog and error copy | `03 §1`, `03 §6`, `09 §5.7`, `09 §9` | A-23 (response-driven limit and remaining credits) |

---

## Deferred (post-Phase 1)

- **A-20 — `course_schedule.day` DB `CHECK` `IN ('MON'…'SUN')` (`00 §7.4`).** A-18 records the four
  constraints enforced at the database level; the day-of-week check is not one of them and is absent
  from the Phase 1 schema. Deferred because the column is written only through the JPA
  `DayOfWeekCode` enum, which cannot produce an out-of-range value, and no raw-SQL write path exists
  (the seeder and test fixtures use the repositories). **Risk:** low — a future native query or bulk
  SQL write would be unguarded. **Owner:** coder-backend; revisit when a non-JPA write path is
  introduced, or before Phase 2. A-18's four constraints are unchanged.

---

## Consequences for the seed and demo

- `FIT3182` is full from startup: `capacity = 1`, one registration owned by Daniel (`32067890`),
  `availableSeats = 0`, C1 holds for all 19 seeded courses.
- Daniel therefore begins with **6 credits** (25 % of the default 24-credit cap); Aisha and Priya
  begin with **0**. No seeded student is near the limit, so `CREDIT_LIMIT_EXCEEDED` tests must build
  their own credits rather than rely on the seed. With `REGISTRATION_MAX_CREDITS=18`, 15 credits is
  the demo "3 remaining" state for FR10.
- `FIT3143` remains the single-free-seat course (`capacity 1`, `availableSeats 1`) used by the
  concurrency tests; `FIT3182` is the always-full course. The two must not be conflated.
- A tester who needs a full course with a *known* incumbent drops Daniel's `FIT3182` registration,
  observes `availableSeats` become 1, then re-registers it. This is the positive control for C1.
- FR8 adds four registration-blocking demo courses: Aisha can register `FIT2004`, `FIT2099` and
  `MTH1030` but is denied `FIT3077` (missing `FIT1047`); Daniel can register all four; Priya is
  denied on every prerequisite course. Prerequisite edges are on `FIT2004`, `FIT2099`, `FIT3077` and
  `MTH1030` only, so all Phase 1 capacity/concurrency courses (`FIT3143`, `FIT3182`, `FIT1045`) and
  every existing Phase 1 test remain unaffected.

---

*Prev: [10 — Test strategy](./10-test-strategy.md) · Back to [00 — Overview](./00-overview.md)*
