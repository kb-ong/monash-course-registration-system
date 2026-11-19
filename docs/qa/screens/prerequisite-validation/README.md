# FR8 prerequisite validation — visual evidence

**Baseline revision:** `62f534f` (the ticket branch base, `origin/main` when the ticket started)
**Changed revision:** the FR8 feature branch (`agent/prerequisite-validation-619065987014`)
**Scenario:** Priya Nair (`32011111`) has completed no courses; `FIT2004` requires `FIT1008`.
Registration period is open on the seeded demo clock.
**Environment:** Chrome 150 headless, 1440 × 1000 viewport, light theme, frontend on
`http://127.0.0.1:5173`, backend seeded on port 8080. Screenshots are actual application captures.

| # | Revision | Shot | What it shows |
| --- | --- | --- | --- |
| 01 | baseline | [course detail](prerequisite-validation/01-before-course-detail.png) | "Assessment & prerequisites" says there are none; the catalogue is described as open enrolment. |
| 02 | FR8 | [course detail](prerequisite-validation/02-after-course-detail.png) | The same page lists `FIT1008 Introduction to Computer Science` as a required completed course. |
| 03 | baseline | [register attempt](prerequisite-validation/03-before-register-attempt.png) | Confirming the `FIT2004` register dialog returns 201: toast "Registered for FIT2004", seats 80 → 79, course marked registered. |
| 04 | FR8 | [register attempt](prerequisite-validation/04-after-register-attempt.png) | The same confirmation returns 409 `PREREQUISITE_NOT_MET`: the dialog stays open with "Cannot register for FIT2004: missing prerequisite FIT1008." and the seat count stays 80. |

The only intended difference is FR8 behaviour and its copy; the pages, viewport, theme, seeded data
and registration period are otherwise identical between the two revisions. The footer/login copy was
updated from "Open enrolment" to "Prerequisites enforced" because the Phase 1 wording became false.
