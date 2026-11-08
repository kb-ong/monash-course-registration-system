# FR5 — View Registered Courses

**Story:** US3 · **Actors:** Student (P1), System
**Endpoints:** `GET /api/registrations`, `GET /api/registrations/export`
**Depends on:** [FR1](./01-fr1-student-login.md), [FR3](./03-fr3-register-course.md).

---

## 1. Requirement

> A student can see every course they are registered in for the active semester, in one place, with
> total credit points against the 24-credit limit, a week-grid timetable, a printable view, and a CSV
> download of the course list.

---

## 2. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | `GET /api/registrations` is scoped to the **token subject** and the **active semester**. Registrations from other semesters are not returned (none can exist in Phase 1, but the query is semester-scoped regardless). |
| B2 | `totalCredits` is computed server-side as Σ `creditHours`; the client must not recompute it as authority. |
| B3 | `maxCredits` is always the literal `24`. |
| B4 | `items` ordered `registeredAt` ASC, tiebreak `course.code` ASC. |
| B5 | Empty state is `200` with `items: []` and `totalCredits: 0` — never 404. |
| B6 | `availableSeats` inside each `course` is live at read time, so the student can see scarcity for their own courses. |
| B7 | CSV: `Content-Disposition: attachment; filename="course-list.csv"`, `text/csv; charset=UTF-8`, RFC 4180, CRLF, **one row per registration × schedule slot**, header exact: `Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location`. |
| B8 | CSV `Time` is `HH:mm-HH:mm`; `Day` is `MON`…`SUN`; fields containing `,` `"` or newline are quoted and inner quotes doubled. |
| B9 | CSV ordering: `course.code` ASC, then day MON→SUN, then `startTime` ASC. |
| B10 | CSV with no registrations returns 200 with the header row only. |
| B11 | CSV has no BOM (so spreadsheet imports do not see a stray `\uFEFF` in the first header cell). |
| B12 | Print: a dedicated print stylesheet renders a clean A4 timetable + course table; nav, buttons and toasts are hidden. |
| B13 | The week grid places a slot by `day` column and `startTime`/`endTime` rows on a 30-minute grid from 08:00 to 20:00; slots outside that range are clamped and flagged with a `†` note. |
| B14 | No clash detection (NG2): overlapping slots are drawn overlapping with a visible conflict tint but no blocking, no error. |

---

## 3. Validation rules

| Field | Rule | Violation |
| --- | --- | --- |
| auth | valid bearer token | 401 `UNAUTHORIZED` |
| — | no query parameters are accepted; unknown params ignored | — |
| export | same auth; `Accept` header irrelevant | 401 `UNAUTHORIZED` |

---

## 4. API surface used

```http
GET /api/registrations
Authorization: Bearer <token>
```

```json
{
  "semester": "2026-S2",
  "totalCredits": 12,
  "maxCredits": 24,
  "items": [
    {
      "registrationId": "1b7e5c44-6c2f-4c9e-8a11-3f0d2c9b7a55",
      "registeredAt": "2026-11-08T03:26:41Z",
      "course": {
        "id": "…", "code": "FIT1045", "name": "Introduction to Programming",
        "creditHours": 6, "instructor": "Dr Alan Ng", "capacity": 120, "availableSeats": 119,
        "semester": "2026-S2", "faculty": "Information Technology", "description": "…",
        "schedule": [
          { "day": "MON", "startTime": "10:00", "endTime": "12:00", "location": "Clayton, Building 25, Rm 125" },
          { "day": "WED", "startTime": "14:00", "endTime": "16:00", "location": "Clayton, Building 25, Rm 130" }
        ]
      }
    }
  ]
}
```

CSV response:

```
Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location
FIT1045,Introduction to Programming,6,Dr Alan Ng,MON,10:00-12:00,"Clayton, Building 25, Rm 125"
FIT1045,Introduction to Programming,6,Dr Alan Ng,WED,14:00-16:00,"Clayton, Building 25, Rm 130"
FIT2004,Algorithms and Data Structures,6,Dr Wei Chen,TUE,10:00-12:00,"Clayton, Building 25, Rm 125"
```

---

## 5. Audit log events

None (read-only). Application log at DEBUG: `registrations.view studentId=… count=…`.

---

## 6. UI

**Screen:** My Schedule (`/schedule`) — [09 §6.5](./09-ui-design-system.md#65-my-schedule-schedule).

Layout: two panes on desktop — left 62 % week grid, right 38 % "your courses" list with credit meter,
`Export CSV` and `Print` actions. On mobile: tabs `Timetable` / `Courses`, credit meter pinned above.

States:

| State | Trigger | Rendering |
| --- | --- | --- |
| Loading | first fetch | grid skeleton (7 columns × 5 rows of faint blocks) + 3 list skeletons |
| Empty | `items: []` | centred `EmptyState`: lucide `CalendarX`, "You're not registered in anything yet.", sub "Browse the catalogue to find your first course.", primary CTA `Browse catalogue` → `/catalog`; CSV/Print disabled |
| Populated | ≥ 1 item | grid + list, credit meter `12 / 24`, each list row shows code, name, credits, slots, seat meter, `Drop` |
| Near limit | `totalCredits >= 18` | credit meter turns amber, caption "6 credits left" |
| At limit | `totalCredits = 24` | meter gold, caption "Credit limit reached" |
| Over (impossible) | `totalCredits > 24` | meter danger; render defensively, log a client warning |
| Error | 5xx | inline error panel + Retry; CSV/Print disabled |
| Exporting | CSV click | button spinner until blob is created; then `URL.revokeObjectURL` |
| Export empty | 0 items | export disabled with tooltip |

Print: `@media print` hides header nav, toasts, buttons; forces black-on-white; grid becomes a
table-like layout with hairline borders; page header reads `Monash University — Course timetable` with
student name, semester and printed date; footer "Generated 8 November 2026".

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR5-01 | happy | Aisha registered in `FIT1045` (6cp) and `FIT2004` (6cp) | `GET /api/registrations` | 200; `items` length 2; `totalCredits=12`; `maxCredits=24`; `semester=2026-S2`; each item has `registrationId`, `registeredAt`, full `course` |
| T-FR5-02 | edge | Aisha registered in nothing | `GET /api/registrations` | 200; `items=[]`; `totalCredits=0`; `maxCredits=24` |
| T-FR5-03 | happy | the two registrations above | `GET /api/registrations/export` | 200; `Content-Type` starts `text/csv`; `Content-Disposition` contains `attachment` and `filename="course-list.csv"` |
| T-FR5-04 | happy | `FIT1045` has 2 slots | export body | first line is exactly `Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location`; `FIT1045` appears on 2 lines; `Time` matches `^\d{2}:\d{2}-\d{2}:\d{2}$` |
| T-FR5-05 | edge | location contains a comma | export body | that field is wrapped in double quotes and parses back to the original string with a CSV reader |
| T-FR5-06 | edge | no registrations | export | 200; body is exactly the header line (+ CRLF); no data rows |
| T-FR5-07 | security | no token | `GET /api/registrations` and `/export` | both 401 `UNAUTHORIZED` |
| T-FR5-08 | security | Aisha's token | `GET /api/registrations` | contains only Aisha's items — assert no `registrationId` belonging to Daniel |
| T-FR5-09 | edge | body bytes | export | no UTF-8 BOM (`EF BB BF`) at offset 0; line separator is CRLF |
| T-FR5-10 | happy | registrations created in order `FIT2004` then `FIT1045` (different times) | `GET /api/registrations` | `items[0]` is the earlier `registeredAt`; export is ordered by `course.code` ASC (independent of insertion order) |
| T-FR5-11 | edge | student registered in a multi-slot course | UI render (manual) | both slots appear in the week grid at the right day/time |

---

## 8. Acceptance criteria

**FR5-AC1** — Given a student with registrations, when they open My Schedule, then every registration
appears with course code, name, credits, instructor, day/time and location.
**FR5-AC2** — Given the registrations response, then `totalCredits` equals the sum of `creditHours`
and `maxCredits` is 24.
**FR5-AC3** — Given zero registrations, then the response is 200 with an empty `items` array and the UI
shows an empty state with a catalogue CTA.
**FR5-AC4** — Given a click on Export CSV, then a `course-list.csv` attachment downloads with the exact
header and one row per registration × slot, correctly quoted.
**FR5-AC5** — Given a click on Print, then the printed output contains only the timetable and course
table — no navigation, buttons, toasts or colours that waste ink.
**FR5-AC6** — Given another student's data exists, when a student views their registrations, then only
their own items are returned.

---

*Prev: [04 FR4](./04-fr4-drop-course.md) · Next: [06 FR6](./06-fr6-capacity.md)*
