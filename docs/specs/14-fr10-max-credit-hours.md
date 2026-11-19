# 13 — FR10 Maximum Credit-Hour Validation

**Story:** US2, US3, US4 · **Actors:** Student (P1), System (authoritative)
**Endpoints:** `POST /api/registrations` (enforcement), `GET /api/registrations` (total, limit, remaining)
**Depends on:** [FR3](./03-fr3-register-course.md), [FR5](./05-fr5-view-registered.md),
[FR8](./12-fr8-prerequisites.md).
**Phase:** 2 (second Phase 2 requirement; Phase 1 remains FR1–FR7, FR8 remains unchanged).

---

## 1. Requirement

> The system enforces a maximum credit-hour limit per semester. It calculates the student's total
> registered credit hours, rejects a registration that would take the total above the configured
> semester limit, and tells the student how many credits remain.
>
> Example: semester limit 18, already registered 15, new course 4 → total 19 → registration rejected.

The limit is **configuration, not a code constant**. The server is the only authority; the UI uses the
server's `totalCredits`, `maxCredits` and `remainingCredits` and never substitutes its own number.

---

## 2. Configuration (normative)

| Property | Environment variable | Default | Rule |
| --- | --- | --- | --- |
| `app.registration.max-credits` | `REGISTRATION_MAX_CREDITS` | `24` | Positive integer. Applies to the configured active semester. |

- The default stays **24** so every Phase 1 registration scenario and the frozen API contract keep
  their existing behaviour unless an operator opts in.
- To reproduce the FR10 example, start the backend with `REGISTRATION_MAX_CREDITS=18` (or pass
  `--app.registration.max-credits=18`). No source change or database edit is required.
- A value below `1` fails startup through the existing `@Validated` configuration binding
  (`app.registration.max-credits must be at least 1`), matching NFR3-A5. A silently ignored limit is
  a defect.
- There is one limit for the active semester; per-program or per-student overrides are out of scope
  (AS5, NG3).

---

## 3. Domain and behaviour rules

### 3.1 Totals

`totalCredits` is the server-side sum of `course.creditHours` over the student's registrations in the
active semester. It is never supplied by the client and is never recomputed as authority in the UI.
`remainingCredits` is `max(0, maxCredits − totalCredits)`; it is zero rather than negative because a
registration that would go over the limit is rejected before it is written.

### 3.2 Rules

| # | Rule |
| --- | --- |
| B1 | Every `POST /api/registrations` evaluates `totalCredits + course.creditHours` against the configured `maxCredits`. |
| B2 | Registration is allowed when `totalCredits + course.creditHours <= maxCredits`. Landing exactly on the limit is allowed. |
| B3 | When the addition would exceed the limit, the server returns **409 `CREDIT_LIMIT_EXCEEDED`**, creates no registration row, and changes no seat count. |
| B4 | The 409 message names the configured limit **and** the credits remaining before the attempt, e.g. `Adding FIT3179 (6 credit points) would exceed the 18 credit point limit. You have 3 credit points remaining.` The message is student-facing but not contractual; clients branch on the code. |
| B5 | Check order is unchanged (A-03, A-21, A-22, A-23): auth → body → period → course exists in the active semester → duplicate → prerequisites → **timetable clash** → **credit limit** → capacity. |
| B6 | A rejection writes exactly one audit row `REGISTER_REJECTED / FAILURE / CREDIT_LIMIT_EXCEEDED` with the resolved course code (NFR4 E-05). |
| B7 | `GET /api/registrations` returns `totalCredits`, the configured `maxCredits` and `remainingCredits`. The triple is always internally consistent: `remainingCredits = max(0, maxCredits − totalCredits)`. |
| B8 | Dropping a registration reduces `totalCredits` by that course's `creditHours` and increases `remainingCredits` on the next read. |
| B9 | The client must not hardcode `24`. All credit arithmetic in the SPA comes from the registrations response; before that response arrives the UI omits the limit/remaining rather than guessing. |
| B10 | The UI pre-empts an over-limit registration where it already knows the total (disabled register action with a reason), but the server decision remains authoritative when data is stale. |
| B11 | The UI always shows the remaining available credits in student-facing credit contexts: dashboard ring, catalogue credit summary, course-detail action note, and register dialog. |
| B12 | A 409 `CREDIT_LIMIT_EXCEEDED` keeps the register dialog open and shows the server message; no success toast is shown. |
| B13 | The limit applies to the active semester only. Prior-semester registrations are not returned by E7 and do not consume the limit. |

---

## 4. API surface

### 4.1 `GET /api/registrations`

```json
{
  "semester": "2026-S2",
  "totalCredits": 15,
  "maxCredits": 18,
  "remainingCredits": 3,
  "items": [
    {
      "registrationId": "1b7e5c44-6c2f-4c9e-8a11-3f0d2c9b7a55",
      "registeredAt": "2026-11-08T03:26:41Z",
      "course": { "…": "full course object as FR2/FR5" }
    }
  ]
}
```

- `maxCredits` is the configured `app.registration.max-credits` value (default `24`).
- `remainingCredits = max(0, maxCredits − totalCredits)`.
- Empty registrations still return the configured limit and remaining:
  `{"semester":"2026-S2","totalCredits":0,"maxCredits":18,"remainingCredits":18,"items":[]}`.

### 4.2 `POST /api/registrations` rejection

```http
POST /api/registrations
Authorization: Bearer <token>
Content-Type: application/json

{ "courseId": "bbbbbbbb-0000-4000-8000-000000000009" }
```

```http
HTTP/1.1 409 Conflict

{
  "error": {
    "code": "CREDIT_LIMIT_EXCEEDED",
    "message": "Adding FIT3179 (6 credit points) would exceed the 18 credit point limit. You have 3 credit points remaining.",
    "timestamp": "2026-11-19T15:30:00+08:00"
  }
}
```

- HTTP `409`: the request is understood, but the academic state forbids it.
- No registration row; no seat decrement; the course's `availableSeats` is unchanged.
- The error envelope is unchanged (00 §9.1); `CREDIT_LIMIT_EXCEEDED` was already in the stable
  catalogue.

### 4.3 Success

Unchanged from FR3. A registration that lands on the limit returns `201`; the next read shows
`remainingCredits: 0`.

---

## 5. Audit log events

| Case | `action` | `outcome` | `detail` | HTTP |
| --- | --- | --- | --- | --- |
| Within the limit | `REGISTER` | `SUCCESS` | `null` | 201 |
| Would exceed the limit | `REGISTER_REJECTED` | `FAILURE` | `CREDIT_LIMIT_EXCEEDED` | 409 |

`student_id` = JWT subject; `course_code` = the resolved requested course. The failure row survives
rollback through the existing `REQUIRES_NEW` audit mechanism (NFR4). This is E-05 in
[08 NFR4.2](./08-non-functional.md); no new audit vocabulary is introduced.

---

## 6. UI

### 6.1 Dashboard

- The credit ring uses `maxCredits` from E7; there is no literal `24`.
- Below the ring: `{remainingCredits} credits remaining`, or `Credit limit reached` at the limit.
- Hero copy: `You're registered in {n} courses and {totalCredits} of {maxCredits} credit points.`
- "Term at a glance" shows the configured **Credit limit** and the **Credits remaining**.

### 6.2 Catalogue

A credit summary strip under the page header shows `{totalCredits} of {maxCredits} credit points
registered` and `{remainingCredits} credits remaining`. It turns amber from 75 % of the limit and
danger above the limit. Register actions for courses that would exceed the limit are disabled with
the reason `That would take you to {next} of {max} credits. You have {remaining} credits remaining.`
A 3-credit course remains actionable when it lands exactly on the limit.

### 6.3 Course detail

The sticky registration panel says
`Adding this brings you to {next} of {max} credits — {remainingAfter} credits would remain.`
When the course does not fit, the button reads `Credit limit reached` and the warning names the
current remaining credits. The server remains authoritative if the page data is stale.

### 6.4 Register dialog

```
┌──────────────────────────────────────────────┐
│  Register for FIT3179?                    ✕  │
│  ── gold rule ──                             │
│  Data Visualisation                          │
│  6 credit points · Thu 10:00–12:00           │
│  Caulfield, Building B, Rm 310               │
│                                              │
│  This will bring you to 18 of 18 credits.    │
│  You would have 0 credits remaining.         │
│                                              │
│              [ Cancel ]  [ Confirm register ]│
└──────────────────────────────────────────────┘
```

- Remaining credits are shown before confirmation, not only after a rejection.
- When `next > max`, the remaining line becomes a danger warning, the dialog says the course does not
  fit, and Confirm is disabled. If the server still rejects (stale total), the server message is shown
  in the inline alert, the dialog stays open and the registrations are refetched; no success toast.
- On success the existing FR3/FR8 flow is unchanged.

### 6.5 My schedule

The credit ring, the `{total} / {max} CP` label and the print header all use the configured limit.
"Near limit" is 75 % of the configured limit (18 of 24; with 3/6-credit courses at an 18 limit the amber state starts at 15). The caption is
`{remaining} credits left`; at the limit it is `Credit limit reached`.

### 6.6 Copy

| Context | Text |
| --- | --- |
| Summary | `{total} of {max} credit points registered` |
| Remaining | `{remaining} credits remaining` (singular `1 credit remaining`) |
| At limit | `Credit limit reached` |
| Dialog before confirm | `This will bring you to {next} of {max} credits.` |
| Dialog remaining | `You would have {after} credits remaining.` |
| Dialog over limit | `You have {remaining} credits remaining, so this course does not fit within your limit.` |
| Server rejection | The 409 `message` (names the limit and remaining credits) |
| Blocked action reason | `That would take you to {next} of {max} credits. You have {remaining} credits remaining.` |

Copy is sentence case, no exclamation marks, no emoji (NFR5-U8).

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR10-01 | negative | `max-credits=18`; student has 15 credits | register a 6cp course | 409 `CREDIT_LIMIT_EXCEEDED`; message names `18` and `3 credit points remaining`; no row; seats unchanged; audit E-05 |
| T-FR10-02 | edge | `max-credits=18`; student has 15 credits | register a 3cp course | 201; next E7 read is `totalCredits=18`, `maxCredits=18`, `remainingCredits=0` |
| T-FR10-03 | negative | student is exactly at the configured limit | register any positive-credit course | 409 `CREDIT_LIMIT_EXCEEDED`; message names `0 credit points remaining`; no row |
| T-FR10-04 | happy | default configuration | `GET /api/registrations` with two 6cp courses | `totalCredits=12`, `maxCredits=24`, `remainingCredits=12` |
| T-FR10-05 | api | `max-credits=18`, no registrations | `GET /api/registrations` | 200; `maxCredits=18`, `remainingCredits=18`, `items=[]` |
| T-FR10-06 | edge | student drops a registered course | `DELETE`, then `GET /api/registrations` | total falls by that course's credits; remaining rises by the same amount |
| T-FR10-07 | config | `app.registration.max-credits=0` | start the context | startup fails fast with a message naming `app.registration.max-credits` |
| T-FR10-08 | ui | student at 15/18; a 6cp course and a 3cp course are visible | open catalogue, course detail and the register dialogs | summary/remaining show `15 of 18` and `3 credits remaining`; 6cp action is `Credit limit reached`; 3cp dialog shows `18 of 18` / `0 credits remaining`; no literal `24` |

Automated coverage for T-FR10-01…07 lives in `MaxCreditConfigTest`, `RegistrationViewTest`,
`DropControllerTest` and `StartupValidationTest`; T-FR10-08 is manual with the before/after
screenshots in the PR.

---

## 8. Acceptance criteria

**FR10-AC1** — Given a student's active-semester registrations, then the system returns the total
registered credit hours, the configured semester limit and the remaining available credits.
**FR10-AC2** — Given `totalCredits + course.creditHours > maxCredits`, when the student registers,
then 409 `CREDIT_LIMIT_EXCEEDED` is returned, no registration row is created and no seat is consumed.
**FR10-AC3** — Given `totalCredits + course.creditHours <= maxCredits`, when the student registers,
then the registration succeeds, including when the total lands exactly on the limit.
**FR10-AC4** — Given a credit-limit rejection, then the message names the configured limit and the
remaining available credits, and exactly one `REGISTER_REJECTED / FAILURE / CREDIT_LIMIT_EXCEEDED`
audit row exists for the subject and requested course.
**FR10-AC5** — Given the operator sets a different positive `app.registration.max-credits`, then
enforcement and all three response fields use that value without a code change; a non-positive value
fails startup.
**FR10-AC6** — Given the SPA renders the dashboard, catalogue, course detail or register dialog, then
the displayed limit and remaining credits come from the registrations response and match the server.

---

## 9. Relationship to earlier specs

- **FR3** keeps its check order and its `CREDIT_LIMIT_EXCEEDED` status; the threshold changes from
  the literal `24` to the configured `maxCredits`.
- **FR5** keeps `totalCredits` and `maxCredits` and gains `remainingCredits`.
- **FR8** is unchanged and still runs before the credit check.
- **09 UI design system** uses the response-driven limit; the wireframe number `24` is illustrative.

---

*Prev: [13 FR9](./13-fr9-timetable-clash.md) · Back to [00 — Overview](./00-overview.md)*
