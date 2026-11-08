# Phase 1 — Triage

**Date:** 2026-11-08 (Round 1 authoring; Round 2 regression and Round 3 final closure added below)
**Suite (Round 3, current):** `cd backend && mvn -B clean test` → **146 run, 146 passed, 0 failed, 0 errors, 0 skipped**
**Suite (Round 2, historical):** 141 run, 141 passed
**Suite (Round 1, historical):** 132 run, 132 passed
**App defects:** Round 1: none detected. Round 2 (after the independent review): **6 found by the reviewer,
all 6 CLOSED** by named regression tests — see §5. Round 3 (after the adversarial re-review):
**N-1/N-2/N-3 CLOSED**, N-4/N-5/N-7 CLOSED, N-6 CLOSED (README documents JWT_SECRET + restart semantics), N-8 observations OPEN — see §6.
**Blocking issues:** **none open** (B-1 closed by T-B1-01; B-2 closed by T-B2-01/02 and the new
all-failure probe T-B2-03).

Every failing test encountered during authoring was a defect in the test harness and was fixed (see
§3). Nothing in §3 is a production-code failure. The Round 2 sections record real app defects that the
Round 1 suite could not see.

---

## 1. Feature triage

| Feature | Status | Failing tests | Root cause | Owner | Recommended fix |
| --- | --- | --- | --- | --- | --- |
| FR1 Login | **PASS** | none | — | coder-backend | None. Valid/invalid/unknown/padded/password-space paths, `/me`, 401 matrix, expiry, wrong signature and password-leak scan all green. |
| FR2 Catalogue | **PASS** | none | — | coder-backend | None. Active-semester scoping, 16/3 counts, code/name search, blank q, unknown semester 400, detail 200/404, multi-slot ordering and public `/semesters` all green. |
| FR3 Register | **PASS (Round 2)** | none | Round 1 had **B-1**: the write path was not scoped to the active semester (fixed) | coder-backend | B-1 closed by T-B1-01/T-B1-01b. Round 1 coverage still green: 201 + seat decrement, duplicate 409, invalid UUID 400, unknown 404, closed 403, 24-credit boundary, body `studentId` ignored, idempotent retry and audit rows. |
| FR4 Drop | **PASS (Round 2)** | none | Round 1 had **M-2**: concurrent double drop returned 500 and lost an audit row (fixed) | coder-backend | M-2 closed by T-M2-01 (`{200,404}`, 2 audit rows, one seat). Round 1 coverage green: NOT_OWNER without course leak, unknown/malformed id 404, closed 403, double-drop 404, capacity clamp. T-FR4-08 race is covered by `CapacityConcurrencyTest#tFr6_06`. |
| FR5 Registered view / CSV | **PASS (automated scope)** | none | — | coder-backend | None for API/CSV. T-FR5-11 (week-grid rendering) is manual and was **not executed**; assign to manual UI pass. |
| FR6 Capacity | **PASS (Round 2)** | none | Round 1 had **B-2** (shipped pool → 500s/lost audit), **M-3** (no CHECK constraints) and **M-4** (seed broke C1) — all fixed | coder-backend | Closed by T-B2-01, T-M3-01, T-M4-01. 25 concurrent students on 10 seats: 10×201 / 15×COURSE_FULL, 10 rows, seats 0, 25 audit rows, zero 5xx at pool 20; SQL contains `for update`; 3/3 consecutive runs green. |
| FR7 Period | **PASS** | none | — | coder-backend | None. Inclusive boundaries at `startAt`/`endAt`, ±1 s rejection, reads-while-closed, auth-before-period, public endpoint and fail-fast startup all green. |
| NFR2 Security | **PASS (Round 2)** | none | Round 1 had **M-1**: the H2 console was publicly exposed and a dev JWT secret was committed (fixed) | coder-backend | M-1 closed by T-M1-01/T-M1-01b (console 401/403/404 on both profiles, no committed secret). 7 routes × 5 bad-token states = 35 cases, hash storage, no password leakage, opaque 500, CORS allow/deny and SQL-looking input all green. |
| NFR4 Audit | **PASS (Round 2)** | none | Round 1 had **B-2** and **M-2**: audit rows were lost under pool pressure and on the stale-delete path (fixed) | coder-backend | B-2/M-2 closed by T-B2-01, T-B2-02, T-M2-01. E-01/E-02/E-04/E-06/E-07/E-09 observed; failures survive rollback through the `AFTER_COMPLETION` listener; 25-thread burst writes exactly 25 rows; no audit endpoint exists. |
| NFR1 Performance | **NOT VERIFIED** | n/a | No perf-tagged test or browser measurement was run in this task | tester / coder-frontend | Run `PerformanceSmokeTest` (to be added under `@Tag("perf")`) and the NFR1.2 browser procedure before sign-off. |
| NFR3 Availability (A1, A3, A6, A7) | **PARTIAL** | none | A4/A5 are automated; restart, client resilience and fail-closed UI need a running stack/browser | tester / coder-frontend | Manual restart + API-down checks per T-NFR3-01/04/05. |
| NFR5 Usability | **NOT VERIFIED** | n/a | Manual UI/axe/keyboard/print work is outside this automated run | tester / coder-frontend | Execute the §9 manual checklist with screenshots. |

---

## 2. Non-blocking observations

These did not fail any test and do not block the backend contract, but they should be closed or
explicitly waived by the orchestrator.

| ID | Severity | Observation | Evidence | Owner | Recommendation |
| --- | --- | --- | --- | --- | --- |
| O-1 | **CLOSED** (spec) | The seeded-full course violated invariant C1. The seed is now `FIT3182` at `capacity=1, availableSeats=0` with one real registration holding the only seat, so C1 holds for every seeded row. | `DataSeeder.seedFullCourseRegistration`; verified by T-M4-01 and `CapacityConcurrencyTest#tFr6_07` | planner / coder-backend | Closed — the TD-4 carve-out was removed from the suite. |
| O-2 | minor (spec) | NFR2-03 says “all 8 protected routes”; the contract lists 7 (`E2,E4,E5,E7,E8,E9,E10`). | spec 00 §9.5 / §9.3 vs 08 §NFR2.3 | planner | Correct the count to 7 so the coverage checklist is unambiguous. |
| O-3 | minor (demo UX) | The demo password appears in the login screen, not only in seed/docs as NFR2-09 states. | `frontend/src/pages/Login.tsx:118,312` | coder-frontend | Either scope NFR2-09 to “demo build only” or move the hint behind a dev flag. Not a security defect (no real secret). |
| O-4 | minor (tooling) | `npm run lint` cannot run: there is **no** `lint` script in `package.json` (and no `eslint` dependency). | `package.json` scripts = `dev`, `build`, `preview` | coder-frontend | Add `eslint` + a `lint` script as devDependencies, or drop all references to linting from the docs. Round 1's wording (“the script exists but the binary does not”) was wrong. |
| O-5 | **CLOSED** (test env) | The test profile previously raised the pool to 40, hiding the shipped pool of 10. Both now ship **20** and the test asserts the effective `HikariDataSource` size. H2 `LOCK_TIMEOUT=20000` is kept because the suites deliberately queue 25 writers on one row. | `application.yml`, `application-test.yml`; T-B2-01 | tester | Closed — the test profile now mirrors production. |

---

## 3. Test-harness defects fixed (not app defects)

| ID | Failing test(s) | Root cause | Fix |
| --- | --- | --- | --- |
| TD-1 | `CatalogControllerTest#tFr2_05_blankQuery` | MockMvc kept `%20%20` literal in the URL template; query was non-blank | Use `.param("q", "  ")` |
| TD-2 | `RegistrationControllerTest#tFr3_12`, `CapacityConcurrencyTest#tFr6_02/04` | Lazy `Student` proxy read outside a persistence context | Assert with `existsByStudentIdAndCourseId(...)` |
| TD-3 | `RegistrationViewTest` (all methods, after a `@MockBean` class ran) | Mockito contexts close after their class and `create-drop` dropped the shared H2 schema; a cached context then saw no tables | `@DirtiesContext(AFTER_CLASS)` on `BaseIntegrationTest` |
| TD-4 | `CapacityConcurrencyTest#tFr6_07/11` | C1 asserted against a seeded-full fixture that is outside C1 by design (O-1) | **CLOSED** — the fixture now matches the real seed (capacity 1 / seats 0 / 1 held registration) and `tFr6_07` asserts C1 instead of skipping it |

---

## 4. Exit assessment

| Gate | Result |
| --- | --- |
| Suite compiles | **YES** |
| `mvn -B clean test` green | **YES** — 146/146 (Round 3) |
| Critical concurrency test 3/3 | **YES** — `CapacityConcurrencyTest` 3/3 and the all-failure `RegressionAuditPoolTest` 3/3 (Round 3) |
| Row lock proven in SQL | **YES** — `for update` |
| Frontend `npm run build` | **YES** |
| App blocker/major defects | **0 open** — all Round 2 and Round 3 blocker/major findings CLOSED (§5, §6) |
| Manual NFR1/NFR5/NFR3 items | **OUTSTANDING** — not executed in this task |

**Backend automated verdict: PASS.**
**Phase 1 sign-off verdict: PASS WITH MANUAL-UI WAIVERS** — the remaining items are manual
performance/usability/availability checks, not backend defects.

---

## 5. Round 2 — independent review findings (closure)

Source: `docs/reviews/phase-1-review.md`. The Round 1 suite was green but could not see these; the
reviewer reproduced each one against the running artifacts. The backend coder fixed all six; the
regression suite below was written to fail if any of them regresses.

| ID | Severity | Defect | Status | Verifying test(s) | Evidence |
| --- | --- | --- | --- | --- | --- |
| **B-1** | blocker | Register accepted a course from a non-active semester; the row was then invisible to My Schedule/CSV and bypassed the 24-credit cap. | **CLOSED** | `T-B1-01`, `T-B1-01b` (`RegressionSemesterScopeTest`) | 404 `COURSE_NOT_FOUND`, seats unchanged, 0 rows, one `REGISTER_REJECTED/FAILURE/SEMESTER_NOT_ACTIVE` row. |
| **B-2** | blocker | Failure audit ran in `REQUIRES_NEW` while the business transaction held a connection + row lock; at the shipped pool the 25-writer burst produced 500s and lost audit rows. | **CLOSED** | `T-B2-01`, `T-B2-02`, **`T-B2-03`** (`RegressionAuditPoolTest`) | Pool asserted at 20 (audit pool 5); 10×201 / 15×409, zero 5xx, 10 rows, exactly 25 audit rows. T-B2-03 runs the all-failure shape at the shipped pool: 25×409, zero other statuses, 25/25 audit rows, slowest 145–255 ms. |
| **M-1** | major | `/h2-console/` served unauthenticated with full DB access; a dev JWT secret was committed. | **CLOSED** | `T-M1-01` (`RegressionDefaultProfileTest`), `T-M1-01b` (`SecurityMatrixTest`) | Console returns 401/403/404 on default and test profiles; `secret: ${JWT_SECRET:}` with no committed key. |
| **M-2** | major | Two concurrent drops of one registration gave `200` + `500 INTERNAL_ERROR` and only one audit row. | **CLOSED** | `T-M2-01` (`RegressionConcurrentDropTest`) | Statuses exactly `{200, 404}`, 404 body `REGISTRATION_NOT_FOUND`, 2 audit rows (1 success, 1 failure), seats returned once, C1 holds. |
| **M-3** | major | Zero database `CHECK` constraints; C2/C3 had no backstop. | **CLOSED** | `T-M3-01` (`RegressionSchemaConstraintTest`) | Named CHECKs in `information_schema`: `ck_course_credit_hours_allowed` (`IN(3,6)`), `ck_course_capacity_non_negative` (`>=0`), `ck_course_available_seats_non_negative`, `ck_course_available_seats_within_capacity`, `ck_course_schedule_time_order`. Rejects seats `-1`, seats `> capacity`, credit `0/4/5`, capacity `-1`, and reversed/equal schedule times; **accepts** `capacity = 0` (A-18) and credit 3/6. |
| **M-4** | major | Seeded `FIT3182` was capacity 40 / seats 0 / 0 registrations — outside invariant C1 — and the suite skipped C1 for it (TD-4). | **CLOSED** | `T-M4-01` (`RegressionDefaultProfileTest`), `CapacityConcurrencyTest#tFr6_07` | Real seeder: 19 courses, `FIT3182` 1/0/1 held by `32067890`, `FIT3143` 1/1/0, C1 for all 19 rows. TD-4 carve-out deleted. |

**Negative controls (run in a throwaway copy at `/tmp`, never in the workspace):**

| Finding | Mutation applied to the pre-fix shape | Observed |
| --- | --- | --- |
| B-1 | replace `findWithLockByIdAndSemester` with the unscoped `findWithLockById` | `tB1_01` fails: `Status expected:<404> but was:<201>` |
| B-2 | failure audit back inside the locked transaction (`REQUIRES_NEW`), pool 10 | `tB2_01` fails: 10×201 but only **6**×`COURSE_FULL` (the other 9 were 5xx), ≈28 s |
| M-2 | unlocked registration read + no optimistic-lock catch/handler | `tM2_01` fails: `[200, 500]` instead of `[200, 404]` |

**Still open (non-blocking, unchanged from Round 1):** O-2 (NFR2-03 says 8 routes, contract lists 7),
O-3 (demo password shown on the login screen — mandated by spec 09, contradicting NFR2-09), O-4 (no
`lint` script exists in `frontend/package.json`). Manual NFR1/NFR3/NFR5 evidence is still outstanding.

---

## 6. Round 3 — adversarial re-review findings (`docs/reviews/phase-1-rereview.md`)

Source: N-1…N-8 in the re-review. Every entry is either CLOSED with the test that now proves it, or
OPEN with evidence and an owner. No assertion was relaxed to close a row.

| ID | Severity | Finding | Status | Verifying test(s) / evidence |
| --- | --- | --- | --- | --- |
| **N-1** | blocker | B-2 still live on the shipped jar: failure audit asked the business pool for a second connection; 25 all-failure writers gave 3–4 × 500 and 30 s stalls at pool 20. | **CLOSED** | Audit no longer touches JPA/business pool: `AuditFailureListener` → `AuditWriter` on `auditDataSource` (`DataSourceConfig`, pool 5). **T-B2-03** at the shipped pools (20/5): 25 × 409, zero other statuses, 25/25 audit rows, 25 distinct ids, slowest 145 ms (full run) / 219–255 ms (3 flake runs). |
| **N-2** | major | M-3 constraints did not match the frozen schema; M-4 seed rewrite was an unrecorded contract change. | **CLOSED** | `Course` now declares `credit_hours in (3,6)`, `capacity >= 0`, both seat bounds; `CourseSchedule` declares `end_time > start_time` — proven by **T-M3-01** (presence + accept/reject matrix, including `credit_hours = 4/5` rejected and `capacity = 0` accepted). Seed decision recorded as **A-16** in `docs/specs/11-decisions.md`; seed shape proven by **T-M4-01**. Residual: `day_code IN (MON…SUN)` DB check is absent (OPEN-3 below). |
| **N-3** | major (test quality) | `RegressionAuditPoolTest#tB2_01` was a false green: the 10-success/15-failure mix never reaches the all-failure peak. | **CLOSED** | **T-B2-03** added: genuinely full `FIT3182` (capacity 1, held by `32067890`), 25 simultaneous POSTs from `32012345`, shipped pools asserted, baseline audit count 0. Asserts exact `{409}` status set, 0 × 201, 25 audit rows all `REGISTER_REJECTED/FAILURE/COURSE_FULL`, 25 distinct ids, 1 registration, C1, ≤2 s. |
| **N-4** | minor | `SEMESTER_NOT_ACTIVE` was outside the supposedly exhaustive audit matrix. | **CLOSED** (spec) | Recorded as E-11 in `docs/specs/08-non-functional.md:195` and decision **A-17** in `docs/specs/11-decisions.md`. Behaviour already proven by T-B1-01/T-B1-01b. |
| **N-5** | minor | Global exception mappings were over-broad (`DataIntegrityViolationException` → 409, `ObjectOptimisticLockingFailureException` → 404 on every endpoint). | **CLOSED** (code) | `GlobalExceptionHandler.java:114-146`: both handlers now branch on `isRegisterAttempt` / `isDropAttempt`; unmatched paths log and return 500. Closed by inspection — no dedicated behavioural test (the paths are unreachable without `@Version`/a forced constraint violation). |
| **N-6** | minor | README never documents `JWT_SECRET` or restart semantics. | **CLOSED** | `README.md` §Configuration now documents `JWT_SECRET`, `REGISTRATION_START/END`, `ACTIVE_SEMESTER`, ephemeral-key restart semantics, and dev-only H2 console. |
| **N-7** | minor | Malformed attempts were not audited (E-12/E-13 absent). | **CLOSED** | `GlobalExceptionHandler.auditRejection` writes via `AuditWriter`; proven by **T-A19-01** (1 row E-12), **T-A19-01b** (2 attempts → 2 rows, distinct ids), **T-A19-02** (E-13 row), **T-A19-03** (401 → 0 rows, no subject). |
| **N-8** | observations | No CHECK on `audit_log.action/outcome`; CORS includes `127.0.0.1`; JWT parser accepts HS384/512 with the same key; Spring generated `user` password logged. | **OPEN** (accepted) | Pre-existing observations, none reachable as a Phase 1 defect. Owner: coder-backend for a later pass. |

### 6.1 Decision closure (A-16…A-19)

| Decision | Requirement | Verifying test(s) | Result |
| --- | --- | --- | --- |
| **A-16** | `FIT3182` = capacity 1 / seats 0 / exactly one registration held by `32067890`; C1 holds | T-M4-01, T-FR6-07, T-B2-03 | **PASS** |
| **A-17** | Non-active-semester register → 404 `COURSE_NOT_FOUND` + `SEMESTER_NOT_ACTIVE` audit, no seat change, no row | T-B1-01, T-B1-01b | **PASS** |
| **A-18** | Four DB CHECKs: `capacity >= 0`, `credit_hours IN (3,6)`, `0 <= available_seats <= capacity`, `end_time > start_time`; `capacity = 0` legal | T-M3-01 | **PASS** |
| **A-19** | Validation-rejected register/drop attempts audited (`VALIDATION_ERROR`); auth rejection not audited | T-A19-01, T-A19-01b, T-A19-02, T-A19-03 | **PASS** (HTTP status for E-13 is a spec conflict — OPEN-1) |

### 6.2 Open items from Round 3

| ID | Severity | Item | Evidence | Owner |
| --- | --- | --- | --- | --- |
| **OPEN-1** | minor (spec conflict) | **CLOSED** — specs reconciled to the implemented contract: malformed `registrationId` on DELETE is `404 REGISTRATION_NOT_FOUND` with audit detail `VALIDATION_ERROR` (NFR4.2 E-13, FR4, 00 §9.2 all agree). | `docs/specs/08-non-functional.md:197-206`; T-A19-02 | planner |
| **OPEN-3** | minor (schema gap) | **DEFERRED (A-20)** — `course_schedule.day_code` has no DB CHECK; JPA enum constrains all writes and there is no raw-SQL path. Recorded as a post-Phase-1 follow-up, not a behaviour defect. | `docs/specs/11-decisions.md` A-20; `CourseSchedule.java` | coder-backend |
| O-2 | minor (spec) | NFR2-03 says 8 protected routes; contract lists 7. | unchanged | planner |
| O-3 | minor (demo UX) | Demo password shown on the login screen. | `frontend/src/pages/Login.tsx:118,312` | coder-frontend |
| O-4 | minor (tooling) | **CLOSED** — dead `lint` script removed (no ESLint dependency/config existed). | `frontend/package.json` | coder-frontend |

**Round 3 exit:** `mvn -B clean test` → 146/146; `CapacityConcurrencyTest` 3/3;
`RegressionAuditPoolTest` (incl. T-B2-03) 3/3; `npm run build` exit 0. No production behaviour
defect open on the tested paths; OPEN-1/OPEN-3 are spec/schema items, not test failures.
