# 12 — FR8 Prerequisite Validation

**Story:** US2, US4 · **Actors:** Student (P1), System (authoritative)
**Endpoints:** `POST /api/registrations` (enforcement), `GET /api/courses*` (prerequisite display)
**Depends on:** [FR2](./02-fr2-view-courses.md), [FR3](./03-fr3-register-course.md),
[FR6](./06-fr6-capacity.md), [FR7](./07-fr7-registration-period.md).
**Phase:** 2 (first Phase 2 requirement; Phase 1 remains FR1–FR7).

---

## 1. Requirement

> The system verifies that a student has completed every prerequisite course before allowing that
> student to register for a course. A student who has completed all prerequisites registers normally.
> A student missing one or more prerequisites is denied and is shown exactly which course(s) are
> missing.

The check is **server-side and authoritative**. The UI may display prerequisite information, but it
never decides eligibility.

---

## 2. Domain model

FR8 adds two join tables. They are deliberately separate: a prerequisite relationship is catalogue
data, while completion is a student's academic history.

### 2.1 `course_prerequisite`

| Column | Type | Constraints | Notes |
| --- | --- | --- | --- |
| `course_id` | `UUID` | FK → `course.id`, not null | the course being registered for |
| `prerequisite_course_id` | `UUID` | FK → `course.id`, not null | the course that must be completed |

**Unique constraint `uk_course_prerequisite (course_id, prerequisite_course_id)`.** A course cannot
be its own prerequisite (`Course.addPrerequisite` rejects it). Longer cycles are a catalogue-data
quality concern of the (out-of-scope) administration path; the registration check does not traverse
the graph, so a cycle cannot cause a loop.

### 2.2 `student_completed_course`

| Column | Type | Constraints | Notes |
| --- | --- | --- | --- |
| `student_id` | `UUID` | FK → `student.id`, not null | |
| `course_id` | `UUID` | FK → `course.id`, not null | the completed course row |

**Unique constraint `uk_student_completed_course (student_id, course_id)`.** There is no grade,
mark or completion date in Phase 2; presence of the row is the definition of "completed". The data is
seeded (A-21): there is no student-records administration UI.

### 2.3 What "completed" means

- A completion row is the **only** evidence. `registration` rows are current-semester enrolments and
  never count as completion, even after the registration exists.
- Completions may reference a prior-semester course row (`2026-S1`) or any other `course` row; the
  check compares course ids, not codes, so a prerequisite and its completion must point at the same
  catalogue row.
- Dropping a current registration does not remove history. Only the (out-of-scope) records process
  can change completion history.

---

## 3. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | Every `POST /api/registrations` evaluates the requested course's prerequisite set against the authenticated student's completion history. |
| B2 | The registration is allowed only when **every** prerequisite course id is present in the student's completion history. |
| B3 | If one or more prerequisites are missing, the server returns **409 `PREREQUISITE_NOT_MET`**, creates no registration row, and changes no seat count. |
| B4 | The error message names **every** missing prerequisite code, sorted `code` ASC, e.g. `Cannot register for FIT3077: missing prerequisites FIT1008, FIT1047.` The frozen error envelope is unchanged (00 §9.1); the message is the student-facing display channel. |
| B5 | Check order is normative and inserts prerequisites into the Phase 1 order (A-03, A-21): auth → body → period → course exists in the active semester (A-17) → duplicate → **prerequisites** → credit limit → capacity. |
| B6 | A course with an empty prerequisite set is unaffected: registration behaves exactly as in Phase 1. |
| B7 | A course payload carries its prerequisites as `prerequisites: [{id, code, name}]`, ordered `code` ASC. The field is required and is `[]` when there are none. |
| B8 | A prerequisite rejection writes exactly one audit row: `REGISTER_REJECTED / FAILURE / PREREQUISITE_NOT_MET`, with the resolved `course_code` (NFR4 E-14). |
| B9 | The capacity lock and atomic decrement are unchanged; the prerequisite check runs before the capacity check inside the same transaction. |
| B10 | The client must not pre-empt the server's prerequisite decision. The register dialog displays the server message when a 409 `PREREQUISITE_NOT_MET` arrives and stays open, exactly like a credit-limit rejection. |

---

## 4. API surface

### 4.1 Course payload (E4 catalogue array, E5 detail, E7/E8 embedded course)

Every course object gains one additive field:

```json
{
  "id": "bbbbbbbb-0000-4000-8000-000000000004",
  "code": "FIT2004",
  "name": "Algorithms and Data Structures",
  "creditHours": 6,
  "capacity": 80,
  "availableSeats": 80,
  "semester": "2026-S2",
  "faculty": "Information Technology",
  "description": "…",
  "schedule": [{ "day": "TUE", "startTime": "10:00", "endTime": "12:00", "location": "…" }],
  "prerequisites": [
    {
      "id": "cccccccc-0000-4000-8000-000000000001",
      "code": "FIT1008",
      "name": "Introduction to Computer Science"
    }
  ]
}
```

`prerequisites` is ordered `code` ASC. `id` is the prerequisite course row id (used only as a stable
React key / future detail link).

### 4.2 Registration rejection

```http
POST /api/registrations
Authorization: Bearer <token>
Content-Type: application/json

{ "courseId": "bbbbbbbb-0000-4000-8000-000000000004" }
```

```http
HTTP/1.1 409 Conflict

{
  "error": {
    "code": "PREREQUISITE_NOT_MET",
    "message": "Cannot register for FIT2004: missing prerequisite FIT1008.",
    "timestamp": "2026-11-19T12:30:00+08:00"
  }
}
```

- HTTP `409` (the request is understood, but the current academic state forbids it).
- `PREREQUISITE_NOT_MET` joins the stable error catalogue (00 §9.2).
- The message is not contractual; clients branch on the code and may display the message verbatim
  because it contains the missing codes. Tests may assert on code and referenced codes, not exact
  prose.

### 4.3 Success

Unchanged from FR3. A student with all prerequisites completed receives `201` and the normal
`RegisterResponse`; the embedded `course.prerequisites` is present.

---

## 5. Audit log events

| Case | `action` | `outcome` | `detail` | HTTP |
| --- | --- | --- | --- | --- |
| All prerequisites completed | `REGISTER` | `SUCCESS` | `null` | 201 |
| One or more missing | `REGISTER_REJECTED` | `FAILURE` | `PREREQUISITE_NOT_MET` | 409 |

`student_id` = JWT subject; `course_code` = the resolved requested course. The failure row survives
rollback through the existing `REQUIRES_NEW` audit mechanism (NFR4). The event matrix is extended to
E-14 in [08 NFR4.2](./08-non-functional.md).

---

## 6. UI

### 6.1 Course detail

The "Assessment & prerequisites" section lists each prerequisite code and name, or says
"No prerequisites. This unit is open to students in the active semester." when the array is empty.
The section also states that timetable clashes are checked when registering (FR9).

### 6.2 Register dialog

- Before confirming, the dialog shows `Requires FIT1008, FIT1047` when prerequisites exist.
- On `409 PREREQUISITE_NOT_MET` the dialog **stays open** and shows the server message in the inline
  danger alert, so the student sees exactly which course(s) are missing. No toast claims success and
  no local state marks the course registered.
- On success the existing FR3 flow is unchanged.

### 6.3 Catalogue

Cards are unchanged: the catalogue response carries `prerequisites`, but the card does not block or
badge a course from prerequisite data alone. Eligibility remains a server decision, matching the
FR6 philosophy that seat counts are advisory.

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR8-01 | happy | Aisha completed `FIT1008`; `FIT2004` requires it | register `FIT2004` | 201; one row; seats decremented; audit `REGISTER/SUCCESS` |
| T-FR8-02 | negative | Priya completed nothing; `FIT2004` requires `FIT1008` | register `FIT2004` | 409 `PREREQUISITE_NOT_MET`; message names `FIT1008`; no row; seats unchanged |
| T-FR8-03 | negative | `FIT3077` requires `FIT1008` + `FIT1047` | Priya registers; then Aisha (has `FIT1008` only) registers | Priya's message names both; Aisha's names only `FIT1047` |
| T-FR8-04 | happy | Daniel completed both prerequisites | register `FIT3077` | 201 |
| T-FR8-05 | edge | `FIT1045` has no prerequisites | Priya registers | 201 (Phase 1 behaviour preserved) |
| T-FR8-06 | audit | as T-FR8-02 | inspect audit | exactly one `REGISTER_REJECTED/FAILURE/PREREQUISITE_NOT_MET` row with `course_code` |
| T-FR8-07 | ordering | full course with an unmet prerequisite; student over the credit cap | register | `PREREQUISITE_NOT_MET`, not `COURSE_FULL`/`CREDIT_LIMIT_EXCEEDED` |
| T-FR8-08 | ordering | direct existing registration for a course whose prerequisite is unmet | register again | `ALREADY_REGISTERED` (duplicate precedes prerequisites) |
| T-FR8-09 | api | `FIT3077` requires two courses | `GET /api/courses/{id}` and `GET /api/courses?q=FIT2004` | `prerequisites` present, ordered `code` ASC, with id/code/name |
| T-FR8-10 | semantic | Priya registers `PRE1001` in the active semester; `ADV2001` requires it | register `ADV2001` | 409 `PREREQUISITE_NOT_MET` — a current registration is not completion |
| T-FR8-11 | seed | shipped default profile | inspect seed | Aisha `FIT1008,MTH1020`; Daniel `FIT1008,FIT1047`; Priya none; edges exactly as §8.5 |

---

## 8. Acceptance criteria

**FR8-AC1** — Given a course with prerequisites and a student who has completed all of them, when
the student registers, then `201` is returned and one registration exists.
**FR8-AC2** — Given a course with prerequisites and a student missing one or more, when the student
registers, then `409 PREREQUISITE_NOT_MET` is returned, no registration is created and no seat is
consumed.
**FR8-AC3** — Given a rejection, then the response message names every missing prerequisite course
code.
**FR8-AC4** — Given a course with prerequisites, then the course payload lists them with `id`,
`code` and `name`, ordered `code` ASC.
**FR8-AC5** — Given any prerequisite rejection, then exactly one audit row
`REGISTER_REJECTED / FAILURE / PREREQUISITE_NOT_MET` exists for the JWT subject and requested course.
**FR8-AC6** — Given a course with no prerequisites, then registration behaves exactly as in Phase 1.

---

## 9. Seed data

See [00 §8.5](./00-overview.md#85-fr8-prerequisite-graph-and-completion-history-normative).

| Course | Prerequisites |
| --- | --- |
| `FIT2004` | `FIT1008` |
| `FIT2099` | `FIT1008` |
| `FIT3077` | `FIT1008`, `FIT1047` |
| `MTH1030` | `MTH1020` |
| every other course | none |

| Student | Completed courses |
| --- | --- |
| `32012345` Aisha | `FIT1008`, `MTH1020` |
| `32067890` Daniel | `FIT1008`, `FIT1047` |
| `32011111` Priya | none |

Priya is the demo "denied" student; Aisha demonstrates a single missing prerequisite on `FIT3077`;
Daniel demonstrates the all-prerequisites-completed path.

---

*Prev: [11 — Decision Log](./11-decisions.md) · Back to [00 — Overview](./00-overview.md)*
