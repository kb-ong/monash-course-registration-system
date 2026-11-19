# FR9 timetable clash detection — visual evidence

**Baseline revision:** `a486cde` (`origin/main` when the ticket started)
**Changed revision:** the FR9 feature branch (`agent/timetable-clash-detection-f2bdb02c694f`)
**Scenario:** Aisha Rahman (`32012345`) is registered in `FIT2004` (Tuesday 10:00–12:00), then tries
to register `FIT1051` (Tuesday 09:00–11:00). The registration period is open on the seeded demo
clock.
**Environment:** Chrome 150 headless, 1280 × 900 viewport, light theme, frontend on
`http://localhost:5174`, backend seeded on port 8080 (port 5173 was occupied by an unrelated local
process; the backend CORS allow-list was extended to 5174 for this local capture only, no repository
config changed). Screenshots are actual application captures.

| # | Revision | Shot | What it shows |
| --- | --- | --- | --- |
| 01 | baseline | [course detail](01-before-course-detail.png) | "Assessment & prerequisites" states that timetable clashes are not detected. |
| 02 | FR9 | [course detail](02-after-course-detail.png) | The same section states that clashes are checked at registration and conflicting classes are named. |
| 03 | baseline | [register attempt](03-before-register-attempt.png) | Confirming the `FIT1051` dialog returns 201: the dialog closes, toast "Registered for FIT1051", seats 100 → 99, course marked registered even though it overlaps `FIT2004`. |
| 04 | FR9 | [register attempt](04-after-register-attempt.png) | The same confirmation returns 409 `TIMETABLE_CLASH`: the dialog stays open with "Cannot register for FIT1051: timetable clash with FIT2004 (TUE 10:00-12:00)." and no seat is taken. |

The only intended difference is FR9 behaviour and its copy; the pages, viewport, theme, seeded data
and registration period are otherwise identical between the two revisions.
