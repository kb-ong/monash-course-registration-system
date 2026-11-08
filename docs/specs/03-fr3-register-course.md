# FR3 — Register for Course

**Story:** US2, US4 · **Actors:** Student (P1), System
**Endpoints:** `POST /api/registrations`
**Depends on:** [FR1](./01-fr1-student-login.md), [FR2](./02-fr2-view-courses.md), [FR6](./06-fr6-capacity.md), [FR7](./07-fr7-registration-period.md).

---

## 1. Requirement

> A signed-in student can register for a course in the active semester, once, while the registration
> window is open, provided they stay within the 24-credit limit and a seat is available. On success,
> exactly one seat is consumed and the student immediately sees the course in their registrations.

---

## 2. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | Request body is `{"courseId": "<uuid>"}`. `courseId` is required and must parse as a UUID. |
| B2 | The student identity comes **only** from the JWT subject. Any `studentId` in the body is ignored (and must not be honoured). |
| B3 | Checks run in this order (normative, A-03): auth → body → **period** → **course exists in the active semester** (A-17) → **duplicate** → **credit limit** → **capacity**. |
| B4 | Period closed → 403 `REGISTRATION_CLOSED`. No writes, no seat change. |
| B5 | Unknown course → 404 `COURSE_NOT_FOUND`. |
| B6 | Already registered (same student + course) → 409 `ALREADY_REGISTERED`. Idempotent retries must not create a second row. |
| B7 | `totalCredits + course.creditHours > 24` → 409 `CREDIT_LIMIT_EXCEEDED`. Landing exactly on 24 is allowed. |
| B8 | `availableSeats = 0` → 409 `COURSE_FULL`, no record created, seat count unchanged. |
| B9 | Success → 201 with `registrationId`, `message`, the full `course` object (seats already decremented) and `availableSeats`. |
| B10 | Seat decrement and registration insert happen in **one transaction** under a pessimistic write lock on the course row (see FR6). |
| B11 | The response is not cacheable; `Cache-Control: no-store`. |
| B12 | A `DataIntegrityViolationException` on the unique key is mapped to 409 `ALREADY_REGISTERED`, never 500. |
| B13 | Double-submit from the UI must be prevented client-side (button disabled from click until response), but the server is still the authority. |
| B14 | Every attempt (success or failure) writes exactly one audit row (NFR4). |

---

## 3. Validation rules

| Field | Rule | Violation |
| --- | --- | --- |
| auth | valid bearer token | 401 `UNAUTHORIZED` |
| `courseId` | present, not null, valid UUID string | 400 `VALIDATION_ERROR` |
| body | object with no unknown-field requirement; extra fields ignored | — |
| period | `startAt ≤ serverTime ≤ endAt` | 403 `REGISTRATION_CLOSED` |
| course | exists | 404 `COURSE_NOT_FOUND` |
| uniqueness | no existing `(student, course)` | 409 `ALREADY_REGISTERED` |
| credits | `sum + creditHours ≤ 24` | 409 `CREDIT_LIMIT_EXCEEDED` |
| seats | `availableSeats ≥ 1` | 409 `COURSE_FULL` |

`VALIDATION_ERROR` message must name the offending field, e.g.
`"courseId must be a valid UUID."`

---

## 4. API surface used

`POST /api/registrations`

```http
POST /api/registrations
Authorization: Bearer <token>
Content-Type: application/json

{ "courseId": "6f1c2f7e-0d5a-4a3e-9c1b-2d7c9a1e4b10" }
```

```http
HTTP/1.1 201 Created

{
  "registrationId": "1b7e5c44-6c2f-4c9e-8a11-3f0d2c9b7a55",
  "message": "Registered for FIT2004 Algorithms and Data Structures.",
  "course": {
    "id": "6f1c2f7e-0d5a-4a3e-9c1b-2d7c9a1e4b10",
    "code": "FIT2004",
    "name": "Algorithms and Data Structures",
    "creditHours": 6,
    "instructor": "Dr Wei Chen",
    "capacity": 80,
    "availableSeats": 79,
    "semester": "2026-S2",
    "faculty": "Information Technology",
    "description": "…",
    "schedule": [{ "day": "TUE", "startTime": "10:00", "endTime": "12:00", "location": "…" }]
  },
  "availableSeats": 79
}
```

Failure examples:

```json
{"error":{"code":"COURSE_FULL","message":"FIT3143 has no available seats.","timestamp":"2026-11-08T11:26:41+08:00"}}
```

```json
{"error":{"code":"CREDIT_LIMIT_EXCEEDED","message":"Adding FIT3077 (6 credit points) would exceed the 24 credit point limit.","timestamp":"2026-11-08T11:26:41+08:00"}}
```

---

## 5. Audit log events

| Outcome | `action` | `outcome` | `detail` | When |
| --- | --- | --- | --- | --- |
| Success | `REGISTER` | `SUCCESS` | `null` | after commit |
| Closed period | `REGISTER_REJECTED` | `FAILURE` | `REGISTRATION_CLOSED` | B4 |
| Unknown course | `REGISTER_REJECTED` | `FAILURE` | `COURSE_NOT_FOUND` | B5 |
| Duplicate | `REGISTER_REJECTED` | `FAILURE` | `ALREADY_REGISTERED` | B6 |
| Credit limit | `REGISTER_REJECTED` | `FAILURE` | `CREDIT_LIMIT_EXCEEDED` | B7 |
| Full | `REGISTER_REJECTED` | `FAILURE` | `COURSE_FULL` | B8 |

`student_id` = JWT subject (always available). `course_code` = resolved code, or `null` when the course
could not be resolved. Audit rows are written in the **same transaction** as the registration for
success, and in a **new transaction** (`REQUIRES_NEW`) for failures so a rollback does not erase the
evidence — see NFR4.

---

## 6. UI

**Where registration is triggered**

1. Catalog card → primary `Register` button (quick action).
2. Course Detail → sticky `Register` action panel.

**Confirmation dialog** (`ConfirmDialog`, [09 §5.7](./09-ui-design-system.md#57-modal--dialog)):

```
┌──────────────────────────────────────────────┐
│  Register for FIT2004?                    ✕  │
│  ── gold rule ──                             │
│  Algorithms and Data Structures              │
│  6 credit points · Tue 10:00–12:00           │
│  Clayton, Building 25, Rm 125                │
│                                              │
│  This will bring you to 12 of 24 credits.    │
│                                              │
│              [ Cancel ]  [ Confirm register ]│
└──────────────────────────────────────────────┘
```

States:

| State | Trigger | Rendering |
| --- | --- | --- |
| Enabled | seats > 0, period open, not registered | primary button |
| Disabled — full | `availableSeats = 0` | button disabled, label "Full", tooltip "No seats available" |
| Disabled — closed | `period.open = false` | button disabled, label "Registration closed" |
| Disabled — already | course present in `/api/registrations` | label "Registered ✓" (lucide `Check`), not clickable |
| Pending | request in flight | button spinner, dialog buttons disabled, `aria-busy` |
| Success | 201 | dialog closes, toast "Registered for FIT2004" + seat meter animates down, card badge becomes "Registered" |
| Error — full | 409 `COURSE_FULL` | dialog closes, toast (danger) "FIT3143 just filled up. No seat was taken." + card refreshes to FULL |
| Error — credit | 409 `CREDIT_LIMIT_EXCEEDED` | dialog stays open, inline danger alert "That would take you to 30 credits. Your limit is 24." |
| Error — closed | 403 `REGISTRATION_CLOSED` | dialog closes, page-wide banner switches to "Registration closed", toast info |
| Error — duplicate | 409 `ALREADY_REGISTERED` | dialog closes, toast info "You're already registered in this course." card marked registered |
| Error — network | no response | dialog stays open, inline "Couldn't reach the server. Try again." with Retry |

After any success the SPA invalidates and refetches `GET /api/registrations` and the visible course list
so seat counts and the credit meter are consistent.

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR3-01 | happy | Aisha, period open, `FIT1045` seats 120 | `POST /api/registrations {courseId: fit1045}` | 201; `registrationId` is a UUID; `course.code=FIT1045`; `availableSeats=119`; `course.availableSeats=119`; `message` contains `FIT1045` |
| T-FR3-02 | happy | as T-FR3-01, then persist check | `GET /api/registrations` | 200; one item whose `course.code=FIT1045`; `totalCredits=6` |
| T-FR3-03 | negative | no token | `POST /api/registrations` | 401 `UNAUTHORIZED`; no registration created; no audit row with action `REGISTER` |
| T-FR3-04 | negative | period closed (clock outside window) | `POST` valid body | 403 `REGISTRATION_CLOSED`; `availableSeats` unchanged; audit `REGISTER_REJECTED/FAILURE/REGISTRATION_CLOSED` |
| T-FR3-05 | negative | `courseId` = `"not-a-uuid"` | `POST` | 400 `VALIDATION_ERROR`; message mentions `courseId` |
| T-FR3-06 | negative | well-formed unknown UUID | `POST` | 404 `COURSE_NOT_FOUND`; audit detail `COURSE_NOT_FOUND` |
| T-FR3-07 | negative | already registered in `FIT1045` | `POST` again | 409 `ALREADY_REGISTERED`; exactly 1 registration row; `availableSeats` unchanged (119) |
| T-FR3-08 | edge | student has 18 credits | register a 6cp course | 201 — total becomes exactly 24 |
| T-FR3-09 | negative | student has 20 credits | register a 6cp course | 409 `CREDIT_LIMIT_EXCEEDED`; no row; seats unchanged |
| T-FR3-10 | negative | `FIT3182` seeded full | register it | 409 `COURSE_FULL`; no row; `availableSeats` still 0 |
| T-FR3-11 | concurrency | `FIT3143` capacity 1, 5 students | 5 concurrent `POST`s | exactly one 201, four 409 `COURSE_FULL`; final `availableSeats=0`; exactly 1 registration row (see T-FR6-03) |
| T-FR3-12 | security | Aisha's token, body includes `{"courseId": …, "studentId":"32067890"}` | `POST` | 201 attributed to `32012345`; Daniel has no registration (body field ignored) |
| T-FR3-13 | edge | duplicate submit of the *same* request twice, sequentially | 2 × `POST` | first 201, second 409 `ALREADY_REGISTERED`; one row only |

---

## 8. Acceptance criteria

**FR3-AC1** — Given an open period and a course with seats, when a student registers, then 201 is
returned, one registration exists, and `availableSeats` decreases by exactly one.
**FR3-AC2** — Given the same student and course twice, when the second request arrives, then 409
`ALREADY_REGISTERED` and no second row exists.
**FR3-AC3** — Given the addition would exceed 24 credits, when the student registers, then 409
`CREDIT_LIMIT_EXCEEDED`, no row, no seat change.
**FR3-AC4** — Given a course with zero seats, when the student registers, then 409 `COURSE_FULL`, no
row, no seat change.
**FR3-AC5** — Given a closed period, when any registration is attempted, then 403
`REGISTRATION_CLOSED` regardless of seats or credits.
**FR3-AC6** — Given a `studentId` in the request body, when it differs from the token subject, then it
is ignored and the token subject owns the registration.
**FR3-AC7** — Given any attempt, success or failure, then exactly one audit row is written with the
matching action/outcome/detail.
**FR3-AC8** — Given a success, when the catalogue is refetched, then the new seat count matches the
201 response.

---

*Prev: [02 FR2](./02-fr2-view-courses.md) · Next: [04 FR4](./04-fr4-drop-course.md)*
