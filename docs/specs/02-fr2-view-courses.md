# FR2 — View Available Courses

**Story:** US2 · **Actors:** Student (P1), System
**Endpoints:** `GET /api/semesters`, `GET /api/courses`, `GET /api/courses/{id}`
**Depends on:** [FR1](./01-fr1-student-login.md) (auth for E4/E5). **Depended on by:** FR3, FR5.

---

## 1. Requirement

> A signed-in student can see the current semester's catalogue, search it by course code or name, and
> open a course to read its description, meeting times and live seat availability. Courses from other
> semesters never leak into the active catalogue.

---

## 2. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | `GET /api/courses` without `semester` returns the **configured active semester** only. |
| B2 | `GET /api/courses?semester=X` returns only courses where `course.semester = X`. An unknown `X` (not in `/api/semesters`) → 400 `VALIDATION_ERROR`. |
| B3 | `q` is trimmed; blank/absent returns everything in scope. Matching is case-insensitive substring on `code` **OR** `name`. `instructor`/`faculty`/`description` are not searched (A-14). |
| B4 | `q` longer than 100 characters → 400 `VALIDATION_ERROR`. |
| B5 | Results ordered by `code` ASC — deterministic, no relevance ranking. |
| B6 | Seat data is live: `availableSeats` reflects committed registrations at read time. No caching (`Cache-Control: no-store`). |
| B7 | `GET /api/courses/{id}` returns the identical object shape as a list item, including all schedule slots. |
| B8 | Unknown or non-UUID `id` → 404 `COURSE_NOT_FOUND` (not 400). |
| B9 | `GET /api/semesters` is public and lists every distinct semester present in the catalogue, plus the configured active one even if it has no courses. Ordered `code` DESC. |
| B10 | No server-side pagination (NG8). The SPA renders all results from one response. |
| B11 | The SPA debounces search input by 300 ms and cancels/ignores superseded responses (last write wins by request sequence). |
| B12 | Search and semester selection are reflected in the URL (`/catalog?semester=2026-S2&q=algo`) so the view is shareable and survives refresh. |

---

## 3. Validation rules

| Param | Rule | Violation |
| --- | --- | --- |
| `semester` | optional; if present must be a known code (`^\d{4}-S[12]$` and present in catalogue/config) | 400 `VALIDATION_ERROR` |
| `q` | optional; ≤ 100 chars after trim | 400 `VALIDATION_ERROR` |
| `id` (path) | must parse as UUID | 404 `COURSE_NOT_FOUND` |
| auth | `Authorization: Bearer` required on E4/E5 | 401 `UNAUTHORIZED` |
| E3 | no auth required; a bad token is ignored, not rejected | — |

---

## 4. API surface used

| Call | Purpose |
| --- | --- |
| `GET /api/semesters` | populate the semester selector; find the `active` one |
| `GET /api/courses?semester=&q=` | catalogue grid + search results |
| `GET /api/courses/{id}` | course detail page |

```http
GET /api/courses?semester=2026-S2&q=algo
Authorization: Bearer <token>
```

```json
[
  {
    "id": "6f1c2f7e-0d5a-4a3e-9c1b-2d7c9a1e4b10",
    "code": "FIT2004",
    "name": "Algorithms and Data Structures",
    "creditHours": 6,
    "instructor": "Dr Wei Chen",
    "capacity": 80,
    "availableSeats": 80,
    "semester": "2026-S2",
    "faculty": "Information Technology",
    "description": "Algorithm design paradigms, complexity analysis and core data structures.",
    "schedule": [
      { "day": "TUE", "startTime": "10:00", "endTime": "12:00", "location": "Clayton, Building 25, Rm 125" }
    ]
  }
]
```

---

## 5. Audit log events

None. Reads are not audited (NFR4 scope is mutations). Application log at DEBUG only:
`catalog.search semester=… q=… results=…`.

---

## 6. UI

**Screens:** Dashboard (`/`, featured strip + status), Catalog (`/catalog`), Course Detail (`/courses/:id`).
Wireframes: [09 §6.2–6.4](./09-ui-design-system.md#62-dashboard-).

States:

| State | Trigger | Rendering |
| --- | --- | --- |
| Loading (first) | request in flight, no data | 6 skeleton cards, correct aspect ratio, no spinner-only screen |
| Loading (refine) | request in flight, data present | grid dims to 60 % opacity, `aria-busy="true"`, previous results stay visible |
| Empty | `200 []` | `EmptyState`: search-off illustration (lucide `SearchX`), "No courses match "algo"." + "Clear search" button |
| Error | 5xx / network | inline error panel with `Retry` button; retains last good results if any |
| Full course | `availableSeats === 0` | card shows `FULL` badge, seat meter in danger colour, Register disabled |
| Almost full | `0 < availableSeats <= 5` | amber seat meter + "Only N seats left" |
| Success | data | responsive grid, 1/2/3 columns |

Search UX: label "Search courses", placeholder "Try FIT2004 or Algorithms", `aria-describedby` hint
"Matches course code or name". Result count announced in a visually-hidden `aria-live="polite"` region:
"14 courses found."

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR2-01 | happy | seed §8.2 | `GET /api/courses` (no params), valid token | 200; exactly 16 items; every `semester=2026-S2`; sorted by `code` ASC; each item has `schedule` non-empty |
| T-FR2-02 | happy | seed | `GET /api/courses?semester=2026-S2&q=FIT1045` | 200; exactly 1 item with `code=FIT1045` |
| T-FR2-03 | happy | seed | `GET /api/courses?q=fIt10` (mixed case) | 200; every returned `code` contains `FIT10` case-insensitively; `FIT1047` is **absent** (belongs to `2026-S1`) |
| T-FR2-04 | happy | seed | `GET /api/courses?q=algorithms` (lowercase) | 200; includes `FIT2004` (name match, case-insensitive) |
| T-FR2-05 | edge | seed | `GET /api/courses?q=%20%20` (whitespace only) | 200; 16 items (blank after trim = all) |
| T-FR2-06 | edge | seed | `GET /api/courses?q=zzzz` | 200 with `[]` — not 404, not 500 |
| T-FR2-07 | negative | seed | `GET /api/courses?semester=2025-S1` | 400 `VALIDATION_ERROR` |
| T-FR2-08 | negative | seed | `GET /api/courses?q=` + 101 chars | 400 `VALIDATION_ERROR` |
| T-FR2-09 | negative | seed | `GET /api/courses` with no token | 401 `UNAUTHORIZED` |
| T-FR2-10 | happy | seed, `FIT1045` has 2 slots | `GET /api/courses/{fit1045Id}` | 200; `schedule` length 2; slots ordered by day then `startTime`; single object (not array) |
| T-FR2-11 | negative | — | `GET /api/courses/00000000-0000-0000-0000-000000000000` | 404 `COURSE_NOT_FOUND` |
| T-FR2-12 | edge | seed | `GET /api/semesters` with **no** token | 200; contains `2026-S2` with `active=true` and `2026-S1` with `active=false`; sorted DESC |
| T-FR2-13 | edge | seed | `GET /api/courses?semester=2026-S1` | 200; exactly 3 items; all `semester=2026-S1`; `FIT1045` absent |

---

## 8. Acceptance criteria

**FR2-AC1** — Given a signed-in student, when the catalogue loads, then only the active semester's
courses are returned, sorted by code.
**FR2-AC2** — Given a search term, when it matches a course code or name case-insensitively, then that
course appears; when it matches neither, the result is an empty array with HTTP 200.
**FR2-AC3** — Given a semester parameter, when it names a semester that exists, then only its courses
are returned; when it does not exist, 400 `VALIDATION_ERROR`.
**FR2-AC4** — Given a course with two schedule slots, when its detail is fetched, then both slots are
returned in day/time order.
**FR2-AC5** — Given an unknown course id, when detail is fetched, then 404 `COURSE_NOT_FOUND`.
**FR2-AC6** — Given seat counts change, when the catalogue is refreshed, then the new `availableSeats`
is shown with no stale cache.
**FR2-AC7** — Given any catalogue request without a token, then 401 `UNAUTHORIZED`.
**FR2-AC8** — Given an empty result set, when the grid renders, then an empty state with a clear-search
action is shown and the count is announced to screen readers.

---

*Prev: [01 FR1](./01-fr1-student-login.md) · Next: [03 FR3](./03-fr3-register-course.md)*
