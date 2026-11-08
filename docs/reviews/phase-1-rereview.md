# Phase 1 — Adversarial Re-Review (closure + regression hunt)

**Project:** Monash Course Registration System — Phase 1
**Branch:** `feat/phase-1` (working tree as presented; no git commands run)
**Reviewer:** re-reviewer agent
**Date:** 2026-11-08 (Asia/Kuala_Lumpur)
**Inputs:** `docs/reviews/phase-1-review.md`, `docs/qa/phase-1-test-report.md` §12, `docs/qa/triage.md`, and the changed source.
**Scope of writes:** this file only. No production code, test, or spec file was modified. All spawned processes were stopped.

---

## 0. Verdict

# **NO SHIP**

**B-2 is not closed.** The new `AFTER_COMPLETION` audit flow removed the *row-lock* half of B-2 but did **not** remove the *second-connection* half. The shipped jar, on the shipped config (`maximum-pool-size: 20`), still deadlocks the pool under the spec's own 25-writer scenario:

| Live run (packaged jar, default profile, 25 concurrent POSTs to the seeded-full `FIT3182`) | Result |
| --- | --- |
| Run 1 | 22 × 409, **3 × 500**, 30.06 s |
| Run 2 | 21 × 409, **4 × 500**, 30.10 s |
| Run 3 | 21 × 409, **4 × 500**, 30.00 s |

Server log: `HikariPool-1 - Connection is not available, request timed out after 30000ms (total=20, active=20, idle=0, waiting=22)`, 11 × `request.failed ... Could not open JPA EntityManager for transaction`, and 5 × `TransactionSynchronization.afterCompletion threw exception`.

The regression test that was supposed to prove B-2 closed (`RegressionAuditPoolTest#tB2_01`) is green, but it is a **false green**: it runs the success-heavy 10-of-25 mix through MockMvc, which never puts ≥20 threads into the after-completion audit at once. The all-failure burst — the exact shape the spec and the original repro use — is untested and fails on the artifact.

Two further majors are partially open: **M-3** (the CHECK constraints do not implement the frozen schema: `credit_hours IN (3,6)` is missing, `CourseSchedule` has no checks at all, and `capacity > 0` is stricter than the contract), and **M-4**'s seed rewrite silently changes §8.2/A-01 without a recorded decision.

B-1, M-1 and M-2 are genuinely closed — verified in code, in the suite, and live.

---

## 1. Commands run independently (this review)

All from the workspace, clean invocations. Totals confirmed by me, not taken from the QA report.

```bash
cd backend && mvn -B clean test
# [INFO] Tests run: 141, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS   (51.873 s)

cd backend && mvn -B -q -DskipTests package
# target/course-registration-0.1.0.jar  (53,054,984 bytes)

cd frontend && npm run build
# tsc -b && vite build -> 2035 modules transformed, built in 9.44 s, exit 0
# dist/assets/index-Do3UQIhO.js 451.32 kB, index-CtpqDCPE.css 37.91 kB
```

Per-class totals (my run): `AuthControllerTest` 11, `LoginValidationNoBcryptTest` 1, `JwtServiceTest` 3, `CatalogControllerTest` 14, `RegistrationControllerTest` 14, `DropControllerTest` 9, `RegistrationViewTest` 10, `CapacityConcurrencyTest` 9, `RowLockSqlTest` 1, `RegistrationPeriodTest` 9, `StartupValidationTest` 1, `SecurityMatrixTest` 42, `AuditLogTest` 8, `ErrorEnvelopeTest` 1, `RegressionSemesterScopeTest` 2, `RegressionAuditPoolTest` 2, `RegressionConcurrentDropTest` 1, `RegressionSchemaConstraintTest` 1, `RegressionDefaultProfileTest` 2 → **141**.

Backend and frontend are green. Green does not equal correct — see §4.

Runtime evidence used below:

```bash
java -jar target/course-registration-0.1.0.jar                     # default profile, :8080
java -jar target/course-registration-0.1.0.jar \
     --server.port=8081 --spring.datasource.hikari.maximum-pool-size=50   # control run
```

---

## 2. Per-finding closure table

| Finding | Severity | Closure | Evidence (file:line / observed) |
| --- | --- | --- | --- |
| **B-1** registration accepted a non-active-semester course | blocker | **CLOSED** | `CourseRepository.java:37-41` (`@Lock(PESSIMISTIC_WRITE)` + `c.semester = :semester`), `RegistrationService.java:84-96`. Live: POST `cccccccc-…-0001` (FIT1008, 2026-S1) → `404 COURSE_NOT_FOUND`, seats stayed 80, `GET /api/registrations` → `totalCredits:0, items:[]`. Test T-B1-01 asserts 0 rows, seat 80, exactly 1 audit row with `SEMESTER_NOT_ACTIVE`; T-B1-01b asserts 2 attempts → 2 rows. Drop path unaffected. |
| **B-2** failure audit doubles connection demand; 25 writers → 500s + lost rows | blocker | **OPEN** | Fix present but ineffective: `AuditService.java:46-47` publishes, `AuditFailureListener.java:18-24` persists at `AFTER_COMPLETION`, `AuditService.java:50-52` is `REQUIRES_NEW`. Live jar at shipped pool 20: **3/3 runs produced 500s** (3, 4, 4 of 25), 30 s stalls, `total=20, active=20, idle=0, waiting=22`. Only the row-lock contention was removed; the second connection is still requested while the request thread's connection is checked out. Full evidence §3/B-2 and N-1. |
| **M-1** H2 console public + committed JWT default | major | **CLOSED (code)** | `application.yml:29` `enabled: false`; `application-dev.yml:5` opts in; `SecurityConfig.java:49,69-71` permits `/h2-console/**` only under `dev`; `frameOptions.deny()` otherwise (`:57-62`). Live default profile: `GET /h2-console/` → **401**, `/h2-console/login.do` → **401**. `secret: ${JWT_SECRET:}` (`application.yml:34`), no committed key; random 256-bit key generated with a WARN that does not print the key (`JwtService.java:36-40`). README still does not document `JWT_SECRET` (minor, §4 N-6). |
| **M-2** concurrent double drop → 500 + lost audit | major | **CLOSED** | `RegistrationRepository.java:44-50` (`SELECT … FOR UPDATE` on the registration), `RegistrationService.java:139-166`, stale mapping at `GlobalExceptionHandler.java:84-90`. Live: two concurrent DELETEs → **200 + 404 `REGISTRATION_NOT_FOUND`**, seats 30 → 29 → 30 (returned once), no 5xx. T-M2-01 asserts exactly `{200,404}`, 2 audit rows (1 success + 1 failure), C1. |
| **M-3** no DB CHECK constraints | major | **PARTIALLY CLOSED → OPEN** | Seat checks added (`Course.java:50-53`) and effective (T-M3-01). But §7.3 requires `credit_hours IN (3,6)`; the code has only `credit_hours > 0` (`Course.java:39`) — a 4-credit row is accepted. §7.4 requires `day IN (…)` and `end_time > start_time`; `CourseSchedule.java` has **no** `@Check` at all. `capacity > 0` (`Course.java:46`) is stricter than the contract's `capacity >= 0` and forbids the A-01-approved capacity-0 demo row. The new test asserts `capacity = 0` is rejected, i.e. it cements the deviation. |
| **M-4** seeded full course violated C1 | major | **CLOSED (invariant only)** | `DataSeeder.java:87-88` FIT3182 `capacity 1 / seats 0`, `:190-207` one held registration for `32067890`; `seed.completed … registrations=1` observed live. T-M4-01 asserts C1 for all 19 rows and drops the TD-4 carve-out. **But** the seed now contradicts the frozen §8.2 table (`FIT3182 capacity 40`) and A-01 (`capacity = 40, availableSeats = 0`) with no recorded planner decision — new major N-2. |

---

## 3. What I verified for each finding

### B-1 — atomicity and all paths

* The semester predicate is part of the **locked** query (`select c … where c.id = :id and c.semester = :semester`), not a check-then-lock. There is no window between the semester test and the lock.
* The only **request** write path to `registration` is `RegistrationService.register` (`:75-133`); no controller or repository bypass exists. `CsvExportService` and the list endpoint are read-only and semester-scoped. (The seeder writes its one demo registration at startup, `DataSeeder.java:204`, outside any request path.)
* Failure path: on a miss, the service does a **non-locking** existence probe (`:90`) only to choose the audit detail; it never reads or mutates seats, and the caller still gets `404 COURSE_NOT_FOUND`. Live confirmed: seat count unchanged (80), no row, empty list.
* Drop still works (live 200, seat returned once) and is unaffected by the semester change.
* Residual (minor, N-4): the audit detail `SEMESTER_NOT_ACTIVE` is not in the **exhaustive** NFR4.2 matrix, and the `A-15` decision the original review asked for was never recorded in `00 §10`.

### B-2 — read end-to-end, and why it still fails

Flow: `register`/`drop` → `auditFailure` → `AuditService.publishFailure` (`AuditService.java:46-47`) → `ApplicationEventPublisher.publishEvent` → `AuditFailureListener.onFailure` at `TransactionPhase.AFTER_COMPLETION` (`:18-24`) → `AuditService.recordFailure` `REQUIRES_NEW` (`:50-52`).

Correct parts:

* Every failure branch calls `auditFailure` exactly once, including `DataIntegrityViolationException` (`RegistrationService.java:120-124`) and every rollback path. T-B2-02 and T-B1-01b confirm one row per attempt, no duplicates.
* Events are published only from inside `@Transactional` methods, so `fallbackExecution=false` never drops one.
* The event is published *before* the exception propagates, so `AFTER_COMPLETION` fires on rollback (verified).
* A failing audit write does **not** turn a committed 201 into a 500: `TransactionSynchronizationUtils` catches and logs the listener exception (5 occurrences in the live log; responses stayed 409). NFR4-6 is satisfied; NFR4-1 is not, because the row is lost.
* Pool is genuinely 20 in `application.yml:14` and `application-test.yml:10`, and the test asserts the effective `HikariDataSource` value.

Incorrect part — the load-bearing assumption:

`application.yml:22-25` claims *"Release the JDBC connection when the Hibernate transaction completes, before Spring's afterCompletion audit listener asks for a second connection (B-2)."* That claim is false in this stack. Spring invokes `TransactionSynchronization.afterCompletion` **before** `cleanupAfterCompletion` (which is where the `EntityManager` and its JDBC connection are closed), and the live pool stats show the connection is still checked out when the listener asks for one. The listener therefore still needs a **second** connection per failing request. Peak demand is still `2 × concurrent failing writers`; the pool is `20 < 2 × 25`.

Why the suite is green anyway: `tB2_01` runs 10 successes + 15 failures. The 10 successes write their audit inside the business transaction and release early, so the failure wave finds free connections. The **all-failure** burst (0 successes, 25 audits) is the worst case and is not in the suite — T-B2-02 covers it only sequentially.

Control experiment (same jar, same code, only the pool changed):

```text
pool 20, 25 concurrent all-fail:  run 1 -> 3 x 500 (30.06 s)   run 2 -> 4 x 500 (30.10 s)   run 3 -> 4 x 500 (30.00 s)
pool 50, 25 concurrent all-fail:  run 1 -> 0 x 500 (0.28 s)    run 2 -> 0 x 500 (0.15 s)
pool 50, 40 concurrent all-fail:  40 x 409, 0 x 500 (0.30 s)
```

That isolates the cause to pool capacity, not seat logic, and proves the audit still consumes a second connection.

Audit loss quantification (3 runs, 75 attempts): 11 requests failed before a transaction existed (`Could not open JPA EntityManager`) → no audit row; 5 listener invocations failed at `afterCompletion` → audit row lost. **≥16 of 75 attempts (21 %) did not produce the required row.** FR6-AC1 is also violated: 11 losers got 500 instead of 409 `COURSE_FULL`.

### M-1 — console, secret, profiles

* Default profile: console disabled (`enabled: false`) **and** not permitted by the chain; live 401 on `/h2-console/` and `/login.do`.
* Dev profile only: `application-dev.yml` enables it and `SecurityConfig` matches `"dev"`; frame options are `sameOrigin` only there, `DENY` elsewhere.
* No committed secret in `application.yml`; a blank secret yields an ephemeral key with a warning that omits the key. Restart invalidates tokens — acceptable for an in-memory single-instance Phase 1, and the warning says so; the README still does not document `JWT_SECRET` (minor).
* No actuator dependency in `pom.xml`, no management endpoints exposed. `"/error"` is the only extra permit.
* Observation: Spring Boot's `UserDetailsServiceAutoConfiguration` still prints "Using generated security password". It is unusable (form login and basic auth are disabled) but the noise should be removed in a later pass.

### M-2 — drop lock and deadlock freedom

* The registration row is loaded with `PESSIMISTIC_WRITE` **before** existence/owner/period checks, so read-check-write is serialised. The loser blocks, then observes no row and returns 404 with an audit row.
* Lock order: drop = registration → course; register = course (only). There is no path that takes course → registration, so no cycle. Drop-vs-drop on different rows of the same course both take their own registration first and then queue on the course; no cycle.
* Live 200 + 404 with one seat returned; T-M2-01 asserts 2 audit rows and C1.
* Residual (minor, N-5): the global `ObjectOptimisticLockingFailureException → 404 REGISTRATION_NOT_FOUND` mapping also applies to register; harmless today (no `@Version` anywhere) but semantically wrong if optimistic locking is introduced.

### M-3 / M-4 — see the table and §4.

### Frontend

* `Catalog.tsx:46-49,77-82,93-96,115-134`: the active term is authoritative from the period endpoint; non-active options are rendered `disabled`; a deep link with another semester is rewritten to the active one; `updateSemester` ignores non-active values.
* `CourseDetail.tsx:114-131,265-295`: a prior-semester course renders `Not open for registration` (disabled) with "Not offered in the current registration period. Registration is open for <active> only."
* `lib/registrationState.ts:43-50` blocks a different term **before** the full/period/credit branches, so the reason message is the semester one.
* Frontend build green (2035 modules, exit 0).
* Note: the API still honours `GET /api/courses?semester=2026-S1` (FR2-B2/T-FR2-13), but the SPA rewrites such a URL. The task brief expects "only active term selectable", so I score this PASS; it does weaken FR2-B12's "shareable URL" for non-active terms and should be recorded as a UI decision.

---

## 4. New findings

### N-1 — BLOCKER — B-2 is still live on the shipped artifact (see §3/B-2)

* **Where:** `application.yml:14` (`maximum-pool-size: 20`), `:22-25` (false `release_mode` comment), `AuditFailureListener.java:18-24`, `AuditService.java:50-52`.
* **Reproduced:** 3/3 runs of the spec's 25-writer burst against the packaged jar: 3–4 × 500 each, 30 s stalls, `active=20, waiting=22`.
* **Impact:** FR6-AC1 (losers must be 409), NFR4-1 (one row per attempt), NFR3 (the whole API becomes unavailable while the pool is saturated — reads included).
* **Minimal exact fix (choose one, then test the all-fail shape):**
  1. **Preferred:** remove the second-connection requirement — write failure audits through a dedicated audit `DataSource`/connection or a single out-of-band writer, not a request-thread `REQUIRES_NEW` transaction on the shared pool.
  2. **Acceptable interim:** set `spring.datasource.hikari.maximum-pool-size: 50` in **both** `application.yml` and `application-test.yml` (validated: 25 × 0 500s, 40 × 0 500s) and correct the misleading comment. This is the original review's own `≥ 2 × max writers` rule; 20 was never enough.
  3. Add a regression test that runs **25 threads all failing** (0 successes, e.g. full course) at the shipped pool against the packaged config — the current 10/15 mix cannot see this.

### N-2 — MAJOR — M-3's constraints do not match the frozen schema; the seed rewrite is an unrecorded contract change

* `Course.java:39` — `credit_hours > 0` instead of `credit_hours IN (3,6)` (§7.3). A 4- or 5-credit row is accepted; the invariant has no backstop.
* `CourseSchedule.java` — no `@Check` for `day_code IN ('MON',…,'SUN')` or `end_time > start_time` (§7.4). The original M-3 explicitly named this file.
* `Course.java:46` — `capacity > 0` instead of `capacity >= 0` (§7.3) and contradicts A-01 ("capacity 0 is legal but reserved"). `RegressionSchemaConstraintTest` asserts `capacity = 0` is **rejected**, so the suite now enforces the deviation.
* `DataSeeder.java:87-88` + `DataSeeder.java:190-207` — FIT3182 is now capacity **1** with one seeded registration, while §8.2 still says capacity **40**, seats 0, and A-01 says "capacity = 40". The invariant now holds, but the seed no longer matches the spec and nobody recorded the decision the original review demanded ("needs a planner decision, not a coder guess"). Daniel also gains a registration in his initial schedule (6 credits), which changes demo data.
* **Fix:** either amend `00 §7.3/§8.2/A-01` to the new shape (and add `credit_hours IN (3,6)`, `capacity >= 0`, `CourseSchedule` checks), or restore `capacity 40` and seed the 40 holds as rows. Add a student-credit-total assertion (currently none; the values are 6/0/0, so all legal).

### N-3 — MAJOR (test quality) — `RegressionAuditPoolTest#tB2_01` is a false green

It asserts real DB state and exact counts (not vacuous), but its scenario cannot reproduce the shipped failure: 10 successes release connections before the 15 failures need their audits. The all-failure 25-writer case — the original repro and the explicit acceptance case "(or 0/25 for the full course)" — is absent. A test that passes only for the artifact's *favourable* request mix is exactly the trap this review was asked to hunt.

### N-4 — MINOR — `SEMESTER_NOT_ACTIVE` is outside the exhaustive audit matrix

`NFR4.2` is declared exhaustive (E-01…E-10); E-03's detail is `COURSE_NOT_FOUND`. The fix emits `SEMESTER_NOT_ACTIVE` and never records the `A-15` decision. HTTP behaviour is correct; the audit vocabulary now has an undocumented value. Either add it to the matrix (and an ambiguity entry) or emit `COURSE_NOT_FOUND`.

### N-5 — MINOR — over-broad global exception mappings

`GlobalExceptionHandler.java:78-90` maps *any* `DataIntegrityViolationException` to `409 ALREADY_REGISTERED` and *any* `ObjectOptimisticLockingFailureException` to `404 REGISTRATION_NOT_FOUND`, on every endpoint. With no `@Version` today these are unreachable, but a future CHECK violation during register would be reported as "already registered", and a future optimistic lock during register as "registration not found". Scope them to the drop/register paths or carry an operation discriminator.

### N-6 — MINOR — JWT documentation/restart semantics

README never mentions `JWT_SECRET` (the original m-1 asked for it). The ephemeral key is safe (not logged) and correct for a single in-memory instance, but a restart silently invalidates every session, and a multi-instance deployment would reject each other's tokens.

### N-7 — MINOR — audit gap for malformed attempts

`POST /api/registrations` with a non-UUID `courseId` throws `ValidationException` in the controller (`RegistrationController.java:63,97-103`) before the service, so no audit row is written. NFR4-1 says "every register and drop attempt". The E-matrix does not list it, so this is a spec ambiguity, not a regression — but it should be decided rather than left implicit.

### N-8 — OBSERVATIONS (pre-existing, unchanged)

* `AuditLog` has no DB `CHECK` on `action`/`outcome` (§7.6).
* CORS still allows `http://127.0.0.1:5173` (m-2).
* JWT parser still accepts HS384/HS512 signed with the same key (m-11).
* The catalogue's semester control is now display-only; FR2-B12 shareability for non-active terms is weakened (see §3/Frontend).
* Spring Boot's generated `user` password is logged at startup.

---

## 5. Test-quality check

**Integrity:** 132 → 141, no test deleted or skipped. I re-read the three tests whose fixtures changed (`CapacityConcurrencyTest#tFr6_07`, `RegistrationControllerTest#tFr3_10`, `AuditLogTest#tNfr4_04`): all became **stricter** (`countByCourseId` instead of `count`, C1 asserted where the TD-4 carve-out used to skip it). No weakening found.

**Spot-check of three new regression tests:**

| Test | Asserts DB state? | Exact counts? | Non-5xx / status? | Verdict |
| --- | --- | --- | --- | --- |
| T-B1-01 (`RegressionSemesterScopeTest`) | yes — seat 80, 0 rows, `existsBy` false | yes — exactly 1 audit row, exact action/outcome/detail/courseCode | yes — 404 + `COURSE_NOT_FOUND` | **Strong, non-vacuous** |
| T-B2-01 (`RegressionAuditPoolTest`) | yes — seats 0, 10 rows, C1, 25 audit rows, 25 distinct students, exact composition | yes | yes — zero statuses other than 201/409 | **Strong assertions, wrong scenario** (false green, N-3) |
| T-M2-01 (`RegressionConcurrentDropTest`) | yes — seats 3→4, 1 row, C1 | yes — exactly 2 audit rows with composition | yes — exactly `{200,404}`, no 5xx | **Strong, non-vacuous** |

**Additional test gaps:** no student credit-total seed assertion; no malformed-attempt audit assertion; `RowLockSqlTest` still asserts on JVM-wide SQL output (pre-existing weakness); T-M3-01 asserts the non-spec `capacity = 0` rejection and never tests `credit_hours = 4`.

---

## 6. Ship verdict and the minimal path to SHIP

**NO SHIP.**

Blocking:

1. **Fix B-2 for real.** Either give the failure audit its own connection path, or raise the pool to 50 in `application.yml` + `application-test.yml` (validated). Add an **all-failure** 25-writer regression at the shipped pool. Acceptance (unchanged from the original review): packaged jar, default config, 25 concurrent failures → 0 × 5xx, 25/25 audit rows, no 30 s stalls.

Non-blocking but must be resolved before sign-off (majors):

2. **M-3/N-2:** `credit_hours IN (3,6)`, `capacity >= 0` (or a recorded spec change to `> 0`), and `@Check` on `CourseSchedule` (`day_code IN (…)`, `end_time > start_time`).
3. **M-4/N-2:** record the seed decision — amend §8.2/A-01 to capacity 1 + one held registration, or restore capacity 40 with 40 seeded holds. Add a per-student credit-total assertion.

Then the previously closed items (B-1, M-1, M-2) stay closed and the frontend remains green. Nothing else in this re-review blocks.

---

## 7. Repro pack

```bash
# Build + run the shipped artifact
cd backend && mvn -B -q -DskipTests package
java -jar target/course-registration-0.1.0.jar        # default profile, pool 20

# B-2: 25 concurrent failing registrations on the seeded-full course
python3 - <<'PY'
import json,threading,urllib.request,urllib.error,time
from collections import Counter
B="http://localhost:8080"
toks=[json.loads(urllib.request.urlopen(urllib.request.Request(B+"/api/auth/login", method="POST",
      data=json.dumps({"studentId":s,"password":"Password123!"}).encode(),
      headers={"Content-Type":"application/json"})).read())["token"] for s in ["32012345","32067890","32011111"]]
course="bbbbbbbb-0000-4000-8000-000000000010"   # FIT3182, capacity 1, seats 0
bar=threading.Barrier(25); out=[]; lk=threading.Lock(); t0=time.time()
def one(i):
    bar.wait()
    r=urllib.request.Request(B+"/api/registrations", method="POST",
        data=json.dumps({"courseId":course}).encode(),
        headers={"Authorization":"Bearer "+toks[i%3],"Content-Type":"application/json"})
    try: resp=urllib.request.urlopen(r); code=resp.status
    except urllib.error.HTTPError as e: code=e.code
    except Exception: code=-1
    with lk: out.append(code)
ts=[threading.Thread(target=one,args=(i,)) for i in range(25)]
[t.start() for t in ts]; [t.join() for t in ts]
print("elapsed=%.1fs"%(time.time()-t0), Counter(out))
PY
# observed (x3): 3-4 x 500, 30 s; log: total=20, active=20, waiting=22
# control: same jar with --spring.datasource.hikari.maximum-pool-size=50 -> 0 x 500 in 0.28 s

# B-1 live
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"studentId":"32012345","password":"Password123!"}' | jq -r .token)
curl -s -w '\n%{http_code}\n' -X POST localhost:8080/api/registrations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"courseId":"cccccccc-0000-4000-8000-000000000001"}'      # 404 COURSE_NOT_FOUND
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/courses?semester=2026-S1' | jq '.[0].availableSeats'  # 80

# M-1 live
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/h2-console/   # 401
```

---

*Report scope: `docs/reviews/phase-1-rereview.md` only. No git command was run. No production code, test, spec or QA file was modified. Both Spring Boot instances (8080, 8081) were stopped after measurement.*
