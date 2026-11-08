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

---

## Deferred (post-Phase 1)

- **`course_schedule.day` DB `CHECK` `IN ('MON'…'SUN')` (`00 §7.4`).** A-18 records the four
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
- Daniel therefore begins with **6 credits** (25 % of the 24-credit cap); Aisha and Priya begin with
  **0**. No seeded student is near the limit, so `CREDIT_LIMIT_EXCEEDED` tests must build their own
  credits rather than rely on the seed.
- `FIT3143` remains the single-free-seat course (`capacity 1`, `availableSeats 1`) used by the
  concurrency tests; `FIT3182` is the always-full course. The two must not be conflated.
- A tester who needs a full course with a *known* incumbent drops Daniel's `FIT3182` registration,
  observes `availableSeats` become 1, then re-registers it. This is the positive control for C1.

---

*Prev: [10 — Test strategy](./10-test-strategy.md) · Back to [00 — Overview](./00-overview.md)*
