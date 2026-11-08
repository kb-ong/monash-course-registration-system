# Phase 1 — Independent Review (reviewer agent)

**Project:** Monash Course Registration System — Phase 1
**Branch reviewed:** `feat/phase-1` (working tree as presented, no git commands run)
**Reviewer:** reviewer agent
**Date:** 2026-11-08 (Asia/Kuala_Lumpur)
**Baseline:** `ORCHESTRATION.md`, `docs/specs/00`–`10`, `docs/qa/phase-1-test-report.md`, `docs/qa/triage.md`

**Ship verdict: CHANGES REQUIRED** — 2 blocking defects, 4 major, 12 minor. The backend is well
structured and the concurrency design is fundamentally right, but two behaviours are wrong in ways the
test suite cannot see: **registration is not scoped to the active semester**, and **the shipped
connection-pool configuration cannot survive the spec's own 25-writer concurrency scenario** (16/25
requests returned 500 and lost their audit rows in a live reproduction).

I confirm the QA numbers — 132 backend tests pass and the frontend builds — but I do **not** accept them
as proof of correctness. Every defect below was reproduced against the running artifacts or read out of
the code, not inferred from a red test.

---

## 1. Environment, commands, results

| Item | Value |
| --- | --- |
| Host | `NL0789`, Linux 5.15 (WSL2 x64), Node v24.18.0 |
| JDK / Maven | OpenJDK 23.0.2 (release 21) / Maven 3.9.9 |
| Backend runtime | `java -jar target/course-registration-0.1.0.jar` on `:8080` (plus throwaway instances on `:8081`, `:8082`) |
| Frontend runtime | `npm run dev` on `:5173` |
| Browser | Google Chrome 150 headless via CDP (`--remote-debugging-port=9222`), DOM + computed styles + print emulation |
| All processes stopped after review | yes (`8080`, `5173`, Chrome all down) |

Commands run (all independently, from a clean tree):

```bash
cd backend && mvn -B clean test
# [INFO] Tests run: 132, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS   (35.202 s)

cd backend && mvn -B -q -DskipTests package
# target/course-registration-0.1.0.jar (53,050,294 bytes)

cd frontend && npm run build
# tsc -b && vite build -> 2034 modules transformed, built in 4.75 s, exit 0
# dist/assets/index-ahfyhwKw.js 449.88 kB, index-CtpqDCPE.css 37.91 kB
```

Runtime probes used as evidence (reproducible):

```bash
# login, catalogue, CSV, 401 matrix, IDOR
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"studentId":"32012345","password":"Password123!"}' | jq -r .token)
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/courses' | jq length        # 16
curl -s -D - -o /tmp/out.csv -H "Authorization: Bearer $TOKEN" \
  localhost:8080/api/registrations/export | grep -i 'content-'
xxd -l 16 /tmp/out.csv            # no EF BB BF; CRLF confirmed with cat -A
```

Performance spot checks (NFR1-P2/P5, 30 requests, first 3 discarded):

| Metric | Threshold | Measured |
| --- | --- | --- |
| `GET /api/courses?q=algo` server time | < 250 ms p95 | **p95 7 ms** (p50 7 ms) |
| `POST /api/auth/login` | < 500 ms p95 | **p95 73 ms** (p50 71 ms) |

Not verified (stated honestly): NFR1-P1 end-to-end paint under 4× CPU throttle, axe DevTools run,
200 % zoom, VoiceOver/NVDA, Maven dependency CVE scan, multi-node concurrency. This review used DOM and
computed styles, not human eyes; visual polish is assessed structurally against §09, not by screenshot.

---

## 2. Per-feature verdicts

| FR | Spec ref | Verdict | Evidence |
| --- | --- | --- | --- |
| FR1 Student login | `01-fr1` | **APPROVED** | Trim + no-trim semantics (`AuthService.java:41`, `LoginRequest.java:11-17`); dummy BCrypt on unknown user (`AuthService.java:36,44-47`); identical 401 bodies (`AuthService.java:46-52`); HS256/sub/120 min verified by decoding a live token (`JwtService.java:31-39`, payload `{sub,iat,exp}`, ttl 7200 s); no hash in any DTO; `WWW-Authenticate: Bearer` (`RestAuthenticationEntryPoint.java:31`); open-redirect guard (`Login.tsx:16-29`). |
| FR2 Catalogue | `02-fr2` | **APPROVED** | Active-semester default and unknown-semester 400 (`CatalogService.java:76-89`); 16/3 counts, code ASC, `q` on code OR name, 100-char cap (`CatalogService.java:52-63`); public `/semesters` + active flag (`CatalogService.java:41-50`); detail 404 for non-UUID (`CatalogService.java:65-74`). Live: 16 courses `ECF1100`…`SCI1020`. |
| FR3 Register | `03-fr3` | **CHANGES REQUIRED** | **B-1**: no active-semester check on the write path (`RegistrationService.java:83`). Otherwise correct: order period → exists → duplicate → credit → capacity (:77-107), exact-24 allowed (`> MAX_CREDITS` :98), body `studentId` ignored (`RegisterRequest.java:6-9`), audit on every branch (:79-106). |
| FR4 Drop | `04-fr4` | **CHANGES REQUIRED** | **M-2**: concurrent double-drop returns 500 and writes no audit row. Correct otherwise: exists → owner → period (`RegistrationService.java:131-148`), 404 for malformed id (`RegistrationController.java:71-77`), clamp `min(capacity, seats+1)` (:155), NOT_OWNER leaks nothing. |
| FR5 View registered + CSV | `05-fr5` | **APPROVED** (blocked only by B-1 upstream) | Semester-scoped read (`RegistrationService.java:165-182`), `maxCredits` 24, ordering `registeredAt ASC, code ASC` (`RegistrationRepository.java:25-32`), empty → 200/`[]`. CSV byte-verified: exact header, no BOM, CRLF, RFC 4180 quoting, one row per slot (`CsvExportService.java:44-90`; live export of FIT1045 produced two rows). |
| FR6 Capacity | `06-fr6` | **CHANGES REQUIRED** | Lock is real and correctly scoped: `PESSIMISTIC_WRITE` (`CourseRepository.java:30-32`), acquired **before** duplicate/credit/seat checks (`RegistrationService.java:83`), seat check + decrement + insert in one tx (:104-118), SQL proof `... for update` (`RowLockSqlTest`). **B-2**: the shipped pool config turns the spec's 25-writer scenario into 16 × 500. **M-3**: no DB `CHECK` constraints. **M-4**: seed violates invariant C1. |
| FR7 Registration period | `07-fr7` | **APPROVED** (1 minor) | Closed interval from an injectable `Clock` (`RegistrationPeriodService.java:32-38`); `startAt`/`endAt` inclusive; reads stay open; fast-fail config (`RegistrationProperties.java:30-37`); auth precedes period (tested live via 401-first matrix). **m-5**: comparison truncates to seconds, so the window stays open up to 999 ms past `endAt` (spec B9 says 1 ms is closed). |
| NFR2 Security | `08 §NFR2` | **CHANGES REQUIRED** | **M-1**: unauthenticated H2 console with full DB access (verified). Secret handling, hashing, enumeration, IDOR, mass assignment, error hygiene and CSRF posture are correct — see §4. |
| NFR4 Audit | `08 §NFR4` | **CHANGES REQUIRED** | Design is right (`AuditService.java:33-41`, `REQUIRES_NEW` for failures, same-tx for success) and E-01…E-10 rows exist in tests, **but** B-2 and M-2 lose rows under load/races: 25 attempts produced 9 `COURSE_FULL` rows; a concurrent double-drop produced 1 row for 2 attempts. |
| NFR5 Usability | `08 §NFR5` | **APPROVED** (3 minor) | Measured in a real browser: register = **3 clicks**, drop = **2 clicks**; dialog opens with the safe action focused; success toast + card flips to `REGISTERED`; focus trap and `Esc` verified; skip link first in tab order; reduced motion yields 0.001 s transitions; 360 px has no horizontal scroll. Minor: m-4, m-6, m-8. |
| NFR1 Performance | `08 §NFR1` | **PARTIAL** | P2/P5 measured well inside threshold (table above). P1/P6 (browser paint, cold load) not measured — not claimed. |
| NFR3 Availability | `08 §NFR3` | **PARTIAL** | Envelope on unexpected fault, fail-fast window, fail-closed UI on period error all present. Not verified: restart/RTO, API-down UX. Client has no axios timeout/retry (m-7). |

---

## 3. Findings

### BLOCKING

#### B-1 — Registration accepts courses outside the active semester; the registration is then invisible and the credit cap is bypassable

* **Where:** `backend/src/main/java/edu/monash/registration/service/RegistrationService.java:83`
  (`courseRepository.findWithLockById(courseId)` — no semester predicate);
  `backend/src/main/java/edu/monash/registration/repository/CourseRepository.java:30-32`.
* **Spec:** FR3 §1 "register for a course in the **active semester**"; FR5-B1 "Registrations from other
  semesters are not returned (**none can exist in Phase 1**…)"; FR3-B7 credit cap is computed over the
  active semester only.
* **Reproduced (live):**
  ```
  GET  /api/courses?semester=2026-S1      -> FIT1008 cccccccc-…-0001
  POST /api/registrations {courseId:FIT1008}
    -> 201 {"registrationId":"907a72a7-…","course":{"semester":"2026-S1","availableSeats":79,…}}
  GET  /api/registrations
    -> {"totalCredits":0,"items":[]}
  ```
  A `registration` row exists, exactly one seat was consumed in a **prior-semester** course, and the
  student cannot see it in My Schedule or the CSV export (both filter on the active semester). It also
  does not count toward the 24-credit cap, so the cap can be bypassed without limit.
* **UI reachable:** the catalogue's semester `<select>` lists `2026-S1`
  (`frontend/src/pages/Catalog.tsx:156-167`) and the prior-semester cards render an **enabled**
  `Register` button (`CourseCard.tsx:342-364`). Verified in the browser: `/catalog?semester=2026-S1`
  → 3 cards, `Register` `disabled:false` ×3. Two clicks create an orphan registration.
* **Why tests miss it:** every registration test uses a `2026-S2` fixture
  (`TestFixtures.singleSlotCourse` hard-codes `S2`, line 403-406). There is no negative test for
  "course exists but is not in the active semester".
* **Fix:** scope the lock lookup — e.g. `findWithLockByIdAndSemester(id, activeSemester)` — and treat a
  miss as `404 COURSE_NOT_FOUND` (consistent with catalogue scoping). The contract has no code for
  "out of scope"; record the decision in `00 §10` as a new ambiguity (A-15) so the frontend can map it.
  Add T-FR3-15: POST a `2026-S1` course id → 404 and **no** row, **no** seat change, **one**
  `REGISTER_REJECTED/FAILURE/COURSE_NOT_FOUND` audit row.

#### B-2 — Failure-path audit (`REQUIRES_NEW`) doubles connection demand under a row lock; the shipped pool of 10 turns the spec's 25-writer scenario into 500s and lost audit rows

* **Where:** `service/AuditService.java:38-41` (`REQUIRES_NEW`),
  `service/RegistrationService.java:79-107,193-199` (audit failure called **inside** the transaction
  that holds the `SELECT … FOR UPDATE` lock), `resources/application.yml:7-11` (no Hikari tuning →
  default `maximumPoolSize=10`), `src/test/resources/application-test.yml:10-11`
  (`maximum-pool-size: 40`) and `:6` (`LOCK_TIMEOUT=20000`).
* **Spec:** FR6-AC1 ("exactly N succeed, M−N receive 409 `COURSE_FULL`"), FR3-B14 / NFR4-1 ("every
  attempt writes exactly one audit row"), NFR3-A4/A6.
* **Reproduced (live, stock jar, default config, 25 concurrent POSTs to the seeded-full `FIT3182` with
  the three seeded students — the same shape as T-FR6-04):**
  ```
  codes: {(500, INTERNAL_ERROR): 16, (409, COURSE_FULL): 9}
  latency: min 2.03 s, max 4.28 s
  audit_log: REGISTER_REJECTED/FAILURE/COURSE_FULL = 9 rows   (expected 25)
  log: CannotCreateTransactionException: Could not open JPA EntityManager for transaction
       HikariPool-1 - Pool stats (total=10, active=10, idle=0, waiting=30)
  ```
  A 60-request burst of the same failing call reproduced the stall (9 × 500, max latency 61.3 s); an
  instance forced to `--spring.datasource.hikari.maximum-pool-size=2` returned **8 × 500 out of 10**,
  which isolates the cause to connection starvation rather than the seat logic.
* **Mechanism:** each failing request holds one pooled connection for its outer transaction (and, on the
  seat check, the course row lock) while `REQUIRES_NEW` asks for a **second** connection. At 10
  connections the pool deadlocks against itself until `connectionTimeout` (30 s). The request then
  surfaces `INTERNAL_ERROR`, and because the audit write itself failed, the NFR4 row is lost.
* **Why tests miss it:** the test profile raises the pool to 40 (1 test connection per request + 1 for
  the nested audit ≈ 2 × 25 = 50, but H2 lock serialisation keeps concurrency below the ceiling). The
  green suite therefore says nothing about the artifact that ships. This is exactly the
  "tests pass ≠ correct" trap.
* **Fix (any of a/b, plus c):**
  a. Stop opening a nested transaction while holding the business transaction: emit the failure audit
     after the transaction completes — `TransactionSynchronizationManager.registerSynchronization(...)`
     `afterCompletion`, or `@TransactionalEventListener(phase = AFTER_COMPLETION)` on an application
     event, or write it from the exception handler with the request's `studentId`/`courseCode` carried on
     the exception. The audit stays `REQUIRES_NEW` but no longer competes with the outer connection.
  b. Never call an audit write inside the lock scope; capture the outcome and write after the seat
     decision.
  c. Size the datasource explicitly and make the test profile mirror it:
     `spring.datasource.hikari.maximum-pool-size` ≥ 2 × max concurrent writers (or ≥ 50),
     add `LOCK_TIMEOUT=20000` to the production H2 URL, and add a regression test that runs with the
     production pool size (not 40).
* **Acceptance:** re-run the 25-writer burst against the packaged jar with default config: 0 × 5xx,
  exactly 10/15 (or 0/25 for the full course) and exactly one audit row per attempt.

### MAJOR

#### M-1 — H2 web console is publicly exposed and grants unauthenticated full database access

* **Where:** `resources/application.yml:19-22` (`h2.console.enabled: true`),
  `config/SecurityConfig.java:57` (`/h2-console/**` → `permitAll`), `:52` (`frameOptions.sameOrigin()`
  exists only to make the console frameable).
* **Spec:** NFR2-S8/S9 (least exposure), NFR2-S2/S6 (password material must never leak).
* **Reproduced:** `GET http://localhost:8080/h2-console/` → 200 with no token; `POST login.do` with
  `user=sa`, empty password → authenticated; `query.do` returned
  `select student_id, password_hash from student` → 3 rows of `$2a$10$…`. Write access (fabricating
  registrations, granting seats) follows trivially. Tomcat binds all interfaces, so this is not limited
  to localhost.
* **Fix:** default the console off (`spring.h2.console.enabled=false`), enable it only under a `dev`
  profile; remove `/h2-console/**` from `permitAll`; drop `frameOptions.sameOrigin()` when the console is
  off. Add a test that `/h2-console` is 401/404 in the default profile.

#### M-2 — Concurrent double-drop returns 500 `INTERNAL_ERROR` and writes no audit row

* **Where:** `service/RegistrationService.java:131` (registration read **without** a lock),
  `:150-155` (course lock taken only after the read, then `delete` + `flush`).
* **Spec:** FR4-B8 ("dropping is not idempotent: the second call is 404"), FR4-AC4, E-08, NFR4-1.
* **Reproduced (live, two threads released by a barrier on the same `registrationId`):**
  ```
  (200, {"message":"Dropped FIT1055 Industry Experience.","availableSeats":30})
  (500, {"error":{"code":"INTERNAL_ERROR","message":"An unexpected error occurred. Please try again."}})
  log: ObjectOptimisticLockingFailureException: Row was updated or deleted by another transaction …
  audit_log DROP rows: 1   (two attempts)
  ```
  Seats stay correct (the failed transaction rolls back before the increment), so this is a contract and
  audit defect, not oversell.
* **Fix:** load the registration with `@Lock(PESSIMISTIC_WRITE)` (`findByIdWithLock`) so existence,
  ownership, period and delete are serialised; or catch `ObjectOptimisticLockingFailureException` /
  `StaleObjectStateException` and map it to `404 REGISTRATION_NOT_FOUND` **with** a
  `DROP/FAILURE/REGISTRATION_NOT_FOUND` row. Add T-FR4-11 (concurrent double drop → 200 + 404, one
  audit row per attempt).

#### M-3 — No database `CHECK` constraints; invariants C2/C3 have no backstop

* **Where:** `domain/Course.java:38-48` (no `@Check`), `domain/CourseSchedule.java:30-41` (no
  day-set check, no `end_time > start_time`).
* **Spec:** 00 §7.3 (`CHECK (credit_hours IN (3,6))`, `CHECK (capacity >= 0)`,
  `CHECK (available_seats >= 0 AND available_seats <= capacity)`), 00 §7.4 (`day IN (…)`,
  `end_time > start_time`); FR6-B7/C3.
* **Reproduced:** `select count(*) from information_schema.check_constraints` → **0 rows**; the schema has
  3 FKs, 5 PKs, 4 UNIQUEs and zero CHECKs.
* **Fix:** add Hibernate `@Check` constraints (or an explicit `schema.sql` for H2) and a test asserting
  each constraint name exists / that an out-of-range update is rejected.

#### M-4 — Seeded "full" course violates invariant C1, and the suite formalises tolerance of the inconsistent state

* **Where:** `seed/DataSeeder.java:82-85` (`FIT3182`, capacity 40, seats 0, no registrations);
  `CapacityConcurrencyTest.java:171-186` and `triage.md` TD-4 deliberately skip C1 for it.
* **Spec:** 00 §7.3 "**Invariant C1:** `available_seats + count(registration) = capacity`, **always**";
  FR6-B6/AC3; 00 §8.2 "one course at capacity 0 seeded as full"; A-01 resolves this as capacity 40 /
  seats 0.
* **Assessment:** this is a genuine contradiction between A-01/§8.2 and C1/FR6-B6, not a hidden oversell
  bug — the row lock still protects the seat counter (`FIT3182` yields 409 and never a seat). It does,
  however, hide two real facts: `availableSeats` is authoritative mutable state rather than derived
  from registrations, and `drop`'s clamp (`min(capacity, seats+1)`, `RegistrationService.java:155`)
  accepts exactly the same inconsistent state. T-FR4-07 even *creates* `seats == capacity` while a
  registration still exists and asserts the implementation tolerates it. So the invariant is only
  asserted for "well-formed" fixtures.
* **Fix (needs a planner decision, not a coder guess):** either seed the demo-full course as
  `capacity = 0` (legal per A-01) so C1 holds trivially; or introduce an explicit "held seats" concept
  and restate C1 as `availableSeats + count ≤ capacity`; or seed the 40 holds as rows. Then remove the
  TD-4 carve-out and assert C1 for every seeded course.

### MINOR

| ID | Finding | Where | Spec | Fix |
| --- | --- | --- | --- | --- |
| m-1 | Dev JWT secret default is committed and the README never warns (RK5 says it must) | `application.yml:26`; `README.md` | NFR2-S2, 00 RK5 | Document `JWT_SECRET` in the README; log a loud WARN (or refuse to start) when the default is used outside the `dev` profile |
| m-2 | CORS allows a second origin `http://127.0.0.1:5173` | `application.yml:33`, `application-test.yml:33` | 00 §9.7 "only `http://localhost:5173`" | Drop it, or amend the spec |
| m-3 | No `Referrer-Policy: no-referrer` on the SPA or API | `NoStoreHeaderFilter.java:22-26` (only cache/nosniff), `index.html` | NFR2-S9 | Add the header in the filter and a `<meta name="referrer">` |
| m-4 | Seat meter animates `width` (verified inline `width:100%`, `transition-property: all`) | `components/ui/SeatMeter.tsx:588-597` | 09 §2.9 "never animate height, width, top"; NFR1.2 | Animate `transform: scaleX()` on a full-width fill, or `clip-path` |
| m-5 | Period comparison truncates `now` to seconds, so the window is still open up to 999 ms after `endAt` | `RegistrationPeriodService.java:33-37` | FR7-B9 "1 ms after `endAt` → closed" | Compare un-truncated instants; truncate only for display |
| m-6 | Focus indicator on inputs is a 3 px ring at 20 % alpha with `outline` removed; header account menu (`role="menu"`) has no roving focus / arrow keys | `components/ui/Input.tsx:105-118`; `components/Header.tsx:231-259` | NFR5-U5/U6; 09 §7 | Use a ≥3:1 focus ring (solid `blue-600`); implement menu keyboard pattern or use a plain disclosure |
| m-7 | Axios client has no 10 s timeout and no single retry for idempotent GETs | `api/client.ts:96-102` | NFR3.2 §6 | Add `timeout: 10000` and a bounded GET retry with 300 ms backoff |
| m-8 | Print footer hardcodes "Page 1"; login hero says "Open enrolment" where §6.1 shows "16 courses · Semester 2 2026" | `pages/Schedule.tsx:650`; `pages/Login.tsx:162-171` | 09 §8, §6.1 | Use a CSS page counter (`@page` margin box) or drop the number; align hero copy |
| m-9 | Two extra routes (`/dashboard`, `/courses`) not in the module map; login page has no `<footer>` landmark | `App.tsx:34-37`; `pages/Login.tsx` | 00 §6.2; 09 §7 | Remove aliases or document them; wrap the login credit line in `<footer>` |
| m-10 | `ObjectOptimisticLockingFailureException` falls through to the generic 500 handler | `api/GlobalExceptionHandler.java:85-90` | 00 §9.2 | Map stale-state to 404/409 as appropriate (root cause of M-2) |
| m-11 | JWT parser accepts any HMAC alg (verified: a hand-made **HS512** token signed with the app secret was accepted) | `security/JwtService.java:42-52` | FR1-B5 "HS256" | Pin the algorithm (`verifyWith` + `require` alg check) — no privilege escalation today, but it is unrequested surface |
| m-12 | QA report says "`npm run lint` cannot run: the script exists but the binary does not" — there is **no** `lint` script at all | `frontend/package.json` (`dev`, `build`, `preview` only); `phase-1-test-report.md` §2 | — | Correct the report; add ESLint or stop referencing it |

Dependency scan: `npm audit --omit=dev` → 0 critical / 0 high / **2 moderate** (react-router open-redirect
via backslash GHSA-wrjc-x8rr-h8h6 and an SSR-hydration advisory; no SSR in this app, and
`Login.tsx:16-29` already rejects `/\`). NFR2-S12 ("no known-critical") is satisfied; a react-router 7
upgrade is a separate change.

---

## 4. Security checklist (NFR2)

| Control | Requirement | Result | Evidence |
| --- | --- | --- | --- |
| S1 Password storage | BCrypt cost 10, `$2a$`, never returned/logged | **PASS** | `SecurityConfig.java:80-83`; live `information_schema` hash is `$2a$10$…` length 60; DTOs carry no hash; `AuthService` never logs the password |
| S2 Token | HS256, sub = studentId, 120 min, secret from env, ≥32 bytes | **PASS with caveats** | Decoded live token: `{"alg":"HS256"}`, `sub=32012345`, ttl exactly 120.0 min; `JwtProperties.java:15-21` enforces ≥32 chars. Caveats: committed dev default (m-1), alg not pinned (m-11) |
| S3 Token transport | Bearer header only, no URL/query/cookie | **PASS** | `JwtAuthenticationFilter.java:34,54-70` |
| S4 Input validation | UUID parse, `q` cap, studentId regex, body validated | **PASS** | `RegistrationController.java:63,97-103`; `CatalogService.java:27-28,54-57`; `LoginRequest.java:11-17` |
| S5 SQL safety | Parameter binding only | **PASS** | JPQL with `@Param` throughout; no string concatenation; `1' OR '1'='1` → 400 (`T-NFR2-10`) |
| S6 Output hygiene | No hashes/traces/class names/SQL | **PASS** | `GlobalExceptionHandler.java:36-90`; live 500 body is opaque; tests assert absence of `$2a$`, `Exception`, `at edu.monash` |
| S7 Error uniformity | Unknown user ≡ wrong password | **PASS** | `AuthService.java:41-53` (same exception/message), dummy BCrypt hash :36 |
| S8 CORS | Allow-list, no wildcard, no credentials | **PASS** (one extra origin) | `SecurityConfig.java:66-78`; `OPTIONS` from `evil.example` gets no ACAO; m-2 |
| S9 Headers | nosniff, no-store, referrer policy | **PARTIAL** | no-store + nosniff on `/api/**` verified; **no** `Referrer-Policy` (m-3) |
| S10 Secrets in logs | No password/token/Bearer values | **PASS** | `AuthService.java:46,55`; `JwtAuthenticationFilter.java:99` logs only reason/path; `RegistrationService` logs ids only |
| S11 Brute force | Documented limitation | **PASS (accepted)** | FR1-B12; bcrypt cost dominates (73 ms measured) |
| S12 Dependencies | No known-critical CVE | **PASS** | npm audit: 0 critical/high, 2 moderate |
| IDOR (E9) | Another student's registration → 403, no leak | **PASS** | Live: Aisha → Daniel's id = 403 `NOT_OWNER`, body names neither course nor owner; Daniel still registered |
| Mass assignment | Body `studentId` ignored | **PASS** | `RegisterRequest.java:6-9`; live/test T-FR3-12 |
| CSRF | Stateless bearer, CSRF off is safe | **PASS** | `SecurityConfig.java:46,51` (no cookies for the API; token in header only) |
| H2 console | Not exposed | **FAIL** | M-1, reproduced |
| Demo credentials clearly demo-only | Labelled, no real secret | **PASS (labelled)** | `Login.tsx:305-324` "Demo account", README line 23. Note: NFR2-09 says the password appears "only in seed/docs" — 09 §6.1 **mandates** the on-screen hint, so the two specs contradict each other. Frontend is compliant with 09; the spec conflict should be resolved, not "fixed" in code |

---

## 5. Concurrency & consistency review

* **Lock scope for capacity** — correct. `findWithLockById` (`CourseRepository.java:30-32`) is called
  first inside the `@Transactional(isolation = READ_COMMITTED)` method (`RegistrationService.java:75,83`),
  and the seat check, decrement, insert and `saveAndFlush` all happen while it is held (:104-118).
  Duplicate detection and the credit sum also run under the lock, so a same-student double-click cannot
  race past both the `existsBy` check and the unique key. The emitted SQL contains `for update`
  (`RowLockSqlTest`), and 10 seats / 25 writers gives exactly 10 × 201 when the pool is large enough.
* **Check order** — observable order matches A-03 (period → course existence → duplicate → credit →
  capacity) even though the lock is acquired before the duplicate check; that matches FR6 §3's own
  reference implementation, so it is not a finding. Only side effect: failures also take the row lock,
  which increases contention (and is a contributing factor to B-2).
* **Drop cannot overshoot** — `Math.min(capacity, seats + 1)` (:155) is correct, and the increment runs
  under the same course lock.
* **Drop vs drop** — broken: M-2 (no lock on the registration row, stale delete → 500 + lost audit).
* **Drop vs register** — correct: both serialise on the course row; the invariant held in the burst test
  and in a live 1-seat race.
* **Seat arithmetic** — `course.setAvailableSeats(availableSeats - 1)` and dirty-checking writes an
  absolute value. Correct *only because* the row was read under `FOR UPDATE` in the same transaction.
  FR6-B3 asks for a DB-side relative decrement ("not by re-saving a stale in-memory entity"). The code
  is safe today but fragile: any future writer that bypasses the lock silently loses updates. Consider
  a `@Modifying` relative `UPDATE … SET available_seats = available_seats - 1 WHERE id = ? AND
  available_seats > 0` with a row-count check.
* **Audit on rollback** — the success row joins the business transaction (correct); failure rows use
  `REQUIRES_NEW` (correct in principle, verified by T-NFR4-02) — but B-2 breaks it under pool pressure
  and M-2 skips it entirely on the stale-delete path. NFR4-6 (never mask the original error) is
  implemented properly (`RegistrationService.java:193-199`).
* **Transactional self-invocation** — none. `auditFailure` is a private helper on the same bean but it
  delegates to the `AuditService` **bean**, so the proxy applies. No `this.` call crosses a
  `@Transactional` boundary.

---

## 6. Data integrity

| Check | Result | Evidence |
| --- | --- | --- |
| `uk_registration_student_course` present | **PASS** | `Registration.java:18-22`; `information_schema` shows `UK_REGISTRATION_STUDENT_COURSE` |
| Student `student_id` / `email` unique, course `code` unique | **PASS** | `Student.java:17-22`, `Course.java:24`; 4 UNIQUE constraints in the live schema |
| FKs (registration→student/course, schedule→course) | **PASS** | 3 FKs in the live schema |
| CHECK constraints (credit 3/6, seats 0..capacity, day set, end>start) | **FAIL** | M-3, zero CHECK constraints |
| Credit limit exactly 24 allowed, 25 rejected | **PASS** | `RegistrationService.java:98` uses `>`; T-FR3-08 (18+6=24) and T-FR3-09 (20+6) pass |
| `0 ≤ availableSeats ≤ capacity` under load | **PASS** | Burst tests assert both bounds and C1; live bursts never oversold |
| C1 for every seeded row | **FAIL** | M-4 (`FIT3182` 40/0/0) |
| Semester scoping on read | **PASS** | `RegistrationRepository.java:18-32`, `CsvExportService.java:48-49` |
| Semester scoping on write | **FAIL** | B-1 |
| Audit append-only | **PASS** | No update/delete path for `AuditLog`; no audit endpoint (T-NFR4-08) |

---

## 7. Frontend review

**Contract conformance**
* Base path `/api` only; no host string anywhere in `src` (`api/client.ts:97`). The only
  `http://localhost:8080` in the repo is the Vite dev proxy (`vite.config.ts:42,51`), which the spec
  mandates. **PASS**
* Bearer interceptor on every request (`client.ts:104-110`); 401 clears `monash.token` and emits
  `monash:session-expired` (`:112-129`), which redirects to `/login?reason=expired` and preserves
  `state.from` (`AuthContext.tsx:127-142`, `RequireAuth.tsx:20-27`). **PASS**
* Error mapping branches on `error.code`, never on message (`RegisterDialog.tsx:63-104`,
  `DropDialog.tsx:217-245`). **PASS**

**Disabled states** — full / closed / credit-limited / already-registered all render distinct labels and
reasons (`CourseCard.tsx:342-364`; `CourseDetail.tsx:112-129`), the dialog is the only path to a mutation,
and double-submit is blocked while pending (`RegisterDialog.tsx:46`, `Modal.tsx:368-372`). **PASS**
except that B-1 leaves `Register` enabled on out-of-scope semesters.

**Accessibility (verified in a real browser)**
* Dialog: `role="dialog" aria-modal="true" aria-labelledby`, focus moved to the safe action (`Cancel`/
  `Keep it`), Tab cycling trapped, `Esc` closes when not pending, focus restored to the trigger,
  background scroll locked (`Modal.tsx:353-409`). Measured: `initialFocus="Cancel"`, trap true,
  `Esc` → dialog gone.
* Labels: real `<label for>` with generated ids; `aria-invalid` + `aria-describedby` wiring for hint and
  error (`Input.tsx:76-125`). **PASS**
* Live regions: catalogue count is `aria-live="polite"`; the status banner is `aria-live="polite"` and I
  measured **0 DOM mutations in 3 s** while open (the relative label only changes at day granularity),
  so there is no per-second announcement storm; the `dd HH:mm:ss` countdown is `aria-hidden` with an
  `sr-only` minute-level label (`StatusBanner.tsx:486-489`). **PASS**
* Skip link is the first tab stop; one `<h1>` per page; landmarks present on app pages. **PASS**
* Reduced motion: global override yields `transition-duration: 0.001s` and every framer animation checks
  `useReducedMotion`. **PASS**
* Focus ring weakness and the account menu's keyboard pattern: m-6.

**Print (FR5-B12)** — emulated `@media print` and confirmed: `header`, `footer`, `button`, `.no-print`,
`.status-banner` all `display:none`; `.print-only` becomes `block`; body background `#fff`; the print
document contains the title `Monash University · Course timetable`, meta
`Daniel Tan (32067890) · Semester 2 2026 · 6 of 24 credit points` + `Printed 8 November 2026`, a weekly
timetable table and a 7-column course table with 2 rows for the 2-slot course. **PASS** (m-8 for the
hardcoded page number).

**Responsive** — at 360 × 640 the document scroll width equals the client width (no horizontal scroll);
the schedule switches to Timetable/Courses tabs under `lg`. At 1440 the catalogue is a 3-column grid.
**PASS**

**Design-system fidelity (09)**
* Tokens match: h1 computed `Fraunces 600 36px/42`, body Inter, card radius 14 px, `e1` shadow,
  container `max-width:1200px` with 24 px gutters, palette is the frozen five plus the permitted
  extended scale (a handful of extra shades of the same hues: `gold-500/700`, `danger-300/700/800`).
  Fonts actually load (`document.fonts.check` true for both families).
* Motion: durations/easings match the token table, except the seat meter (m-4).
* Not generic: bespoke navy radial-gradient hero with grain, gold hairline rules, glass card, serif
  display type, seat/credit meters. It does not read as a stock Tailwind/Bootstrap template. (Judgement
  is from DOM and computed styles; I did not evaluate visual polish by eye.)

**NFR5 click budget (measured)** — register 3 clicks from Dashboard (`Browse catalogue` → `Register` →
`Confirm register`), drop 2 clicks from My Schedule (`Drop` → `Drop course`), with correct toasts and
card/row state changes. **PASS**

---

## 8. Test-quality assessment

**Verdict: genuinely good backend suite, with two blind spots that matter.**

Strengths (not tautological):
* Assertions are on **database state**, not just status codes: registration row counts, seat values,
  invariant C1, audit row contents/timestamps, `existsByStudentIdAndCourseId` (`CapacityConcurrencyTest`,
  `AuditLogTest`, `DropControllerTest`).
* The concurrency tests are real: N **distinct** students (so a duplicate 409 cannot mask an oversell),
  `CountDownLatch` + `ExecutorService`, assertions on counts and the invariant, repeated across fresh
  courses (`CapacityConcurrencyTest:76-132`), and a SQL-level proof that `for update` is emitted
  (`RowLockSqlTest`).
* Byte-level CSV assertions (header exact, no BOM, CRLF, quoting) and a no-leak scan over six response
  bodies (`RegistrationViewTest`, `SecurityMatrixTest`).
* 7 protected routes × 5 token states = 35 parameterised auth cases incl. `NOT_BEARER`.
* Test isolation is sound (`@DirtiesContext(AFTER_CLASS)`, per-test `clearAll()`), and the `@SpyBean`
  no-BCrypt test proves validation precedes hashing without relying on a timing threshold.

Weaknesses / gaps:
1. **No test for out-of-scope semester registration** — the defect that blocks the release (B-1). Every
   registration fixture is `2026-S2` (`TestFixtures.java:403-406`).
2. **Test-only configuration hides a production failure** — `application-test.yml` sets
   `maximum-pool-size: 40` and `LOCK_TIMEOUT=20000`; production ships 10 and the default timeout. The
   green 25-writer test is not evidence about the shipped jar (B-2). A test should run at the production
   pool size (or assert the configured size is adequate).
3. **No concurrency test for double-drop** (M-2); T-FR4-06 is sequential only.
4. `RowLockSqlTest` asserts `output.getAll().contains("for update")` — the captured output is JVM-wide,
   so an unrelated query could satisfy it. Scope it (e.g. assert the log line for the specific course id,
   or use Hibernate statistics).
5. No test that a *deleted* student's still-valid token yields 401 (the code does handle it:
   `UnauthorizedException` from `studentRepository`); no test for `q` matching a 100-char boundary
   exactly; no test for a course with zero schedule slots in CSV.
6. NFR1/NFR5 remain manual — correctly disclosed by the tester, but that means the "PASS" verdict never
   covered the UI.

**On the A-01 contradiction (TD-4)** — the suite explicitly does not assert C1 for the seeded-full course
because the seed itself violates C1. That carve-out does **not** hide an oversell bug: the lock still
prevents one (verified live). It does hide that `availableSeats` is authoritative state and that `drop`'s
clamp tolerates the same impossible state (M-4). Treat it as a spec defect that must be decided before
Phase 2 adds admin capacity editing.

---

## 9. Reproduction pack (for the owning coders)

```bash
# B-1
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"studentId":"32012345","password":"Password123!"}' | jq -r .token)
S1=$(curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/courses?semester=2026-S1' | jq -r '.[0].id')
curl -s -X POST localhost:8080/api/registrations -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"courseId\":\"$S1\"}"        # 201 (must be 404)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/registrations # items: [] , totalCredits: 0

# B-2  (stock jar, no profile overrides; 25 threads on the seeded-full course)
python3 - <<'PY'
import json,threading,urllib.request,urllib.error
toks=[json.loads(urllib.request.urlopen(urllib.request.Request(
      "http://localhost:8080/api/auth/login", method="POST",
      data=json.dumps({"studentId":s,"password":"Password123!"}).encode(),
      headers={"Content-Type":"application/json"})).read())["token"] for s in ["32012345","32067890","32011111"]]
course="bbbbbbbb-0000-4000-8000-000000000010"   # FIT3182, seats 0
bar=threading.Barrier(25); out=[]
def one(i):
    bar.wait()
    r=urllib.request.Request("http://localhost:8080/api/registrations", method="POST",
        data=json.dumps({"courseId":course}).encode(),
        headers={"Authorization":"Bearer "+toks[i%3],"Content-Type":"application/json"})
    try: out.append(urllib.request.urlopen(r).status)
    except urllib.error.HTTPError as e: out.append(e.code)
[threading.Thread(target=one,args=(i,)).start() for i in range(25)]
import time; time.sleep(8); print(out.count(500),"x 500,",out.count(409),"x 409")
PY
# then: select count(*) from audit_log where detail='COURSE_FULL';  -- 9, expected 25

# M-1
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/h2-console/       # 200 unauthenticated

# M-2 (two concurrent DELETEs on one registrationId)
# observed: 200 + 500 INTERNAL_ERROR, and one DROP audit row for two attempts
```

---

## 10. Ship verdict

**CHANGES REQUIRED.**

No blocking issue is stylistic: both blockers break a stated acceptance criterion of the frozen contract
(FR3 §1/FR5-B1 and FR6-AC1/FR3-B14), one of them silently (an invisible registration that also bypasses
the 24-credit cap) and one of them loudly (500s and lost audit rows under the exact load the spec says
must be tested). Fixing them is small and local:

1. add the active-semester predicate to the locked course lookup (+ a negative test);
2. stop writing the failure audit from inside the locked business transaction (or size the pool and add a
   production-config regression test) (+ a 25-writer test at the production pool size);
3. then M-1 (console), M-2 (drop lock/stale mapping), M-3 (CHECK constraints), M-4 (C1 decision).

The frontend is in good shape: contract-conformant, accessible in the ways I could measure, faithful to
the design tokens, print stylesheet genuinely working, and inside the NFR5 click budget. The remaining
UI items are polish-level.

---

*Report scope: `docs/reviews/**` only. No production code, tests or specs were modified. No git commands
were run. All spawned processes (3 Spring Boot instances, Vite, headless Chrome) were stopped.*
