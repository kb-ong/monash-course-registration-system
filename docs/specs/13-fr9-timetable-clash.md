# 13 — FR9 Timetable Clash Detection

**Story:** US2, US3 · **Actors:** Student (P1), System (authoritative)
**Endpoint:** `POST /api/registrations` (enforcement); course payloads already carry `schedule`
**Depends on:** [FR2](./02-fr2-view-courses.md), [FR3](./03-fr3-register-course.md),
[FR5](./05-fr5-view-registered.md), [FR6](./06-fr6-capacity.md), [FR7](./07-fr7-registration-period.md),
[FR8](./12-fr8-prerequisites.md).
**Phase:** 2 (second Phase 2 requirement; Phase 1 remains FR1–FR7).

---

## 1. Requirement

> The system shall prevent students from registering for courses with overlapping schedules. A
> registration is rejected when the selected course overlaps with an already registered course, and
> the system displays the conflicting courses and schedules. Registration succeeds when no schedule
> conflict exists.

Source example:

| Course | Day | Time |
| --- | --- | --- |
| CS101 | Monday | 09:00–11:00 |
| MA101 | Monday | 10:00–12:00 |

Registering the second course is blocked.

The check is **server-side and authoritative**. The UI may show class times and may explain a
rejection, but it never decides eligibility.

---

## 2. Domain model

No schema change. FR9 reads the existing `course_schedule` rows (§7.4) for the requested course and
for every registration the authenticated student holds **in the active semester**:

- A course has 1..n weekly slots: `day`, `startTime`, `endTime`.
- The comparison is performed on the student's persisted registrations, not on client state.
- Schedules of other students, of prior-semester registrations, and of courses in the catalogue the
  student is not registered in are irrelevant.
- The requested course itself is excluded; a duplicate registration is rejected earlier by FR3.

### 2.1 Contrast with the old NG2 non-goal

Phase 1 declared clash detection a non-goal and the UI merely drew overlapping blocks side by side.
FR9 removes that non-goal: a new **overlap check** runs during registration. The remaining
non-goals are unchanged: no travel-time/buffer rules, no exam timetables, no preference-based
section selection and no automatic timetable optimisation.

---

## 3. Overlap definition (normative)

Two slots overlap when **all** of the following hold:

1. they are on the same `day`;
2. `requested.startTime < registered.endTime`; and
3. `registered.startTime < requested.endTime`.

Intervals are **half-open** `[start, end)`. Therefore:

- `09:00–11:00` and `10:00–12:00` **overlap** → blocked (the requirement's example).
- `09:00–11:00` and `11:00–13:00` **touch but do not overlap** → allowed.
- The same times on a different day never overlap.
- A course with several slots is compared slot-by-slot; **one** overlapping pair is enough to block.
- No buffer time, travel distance or building change is considered.

---

## 4. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | Every `POST /api/registrations` compares the requested course's slots with all slots of the student's current active-semester registrations. |
| B2 | If at least one pair of slots overlaps (§3), the registration is rejected with **409 `TIMETABLE_CLASH`**. No registration row is created and no seat is consumed. |
| B3 | The error message names **every** conflicting registered course and its overlapping slot(s), ordered by course `code` ASC and slot day/start time ASC. Example: `Cannot register for FIT1051: timetable clash with FIT2004 (TUE 10:00-12:00).` |
| B4 | With no overlap, registration continues through the remaining Phase 1/Phase 2 checks unchanged, and a valid request still returns `201`. |
| B5 | Check order is normative (A-22, extends A-03/A-21): auth → body → period → course exists in the active semester → duplicate → **prerequisites** → **timetable clash** → credit limit → capacity. |
| B6 | A prerequisite rejection is reported before a clash; an ineligible student learns the prerequisite reason first. A clash is reported before the credit-limit and capacity checks, because changing credits or waiting for a seat cannot fix a timetable overlap. |
| B7 | The check applies to every student and every course in the active semester, including courses with no prerequisites. The Phase 1 demo courses (`FIT3143`, `FIT3182`, `FIT1045`) are not exempt. |
| B8 | Only active-semester registrations count. A registration for a prior-semester course (impossible through the API, but possible in fixtures) is not part of the current timetable. |
| B9 | Concurrent overlapping registrations by the same student are serialised with a pessimistic write lock on the student row, so at most one can succeed. The course row lock for capacity is still taken first; no other write path inverts that order. |
| B10 | A clash rejection writes exactly one audit row `REGISTER_REJECTED / FAILURE / TIMETABLE_CLASH` with the resolved `course_code` (NFR4 E-15). |
| B11 | The frozen error envelope is unchanged: the conflicting courses and schedules are carried in `message`, which is safe to display verbatim. Tests assert the code and the referenced codes/times, not exact prose. |
| B12 | The client must not pre-empt the server's clash decision. The register dialog shows the server message in its inline danger alert and stays open, exactly like a prerequisite or credit-limit rejection. |

---

## 5. API surface

### 5.1 Registration rejection

```http
POST /api/registrations
Authorization: Bearer <token>
Content-Type: application/json

{ "courseId": "bbbbbbbb-0000-4000-8000-000000000002" }
```

```http
HTTP/1.1 409 Conflict

{
  "error": {
    "code": "TIMETABLE_CLASH",
    "message": "Cannot register for FIT1051: timetable clash with FIT2004 (TUE 10:00-12:00).",
    "timestamp": "2026-11-19T12:30:00+08:00"
  }
}
```

- HTTP `409` — the request is understood, but the student's current timetable forbids it.
- `TIMETABLE_CLASH` joins the stable error catalogue (`00 §9.2`).
- Multiple conflicts are comma-separated: `… timetable clash with FIT1045 (MON 10:00-12:00), FIT2004 (TUE 10:00-12:00).`
- A conflicting course with more than one overlapping slot lists them inside one pair of
  parentheses: `… timetable clash with FIT1045 (MON 10:00-12:00, WED 14:00-16:00).`

### 5.2 Course payload

The existing `schedule: [{day,startTime,endTime,location}]` field is the display source for class
times and for the conflict details. No payload field is added or changed by FR9.

### 5.3 Success

Unchanged from FR3/FR8: `201` with `registrationId`, `message`, the course object and the remaining
seat count.

---

## 6. Audit log events

| Case | `action` | `outcome` | `detail` | HTTP |
| --- | --- | --- | --- | --- |
| No overlap, registration succeeds | `REGISTER` | `SUCCESS` | `null` | 201 |
| At least one overlap | `REGISTER_REJECTED` | `FAILURE` | `TIMETABLE_CLASH` | 409 |

`student_id` = JWT subject; `course_code` = the resolved requested course. The failure row survives
rollback through the existing `REQUIRES_NEW` audit mechanism (NFR4). The event matrix is extended to
E-15 in [08 NFR4.2](./08-non-functional.md).

---

## 7. UI

### 7.1 Register dialog (catalogue quick action and course detail)

- The dialog already shows the course's first class time and credit impact.
- On `409 TIMETABLE_CLASH` it **stays open** and shows the server message in the inline danger alert,
  so the student sees the conflicting course codes and times. No toast claims success and no local
  state marks the course registered.
- The dialog does not hide the register action merely because local schedule data looks
  overlapping: the server is the authority, and client data can be stale. This mirrors the FR6
  philosophy that seat counts are advisory.

### 7.2 Course detail

The "Assessment & prerequisites" section states that timetable clashes are checked at registration
and that a conflicting class is blocked and named. The class-times section remains the pre-check
view.

### 7.3 Schedule

Overlapping blocks, if any exist in read data, are still drawn side by side and marked `Clash`. The
explanatory note now reflects FR9: registration blocks new clashes, so an overlap in the timetable
means the timetable changed after it was booked.

### 7.4 Catalogue

Cards are unchanged. No client-side clash badge blocks a course; eligibility remains a server
decision.

---

## 8. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR9-01 | negative | student holds `FIT2004` TUE 10:00–12:00; requests `FIT1051` TUE 09:00–11:00 | register `FIT1051` | 409 `TIMETABLE_CLASH`; message names `FIT2004` and `TUE 10:00-12:00`; no row; seat unchanged |
| T-FR9-02 | happy | conflict course is on a different day at the same time | register | 201 |
| T-FR9-03 | edge | intervals touch (`…10:00`, `10:00…`) | register each side | 201; a one-minute overlap on either side is 409 |
| T-FR9-04 | edge | registered course has two slots, one overlapping | register a two-slot course | 409; only the overlapping slot is named |
| T-FR9-05 | happy | registered course has two slots, neither overlapping | register | 201 |
| T-FR9-06 | negative | two registered courses both overlap the request | register | 409; both codes named, `code` ASC |
| T-FR9-07 | scoping | the conflicting registration belongs to another student or to a prior semester | register | 201 |
| T-FR9-08 | ordering | student is also over the 24-credit cap | register an overlapping course | `TIMETABLE_CLASH`, not `CREDIT_LIMIT_EXCEEDED` |
| T-FR9-09 | ordering | requested course also has an unmet prerequisite | register | `PREREQUISITE_NOT_MET`, not `TIMETABLE_CLASH` |
| T-FR9-10 | audit | clash rejection | inspect audit | exactly one `REGISTER_REJECTED/FAILURE/TIMETABLE_CLASH` row with `course_code` |
| T-FR9-11 | concurrency | one student submits two overlapping courses simultaneously | two concurrent `POST`s | exactly one `201` and one 409 `TIMETABLE_CLASH`; one registration row |

---

## 9. Acceptance criteria

**FR9-AC1** — Given a student already registered in a course, when they register for a course whose
weekly schedule overlaps it, then `409 TIMETABLE_CLASH` is returned, no registration is created and
no seat is consumed.
**FR9-AC2** — Given a clash rejection, then the response message names every conflicting course and
its overlapping day/time.
**FR9-AC3** — Given a requested course whose schedule does not overlap any of the student's current
registrations, when the student registers and all other checks pass, then `201` is returned.
**FR9-AC4** — Given intervals that only touch at a boundary, then registration is allowed.
**FR9-AC5** — Given any clash rejection, then exactly one audit row
`REGISTER_REJECTED / FAILURE / TIMETABLE_CLASH` exists for the JWT subject and requested course.

---

## 10. Seed and demo

No seed change is required: the Phase 1 §8.2 schedule already contains overlapping pairs that
demonstrate FR9.

| Demo path | Steps | Result |
| --- | --- | --- |
| Aisha — blocked | register `FIT2004` (TUE 10:00–12:00), then `FIT1051` (TUE 09:00–11:00) | second request: 409 `TIMETABLE_CLASH`, message names `FIT2004` and `TUE 10:00-12:00` |
| Aisha — allowed | register `FIT2004` (TUE 10:00–12:00), then `ENG1001` (TUE 14:00–16:00) | 201; the slots do not overlap |
| Aisha — no overlap | register `FIT1045` (MON 10:00–12:00), then `FIT2101` (MON 14:00–16:00) | 201 |
| any student — multi-slot | register `FIT2099` (WED 10:00–12:00), then `FIT3077` (WED 10:00–12:00) | 409; `FIT2099` named (prerequisites permitting) |

Other inherent seed overlaps: `FIT3143` FRI 09:00–11:00 with `MTH1010` FRI 10:00–12:00. No seeded
pair meets exactly at a boundary; T-FR9-03 covers that rule. The demo student Aisha can reach the
`FIT2004`/`FIT1051` path with her seeded completion history.

---

## 11. Non-goals and limitations

- No travel/buffer time between consecutive classes; touching intervals are allowed.
- No exam, tutorial-group or campus-distance modelling.
- No re-validation of already-existing overlapping registrations on read or startup; the check runs
  on new registrations only. There is no administration path that rewrites schedules.
- The error envelope carries no structured conflict array; conflicts travel in `message`. A future
  contract change would add fields, which this requirement does not need.

---

*Prev: [12 — FR8 Prerequisites](./12-fr8-prerequisites.md) · Back to [00 — Overview](./00-overview.md)*
