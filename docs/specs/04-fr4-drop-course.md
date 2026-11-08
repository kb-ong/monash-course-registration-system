# FR4 — Drop Course

**Story:** US3, US4 · **Actors:** Student (P1), System
**Endpoints:** `DELETE /api/registrations/{registrationId}`
**Depends on:** [FR1](./01-fr1-student-login.md), [FR6](./06-fr6-capacity.md), [FR7](./07-fr7-registration-period.md).

---

## 1. Requirement

> A signed-in student can drop one of *their own* registrations while the window is open. The
> registration is deleted, exactly one seat returns to the course, and the credit total drops
> immediately.

---

## 2. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | The path variable is the **registration id**, not the course id. The SPA must use the `registrationId` from `GET /api/registrations`. |
| B2 | Check order (normative): auth → **registration exists** → **ownership** → **period**. |
| B3 | Unknown registration id (or already dropped) → 404 `REGISTRATION_NOT_FOUND`. |
| B4 | Registration belongs to another student → 403 `NOT_OWNER`. The body must not reveal the owner's identity or the course. |
| B5 | Period closed → 403 `REGISTRATION_CLOSED`. |
| B6 | Success → delete the row, `availableSeats = min(capacity, availableSeats + 1)`, return 200 `{message, availableSeats}`. |
| B7 | Decrement/increment is transactional and holds the same pessimistic course lock used by FR3 so a concurrent register/drop cannot lose an update. |
| B8 | Dropping is **not** idempotent: the second call is 404. The UI must therefore disable the action while pending. |
| B9 | Dropping is subject to the same period rule as registering — no "grace period", no admin override. |
| B10 | A drop restores the seat even if the course is now at capacity (clamped, never exceeds `capacity`). |
| B11 | Every attempt writes one audit row: success `DROP/SUCCESS`, failure `DROP/FAILURE` with a detail code (A-02). |
| B12 | Dropping does not cascade to audit rows; history remains. |

---

## 3. Validation rules

| Field | Rule | Violation |
| --- | --- | --- |
| auth | valid bearer token | 401 `UNAUTHORIZED` |
| `registrationId` (path) | valid UUID; row exists | 404 `REGISTRATION_NOT_FOUND` |
| ownership | `registration.student.id` = token subject | 403 `NOT_OWNER` |
| period | open | 403 `REGISTRATION_CLOSED` |

A malformed (non-UUID) path value is treated as **not found** → 404, not 400: paths are opaque
identifiers and this avoids a second error shape. The attempt is still audited — `DROP/FAILURE` with
`detail = VALIDATION_ERROR` (§5) — so it stays distinguishable from an unknown id in the trail.

---

## 4. API surface used

```http
DELETE /api/registrations/1b7e5c44-6c2f-4c9e-8a11-3f0d2c9b7a55
Authorization: Bearer <token>
```

```http
HTTP/1.1 200 OK

{ "message": "Dropped FIT2004 Algorithms and Data Structures.", "availableSeats": 80 }
```

```json
{"error":{"code":"NOT_OWNER","message":"This registration belongs to another student.","timestamp":"2026-11-08T11:26:41+08:00"}}
```

---

## 5. Audit log events

| Case | `action` | `outcome` | `detail` |
| --- | --- | --- | --- |
| Success | `DROP` | `SUCCESS` | `null` |
| Not found | `DROP` | `FAILURE` | `REGISTRATION_NOT_FOUND` |
| Malformed id (non-UUID path value, returned as 404) | `DROP` | `FAILURE` | `VALIDATION_ERROR` |
| Not owner | `DROP` | `FAILURE` | `NOT_OWNER` |
| Closed | `DROP` | `FAILURE` | `REGISTRATION_CLOSED` |

For `REGISTRATION_NOT_FOUND` and for a malformed path id, `course_code` is `null` (unresolvable). For
`NOT_OWNER`, `course_code` **may** be recorded (it is an internal audit trail), but the HTTP response
must not disclose it.

---

## 6. UI

**Screen:** My Schedule (`/schedule`), each row/card has a `Drop` action; also Course Detail when the
student is already registered.

**Confirmation dialog** (destructive variant):

```
┌──────────────────────────────────────────────┐
│  Drop FIT2004?                            ✕  │
│  ── gold rule ──                             │
│  Algorithms and Data Structures              │
│  Tue 10:00–12:00 · 6 credit points           │
│                                              │
│  Your seat will be released immediately and  │
│  may be taken by another student.            │
│                                              │
│              [ Keep it ]  [ Drop course ]    │
└──────────────────────────────────────────────┘
```

`Drop course` uses the danger button; `Keep it` is the default focus target (safe default).

States:

| State | Trigger | Rendering |
| --- | --- | --- |
| Pending | request in flight | both buttons disabled, spinner on danger button |
| Success | 200 | dialog closes, row animates out (200 ms), toast "Dropped FIT2004", credit meter decreases, seat count in list updates |
| Error — not owner | 403 `NOT_OWNER` | toast danger "You can't drop that registration." + list refresh |
| Error — closed | 403 `REGISTRATION_CLOSED` | dialog closes, page banner switches to closed, toast info |
| Error — gone | 404 | toast info "That registration no longer exists.", list refresh |
| Error — network | no response | dialog stays open, inline retry |

Guard: the dialog is only reachable when `period.open = true`; otherwise the `Drop` action renders
disabled with a tooltip "Registration is closed".

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR4-01 | happy | Aisha registered in `FIT1045` (seats 119) | `DELETE /api/registrations/{id}` | 200; `availableSeats=120`; `message` names `FIT1045` |
| T-FR4-02 | happy | after T-FR4-01 | `GET /api/registrations` | 200; `items` does not contain that registration; `totalCredits` decreased by 6 |
| T-FR4-03 | negative | unknown UUID | `DELETE` | 404 `REGISTRATION_NOT_FOUND`; audit `DROP/FAILURE/REGISTRATION_NOT_FOUND` |
| T-FR4-04 | negative | registration belongs to Daniel, caller is Aisha | `DELETE` | 403 `NOT_OWNER`; the row still exists; response body does not contain `FIT`/course code |
| T-FR4-05 | negative | period closed | `DELETE` own registration | 403 `REGISTRATION_CLOSED`; row still exists; seats unchanged |
| T-FR4-06 | edge | drop the same registration twice | 2 × `DELETE` | first 200, second 404; seats increased by exactly 1 total |
| T-FR4-07 | edge | course at `availableSeats = capacity` (e.g. drop when nobody else joined) | `DELETE` | `availableSeats` stays `capacity`, never `capacity + 1` |
| T-FR4-08 | concurrency | `FIT3143` full (1/1) after one registration | drop and register simultaneously by two students | exactly one of the two mutating outcomes succeeds; final invariant `availableSeats + count = capacity` holds |
| T-FR4-09 | security | no token | `DELETE` | 401 `UNAUTHORIZED`; row unchanged; no `DROP` audit row |
| T-FR4-10 | edge | malformed id `"abc"` | `DELETE /api/registrations/abc` | 404 `REGISTRATION_NOT_FOUND` (not 400, not 500); one `DROP/FAILURE/VALIDATION_ERROR` audit row (A-19) |

---

## 8. Acceptance criteria

**FR4-AC1** — Given a student's own registration and an open period, when they drop it, then 200 is
returned, the row is gone, and the course's `availableSeats` increases by exactly one.
**FR4-AC2** — Given another student's registration, when a student attempts to drop it, then 403
`NOT_OWNER` and the row is untouched.
**FR4-AC3** — Given a closed period, when a drop is attempted, then 403 `REGISTRATION_CLOSED` and the
row is untouched.
**FR4-AC4** — Given a registration that no longer exists, when a drop is attempted, then 404
`REGISTRATION_NOT_FOUND`.
**FR4-AC5** — Given any drop attempt, then exactly one `DROP` audit row is written with matching
outcome and detail.
**FR4-AC6** — Given seats are at capacity, when a drop occurs, then `availableSeats` never exceeds
`capacity`.

---

*Prev: [03 FR3](./03-fr3-register-course.md) · Next: [05 FR5](./05-fr5-view-registered.md)*
