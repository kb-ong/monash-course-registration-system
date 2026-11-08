# Phase 1 — Final Review (ship / no-ship gate)

**Project:** Monash Course Registration System — Phase 1
**Branch reviewed:** `feat/phase-1` (final tree as presented)
**Reviewer:** final-reviewer agent
**Date:** 2026-11-08 (Asia/Kuala_Lumpur)
**Inputs:** `docs/reviews/phase-1-review.md`, `docs/reviews/phase-1-rereview.md`,
`docs/qa/phase-1-test-report.md` §13, `docs/qa/triage.md` §6, `docs/specs/00`–`11`, and the final
source.

**Scope of writes:** this file only. No production code, test, spec, QA or git state was modified.
No `git` command was run.

---

## 0. Verdict

# **SHIP**

**No blocking issues.** Both blockers are genuinely closed on the shipped artifact, all four majors
and both re-review majors hold, the dedicated audit datasource introduces no deadlock, leak or
duplicate-audit regression, and all seven FRs conform to the frozen specs on this tree. The suite is
146/146 and the frontend builds. The remaining items are minor, explicitly enumerated in §7, and do
not affect a stated acceptance criterion.

The one thing I checked hardest is the thing that failed twice before: the **all-failure** burst on
the packaged jar at the shipped pools. It now passes, live, three consecutive runs (75/75 attempts,
25/25 audit rows per run), with zero pool errors — see §2.1 and §3.

---

## 1. Commands run independently

```bash
cd backend && mvn -B clean test
# [INFO] Tests run: 146, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
# [INFO] Total time: 39.796 s   (finished 2026-11-08T13:23:28+08:00)

cd backend && mvn -B -q -DskipTests package
# target/course-registration-0.1.0.jar  53,061,289 bytes  13:23:54

cd frontend && npm run build
# tsc -b && vite build -> 2035 modules transformed, built in 3.52 s, exit 0
# dist/assets/index-Do3UQIhO.js 451.32 kB (gzip 139.91) | index-CtpqDCPE.css 37.91 kB
```

Per-class totals from `target/surefire-reports` (sum = 146, matches the summary line):

| Class | Tests | | Class | Tests |
| --- | ---: | --- | --- | ---: |
| AuthControllerTest | 11 | | RegressionSemesterScopeTest | 2 |
| LoginValidationNoBcryptTest | 1 | | RegressionAuditPoolTest | 3 |
| JwtServiceTest | 3 | | RegressionConcurrentDropTest | 1 |
| CatalogControllerTest | 14 | | RegressionSchemaConstraintTest | 1 |
| RegistrationControllerTest | 14 | | RegressionDefaultProfileTest | 2 |
| DropControllerTest | 9 | | RegressionValidationAuditTest | 4 |
| RegistrationViewTest | 10 | | RegistrationPeriodTest | 9 |
| CapacityConcurrencyTest | 9 | | AuditLogTest | 8 |
| RowLockSqlTest | 1 | | SecurityMatrixTest | 42 |
| StartupValidationTest | 1 | | ErrorEnvelopeTest | 1 |
| | | | **Total** | **146** |

No test is skipped, disabled or removed. The three regression classes are present at the sizes the
QA report claims (2 / 3 / 1) plus the new 4-test validation-audit class.

Runtime verification used the **packaged jar on the shipped default profile** (business pool 20,
audit pool 5, no test overrides):

```bash
java -jar target/course-registration-0.1.0.jar --server.port=8098 \
     --logging.level.edu.monash.registration=DEBUG
# later: --server.port=8099 with default logging for the status-only measurement
```

Both instances were stopped afterwards; ports 8098/8099 are free.

---

## 2. Live evidence (packaged artifact, shipped config)

### 2.1 B-2 — 25 concurrent ALL-FAILING registrations on the seeded-full `FIT3182`

One student (`32012345`), one genuinely full course (capacity 1, seats 0, seat held by `32067890`),
25 threads released by a barrier. This is the exact shape that produced 3–4 × 500 and 30 s stalls in
the re-review.

| Run | Statuses | Elapsed | min / median / max latency | Pool errors in log | `audit.record` rows |
| --- | --- | --- | --- | --- | --- |
| 8099, default logging | `{409: 25}` | 0.32 s | 127 / 222 / 311 ms | 0 | (not logged) |
| 8098 #1, DEBUG | `{409: 25}` | 0.35 s | 152 / 242 / 342 ms | 0 | 25 |
| 8098 #2, DEBUG | `{409: 25}` | 0.16 s | 20 / 87 / 157 ms | 0 | 25 |
| 8098 #3, DEBUG | `{409: 25}` | 0.14 s | 16 / 80 / 136 ms | 0 | 25 |

Log scan over all three DEBUG runs (75 attempts):

```
grep -c "Connection is not available"            -> 0
grep -c "Could not open JPA EntityManager"       -> 0
grep -c "afterCompletion threw exception"        -> 0
grep -c "audit.write.lost|audit.write.failed"    -> 0
grep -c "audit.record attemptId=.*COURSE_FULL"   -> 75
```

Every row carries a distinct attempt id, `action=REGISTER_REJECTED`, `detail=COURSE_FULL`,
`course=FIT3182`. The process kept serving normally after each burst (subsequent burst latency
*dropped*, which is the opposite of connection leakage).

### 2.2 B-1, drop, console

| Check | Result |
| --- | --- |
| `POST /api/registrations` with `cccccccc-…-0001` (`FIT1008`, 2026-S1) | **404 `COURSE_NOT_FOUND`** |
| `GET /api/courses?semester=2026-S1` → `availableSeats` | **80** (unchanged) |
| `GET /api/registrations` | `{"semester":"2026-S2","totalCredits":0,"items":[]}` |
| Audit detail emitted | `SEMESTER_NOT_ACTIVE` ×1 (DEBUG log) |
| `POST` FIT2099 → `GET` list → `DELETE` → `DELETE` again | **201** → `totalCredits:6` → **200** (seats 60) → **404 `REGISTRATION_NOT_FOUND`** |
| `GET /h2-console/`, `/h2-console/login.do` on default profile | **401**, **401** |
| Startup with no `JWT_SECRET` | `WARN … generated an ephemeral 256-bit HS256 key … Tokens will not survive a restart.` (no key material logged) |

---

## 3. B-2 — is it genuinely closed?

### 3.1 The mechanism is the right one

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| Failure audit must not use the request/business pool | Separate Hikari pool `auditDataSource` over the same DB, `maximumPoolSize=5`, `connectionTimeout=5000`, `minimumIdle=1` | `config/DataSourceConfig.java:55-69`; `application.yml:34-40` |
| Business pool explicit and primary (so JPA still binds to it) | `@Primary @Bean(name="dataSource")` built from `DataSourceProperties`, `@ConfigurationProperties("spring.datasource.hikari")` | `DataSourceConfig.java:39-49`; effective 20 asserted by `RegressionAuditPoolTest.java:95-97` |
| Write must not touch the JPA session that just rolled back | Plain `JdbcTemplate` over the audit pool, single-row `insert into audit_log …` | `AuditWriter.java:57-99` |
| Write must happen after the business outcome | `@TransactionalEventListener(phase = AFTER_COMPLETION)` on the event published inside the service transaction | `AuditFailureListener.java:24-26`; `AuditService.java:54-58` |
| One row per attempt, retry-idempotent | Row id is a deterministic negative id derived from the attempt UUID; `DuplicateKeyException` = already recorded | `AuditWriter.java:85-95, 103-108` |
| Audit failure must not change the response | All `DataAccessException` caught, logged, retried once, never rethrown; `afterCompletion` exceptions are swallowed by `TransactionSynchronizationUtils` | `AuditWriter.java:85-99`; verified live (0 `audit.write.*` on 75 attempts) |

### 3.2 The test is not vacuous (T-B2-03)

`RegressionAuditPoolTest#tB2_03_allFailureBurstOnFullCourse` (`:93-173`) asserts, in order:

* **Pool sizes**: business `HikariDataSource#getMaximumPoolSize() == 20`, audit `== 5` (`:94-101`) —
  so it cannot silently run on a test-only inflated pool.
* **Clean baseline**: `auditLogRepository.count() == 0` (`:109-110`).
* **Exact status vector**: `containsExactly(409)` over the distinct set, 25 × 409, 0 × 201
  (`:121-126`) — a single 500 or 200 fails.
* **Error code**: 25 × `COURSE_FULL` (`:127-128`).
* **Latency**: slowest ≤ 2000 ms (`:131-139`); measured 145–342 ms.
* **DB truth**: capacity 1, seats 0, exactly 1 registration (the holder), contender owns none,
  `availableSeats + rows == capacity` (`:142-155`).
* **Audit truth**: exactly 25 rows, all with the exact student/action/outcome/detail/course
  (`:158-168`), **25 distinct audit ids** (`:169-171`), and `count() - before == 25` (`:172`).

It asserts committed database state and the full status distribution — not “no exception thrown”.
On the Round-2 artefact this scenario produced 3–4 × 500 and ≥16/75 lost rows, so every one of those
assertions would fail there. This closes N-3 as well.

### 3.3 Transaction boundaries, connection release, ordering, idempotency

* **Boundary**: the audit write is outside the business transaction *and* outside its connection.
  `AFTER_COMPLETION` runs before `cleanupAfterCompletion`, which is exactly why the old
  `REQUIRES_NEW` approach still needed a second business-pool connection. Using a different
  `DataSource` removes that dependency rather than racing it — the correct fix, not a pool-size
  guess.
* **Connection release**: `JdbcTemplate.update` on a non-transactional `DataSource` borrows and
  returns one connection per statement (`DataSourceUtils.releaseConnection`); no `ConnectionHolder`
  or transaction is bound to `auditDataSource` under `JpaTransactionManager`, so nothing is parked
  on the request thread. 75 burst inserts + continued normal traffic with no idle-pool exhaustion
  confirms it empirically.
* **Listener ordering**: the event is published inside the `@Transactional` service method
  (`RegistrationService.java:80,93,101,107,113,124`), so a synchronization is active and
  `@TransactionalEventListener(fallbackExecution=false)` cannot drop it; it fires on both commit and
  rollback paths, which is the NFR4-1 requirement.
* **Retry idempotency**: deterministic attempt id means a retry after an ambiguous connection
  failure collides on the primary key and is treated as success (`AuditWriter.java:89-92`); the test
  T-A19-01b proves two *attempts* still yield two rows.
* **No duplicate audit**: business failures are audited exactly once in the service; the
  `@RestControllerAdvice` only audits the pre-service rejection types
  (`GlobalExceptionHandler.java:63-107,167-177`) and business `ApiException`s are not re-audited.
* **No new deadlock cycle**: register takes only the course row; drop takes registration → course;
  audit inserts touch only `audit_log` on a separate connection. Nothing takes `audit_log` then a
  course/registration row, so there is no ordering cycle. 75 live all-failure attempts plus the
  mixed 10/15 test show no lock-timeout or stall.
* **Semantics preserved**: failure rows are still `FAILURE` with the same detail vocabulary; success
  rows still join the business transaction (`AuditService.java:43-48`), so a rolled-back registration
  can never leave a success row.

**Conclusion: B-2 is genuinely closed.** N-1 is closed.

---

## 4. B-1 and the majors

| Finding | Status | Evidence |
| --- | --- | --- |
| **B-1** cross-semester registration | **CLOSED** | Semester predicate is inside the locking query (`CourseRepository.java:38-41`), used first in `register` (`RegistrationService.java:84-96`). A miss performs a non-locking existence probe only to pick the audit detail; it never reads or mutates seats. Live: 404, seats 80, empty list, `SEMESTER_NOT_ACTIVE`. `RegressionSemesterScopeTest.java:41-87` asserts 0 rows, seat 80, exactly 1 row per attempt with exact action/outcome/detail/courseCode (2 attempts → 2 rows). Drop unaffected; live 200 + 404 retry verified. A-17 recorded in `docs/specs/11-decisions.md:23`. |
| **M-1** H2 console / committed JWT secret | **CLOSED** | `application.yml:27` console off; `application-dev.yml:1-6` opts in; `SecurityConfig.java:49,57-63,69-71` permits `/h2-console/**` and `sameOrigin` frames only under `dev`, `DENY` otherwise. Live 401/401. `application.yml:32` `secret: ${JWT_SECRET:}`; `JwtService.java:35-47` requires ≥32 bytes and generates a random 256-bit key otherwise, warning without printing it. `RegressionDefaultProfileTest.java:56-73`. |
| **M-2** concurrent double drop | **CLOSED** | `RegistrationRepository.java:44-50` `PESSIMISTIC_WRITE` on the registration row; `RegistrationService.java:139-166`; stale mapping scoped to drop at `GlobalExceptionHandler.java:133-144`. `RegressionConcurrentDropTest.java:34-120` asserts exactly `{200,404}`, seats 3→4 (not 5), 1 row, C1, exactly 2 audit rows with the right composition. Live 200 then 404. |
| **M-3** DB CHECK backstop | **CLOSED (4 normative checks)** | `Course.java:39` `credit_hours in (3,6)`; `:46` `capacity >= 0`; `:50-52` `available_seats >= 0` and `<= capacity`; `CourseSchedule.java:26` `end_time > start_time`. `RegressionSchemaConstraintTest.java:49-54` asserts the five names in `information_schema`; `:63-110` asserts reject/accept incl. `credit_hours = 4/5` rejected and `capacity = 0` **accepted** (A-18); `:128-142` asserts equal/reversed slot times rejected. `day_code` remains the documented deferral (§7). |
| **M-4** seeded full course vs C1 | **CLOSED** | `DataSeeder.java:87-88` `FIT3182` capacity 1 / seats 0, `:193-206` one persisted registration for `32067890`. `RegressionDefaultProfileTest.java:78-115` asserts 19 courses, 16/3 split, 1/0/1 for `FIT3182`, 1/1/0 for `FIT3143`, exactly one seeded registration, and C1 for **every** seeded row — the old TD-4 carve-out is gone. Decision recorded as A-16 (`11-decisions.md:22`) with an explicit supersession record (`:29-38`). |
| **N-2** constraints/seed contract drift | **CLOSED** | As M-3 + M-4; A-16/A-17/A-18 recorded in `11-decisions.md:22-25,29-50`; only `day_code` is deferred, with rationale and owner (`11-decisions.md:44-50`). |
| **N-3** T-B2-01 false green | **CLOSED** | T-B2-03 (§3.2); T-B2-01 is retained as the mixed-mix case (`RegressionAuditPoolTest.java:49-89`). |
| **N-4** `SEMESTER_NOT_ACTIVE` outside the matrix | **CLOSED (spec)** | E-11 added (`docs/specs/08-non-functional.md:195`) + A-17. |
| **N-5** over-broad exception mappings | **CLOSED (code, no test)** | `GlobalExceptionHandler.java:114-144` branches on `isRegisterAttempt`/`isDropAttempt`; unmatched → 500. Unreachable without `@Version`/forced constraint violation, so inspection-only. |
| **N-6** README missing `JWT_SECRET` | **CLOSED** (triage still lists OPEN) | `README.md:29` documents `JWT_SECRET` “random per start … ≥32 chars … set it to keep tokens valid across restarts”, plus the dev-console note at `:35`. |
| **N-7** malformed attempts unaudited | **CLOSED** | `GlobalExceptionHandler.java:63-107,167-177`; `RegressionValidationAuditTest.java:32-109` (E-12 row, 2 attempts → 2 rows with distinct ids, E-13 row, 401 → 0 rows). |

---

## 5. Per-FR verdicts on the final tree

| FR | Spec | Verdict | Evidence (file:line) |
| --- | --- | --- | --- |
| **FR1 Student login** | `01-fr1` | **APPROVED** | `LoginRequest.java:10-18` (studentId pattern allows surrounding space, password ≤72 and never trimmed); `AuthService.java:41` trims id only, `:36,44-48` dummy BCrypt on unknown user, `:50-53` identical 401; `JwtService.java:49-58` HS256/sub/120 min from `JwtProperties.java:16-18`; bearer-only filter; no hash in `StudentDto`. Tests: `AuthControllerTest` 11, `SecurityMatrixTest` 42, `LoginValidationNoBcryptTest` 1, `JwtServiceTest` 3. |
| **FR2 Catalogue** | `02-fr2` | **APPROVED** | `CatalogService.java:41-50` public semesters with active flag; `:52-63` active-or-explicit semester, q over code **or** name, 100-char cap; `:65-74` detail 404 for unknown/non-UUID; `:76-99` unknown semester → 400. Frontend keeps the active term authoritative and rewrites deep links (`Catalog.tsx:46-49,77-96`). Tests: `CatalogControllerTest` 14. |
| **FR3 Register** | `03-fr3` | **APPROVED** | `RegistrationService.java:77-133`: period → semester-scoped locked course (`:84-96`) → duplicate (`:100`) → credit `> 24` (`:106`) → capacity (`:112`) → decrement+insert in one tx (`:117-127`) → success audit (`:128`); every branch audits once. Body `studentId` ignored (`RegisterRequest.java:24-26`). Unique-key race → 409 not 500 (`:118-125` + `GlobalExceptionHandler.java:114-126`). Tests: `RegistrationControllerTest` 14, `RegressionSemesterScopeTest` 2, `CapacityConcurrencyTest` 9. |
| **FR4 Drop** | `04-fr4` | **APPROVED** | `RegistrationService.java:138-177`: locked registration read (`:139`), owner (`:146`), period (`:152`), course lock (`:160`), delete (`:164-166`), clamp `min(capacity, seats+1)` (`:172`), audit both ways (`:142,147,156,169,174`). Malformed id → 404 + `DROP/FAILURE/VALIDATION_ERROR` (`RegistrationController.java:68-80`). Not idempotent (live 200 → 404). Tests: `DropControllerTest` 9, `RegressionConcurrentDropTest` 1, `RegressionValidationAuditTest` 4. |
| **FR5 Registrations + CSV** | `05-fr5` | **APPROVED** | `RegistrationService.java:183-201` semester-scoped list, `maxCredits` 24; `RegistrationRepository.java:27-34` `registeredAt ASC, code ASC`; `CsvExportService.java:45-77` exact header, CRLF, no BOM, RFC 4180 quoting, one row per registration × slot, same-semester scope (`:49`). Tests: `RegistrationViewTest` 10. |
| **FR6 Capacity** | `06-fr6` | **APPROVED** (one design follow-up, F-6) | Lock is `PESSIMISTIC_WRITE` and semester-scoped (`CourseRepository.java:38-41`), taken **before** duplicate/credit/seat checks; check+decrement+insert atomic (`RegistrationService.java:112-127`); SQL `for update` proven by `RowLockSqlTest`; 10/25 → 10×201 + 15×409, C1 holds, 25 audit rows (`CapacityConcurrencyTest#tFr6_04/05`, `RegressionAuditPoolTest#tB2_01`); all-failure 25 at shipped pools → 25×409, 0 × 5xx (§2.1). `availableSeats` bounds also enforced by DB CHECKs. |
| **FR7 Registration period** | `07-fr7` | **APPROVED** (minor F-5) | `RegistrationPeriodService.java:32-38` inclusive `[startAt, endAt]` from the injected `Clock`; reads stay open (`RegistrationService.java:183` is not period-gated); fail-fast config; auth precedes period (`SecurityConfig` chain + `RegistrationPeriodTest#tFr7_09`). 9 period tests incl. exact boundary. Sub-second truncation is F-5. |

Non-FR gates: NFR2 — console closed, no committed secret, bearer-only, no hash/trace leakage,
allow-listed CORS, opaque 500s (`SecurityMatrixTest` 42). NFR4 — one row per attempt incl. the
all-failure burst and validation rejections; no audit read endpoint. NFR1/NFR3/NFR5 manual items are
follow-ups (§7), unchanged from the earlier reviews.

---

## 6. Regression hunt on the dedicated datasource

| Risk | Assessment | Evidence |
| --- | --- | --- |
| Deadlock between pools | No. Different tables/pools; lock order is course-only (register) or registration→course (drop); audit inserts never precede a business lock. | 75 live all-failure attempts, 0 timeouts; 3/3 `RegressionAuditPoolTest` runs reported green by QA (I re-read the assertions; my run green). |
| Connection leak | No. `JdbcTemplate` borrows/returns per statement outside any transaction; sustained traffic after 3 bursts with *decreasing* latency. | §2.1 log scan; `DataSourceConfig.java:55-69` (`destroyMethod = "close"`). |
| Duplicate audit rows | No. Deterministic attempt id + `DuplicateKeyException` handling; service audits once per branch; advice only covers pre-service rejections. | `AuditWriter.java:85-95,103-108`; T-B2-03 distinct-id assertion `:169-171`; T-A19-01b. |
| Lost audit rows | No on tested paths; the residual risk is a JVM crash between commit and `afterCompletion`, which is inherent to post-commit auditing and not a regression. | 75/75 live `audit.record` lines; 0 `audit.write.lost`. |
| Committed 201 turned into 500 by an audit fault | No. Success paths publish no failure event; failure-path writes catch `DataAccessException` and never rethrow, and `afterCompletion` exceptions are swallowed by Spring. | `AuditWriter.java:57-99`; live 409s with a deliberately small audit pool. |
| Audit bottleneck at 5 connections | No at the specified 25-writer load; single-row inserts of ~ms each. A materially larger burst would queue on the audit pool, but that is bounded by the business pool (20) and the 5 s connection timeout, and cannot convert a response into a 5xx. | §2.1 max 342 ms at 25 concurrent. |
| Identity-column interaction | Fine. `AuditLog.id` is `GenerationType.IDENTITY` (`AuditLog.java:22-24`); H2 creates it `generated by default as identity`, so explicit negative ids are accepted and cannot collide with the positive identity range. | `AuditWriter.java:103-108`; all audit assertions pass. |

No new blocking behaviour was introduced by the dedicated datasource.

---

## 7. Non-blocking follow-ups (explicitly accepted for Phase 1)

**Spec / schema items**

1. **OPEN-1** — malformed `registrationId` status conflict: FR4 / T-FR4-10 / handler say 404
   `REGISTRATION_NOT_FOUND`; A-19 / NFR4.2 E-13 / `00 §9.2` say 400 `VALIDATION_ERROR`. The audit row
   (the substance of A-19) is correct either way. Owner: planner.
2. **OPEN-3 / F-1** — `course_schedule.day_code` has no DB `CHECK` (`IN ('MON'…'SUN')`), explicitly
   deferred with rationale in `docs/specs/11-decisions.md:44-50`. Owner: coder-backend, before a
   non-JPA write path exists.
3. **O-2** — NFR2-03 says 8 protected routes; the contract lists 7. Owner: planner.
4. **O-3** — the demo password is shown on the login screen while NFR2-09 says “seed/docs only”;
   09 §6.1 mandates the hint (spec conflict, no real secret). Owner: planner/coder-frontend.

**Code polish (unchanged from the earlier reviews)**

5. **F-2 (m-5)** — `RegistrationPeriodService.java:33` truncates `now` to seconds, so the window stays
   open for up to 999 ms past `endAt` (FR7-B9 says 1 ms). Tests use whole-second offsets.
6. **F-3 (m-3)** — no `Referrer-Policy` header (`NoStoreHeaderFilter.java:22-26` sets only
   no-store/nosniff) and no `<meta name="referrer">`.
7. **F-4 (m-4)** — seat meter animates `width` (`frontend/src/components/ui/SeatMeter.tsx:66`),
   against 09 §2.9. The built assets are byte-identical to the re-review's build
   (`index-Do3UQIhO.js` / `index-CtpqDCPE.css` hashes unchanged), so no frontend change landed after
   that point.
8. **F-5 (m-6/m-8/m-9)** — 20 %-alpha focus ring and account-menu keyboard pattern; hardcoded print
   “Page 1”; two alias routes (`/dashboard`, `/courses`).
9. **F-6 (FR6-B3 wording)** — seat arithmetic still writes an absolute value under the row lock
   (`RegistrationService.java:117,172`) rather than a DB-side relative decrement. Safe today (the lock
   is taken before the read and all writers obey it); the `@Modifying … available_seats - 1` form is a
   hardening item, not a defect.
10. **F-7 (m-7)** — axios client has no timeout and no bounded GET retry
    (`frontend/src/api/client.ts:96`), NFR3.2.
11. **F-8 (m-11/N-8)** — `JwtService.parseSubject` uses `verifyWith(key)` without pinning `HS256`, so
    HS384/HS512 signed with the same secret verify. Requires the secret to exploit; no privilege gain.
12. **F-9 (N-8)** — CORS still allows `127.0.0.1:5173` (`application.yml:46`); no CHECK on
    `audit_log.action/outcome`; Spring's generated `user` password is logged at startup (form/basic
    auth are disabled, so it is unusable noise).
13. **F-10 (O-4/m-12)** — no `lint` script or ESLint dependency in `frontend/package.json`.
14. **F-11 (test hygiene)** — `AuditLogTest#tNfr4_02`'s display name still says “committed in
    REQUIRES_NEW”; the assertion (row survives a rolled-back attempt) is correct and stronger than the
    wording. `RowLockSqlTest` still asserts against JVM-wide SQL output.
15. **F-12 (N-5)** — the scoped exception mappings are closed by inspection only; add a behavioural
    test if `@Version` is ever introduced.

**Manual evidence still outstanding (not run by this review; not claimed)**

16. **NFR1** — p95 measurements and browser paint under 4× CPU throttle on this final tree.
17. **NFR3** — restart/RTO, API-down UX, client-resilience behaviour.
18. **NFR5** — axe run, 200 % zoom, screen-reader pass, print check, and the manual T-FR5-11 week-grid
    rendering. (Round 1 measured 3-click register / 2-click drop on an earlier bundle; the catalogue
    and card logic has since gained the active-term guard, so the click budget and the a11y gates
    should be re-measured before sign-off. The bundle is unchanged since the re-review.)

None of items 1–18 breaks a stated acceptance criterion or is reachable as a defect on a tested path.

---

## 8. Ship decision

**FINAL VERDICT: SHIP.**

Justification for “no blocking issues”:

* **B-2** — the failure audit no longer shares the business pool at all (`DataSourceConfig.java:55-69`,
  `AuditWriter.java:57-99`), and the shipped jar survives the exact adversarial scenario: 75/75
  attempts → 409, 0 × 5xx, 0 pool errors, 0 lost audits, worst request 342 ms (was 3–4 × 500 and 30 s
  stalls). The regression that guards it (`T-B2-03`) asserts pool sizes, the exact status set, DB
  counts, distinct audit ids and latency, so it cannot pass vacuously.
* **B-1** — the semester predicate is part of the locking query; live 404 with no row, no seat change
  and a single `SEMESTER_NOT_ACTIVE` audit row; drop still works.
* **Majors/re-review majors** — M-1, M-2, M-3 (four normative checks), M-4/A-16, N-2, N-3 all closed
  with named tests; the only schema gap (`day_code`) is explicitly deferred in a recorded decision.
* **No regression from the new datasource** — no deadlock, no leak, no duplicate or lost audit row on
  the tested paths.
* **All seven FRs** conform with file:line evidence; 146/146 backend tests and a green production
  frontend build.

The remaining items are the documented minor spec conflicts, UI polish and manual NFR gates listed in
§7. Ship Phase 1; schedule F-1…F-12 and the manual NFR passes as Phase 1.1 hygiene.

---

*Report scope: `docs/reviews/phase-1-final-review.md` only. No `git` command was run. No production
code, test, spec or QA file was modified. The two Spring Boot instances used for measurement were
stopped and their ports released.*
