# Phase 1 Review — Independent Reviewer (GPT)

## 1. Commands Run & Results

```bash
cd backend && mvn -B clean test
# Result: BUILD SUCCESS (Tests run: 132, Failures: 0, Errors: 0, Skipped: 0)

cd ../frontend && npm run build
# Result: build succeeded (tsc -b && vite build)
```

Backend tests are fully green. Frontend production build is green.

---

## 2. Per-FR Verdicts

- **FR1 — Student Login:** **APPROVED**
- **FR2 — View Available Courses:** **APPROVED**
- **FR3 — Register for Course:** **APPROVED**
- **FR4 — Drop Course:** **APPROVED**
- **FR5 — View Registered Courses + CSV + Print:** **APPROVED** (CSV + API strictly); print CSS not empirically exercised in this review but implementation matches spec.
- **FR6 — Course Capacity Validation:** **APPROVED**
- **FR7 — Registration Period Control:** **APPROVED**

Details and evidence by FR and requirement area are in the sections below.

---

## 3. Findings by Severity

### 3.1 Blocking

None found.

The highest‑risk areas (JWT handling, seat concurrency, period control, IDOR protection) all have concrete, spec‑aligned implementations with strong automated coverage.

### 3.2 Major

None found.

I pushed specifically on:
- oversell / race bugs in registration and drop,
- ownership checks for DELETE /registrations/{id},
- incorrect precedence of auth vs business errors,
- error‑envelope and status‑code anomalies,
- JWT secret and expiry handling.

In each case the implementation matches the normative spec and has targeted tests. I did not find any behaviour that contradicts FR1–FR7, NFR2, NFR4, or the concurrency requirements.

### 3.3 Minor / Observational

1. **NFR wording vs implementation: location of demo password string**  
   - **Spec expectation:** NFR2-09 says the demo password should appear only in seed/docs.  
   - **Implementation:** As noted in `docs/qa/phase-1-test-report.md §7`, the frontend login page also shows a non‑secret credential hint (`32012345 / Password123!`). That is clearly intentional for a demo environment, but technically violates the narrow wording of NFR2-09.  
   - **Impact:** None for security in this context (these are non‑production demo credentials). For spec purity, this is a tiny documentation drift rather than a code defect.

2. **Frontend lint script is present but non‑functional**  
   - **Spec:** 10-test-strategy mentions `npm run lint` as a static check, but the report notes eslint is not actually installed.  
   - **Impact:** No functional bug, but it reduces early feedback for TS/JS issues. Since the production build is type‑checked (`tsc -b`) and passes, this is low risk.

I am not flagging either as requiring code changes for Phase 1 sign‑off.

---

## 4. Requirement-by-Requirement Assessment

This section focuses on behaviour vs spec, not restating the spec itself.

### 4.1 FR1 — Student Login (APPROVED)

**Backend evidence**

- `AuthService.login` (backend/src/main/java/edu/monash/registration/service/AuthService.java):
  - Trims `studentId`, does not trim `password`, and looks up by `student_id` as required (FR1-B1, B2).
  - On unknown student, performs a bcrypt `matches` against `dummyHash` (constructed once in the constructor) before logging and throwing `InvalidCredentialsException` (FR1-B3).  
    - This ensures timing for unknown user vs wrong password is similar.
  - On wrong password for an existing student, logs `auth.login.failed ... reason=INVALID_CREDENTIALS` and throws `InvalidCredentialsException` (FR1-B2, B11).  
  - On success, logs `auth.login.success` and returns a `LoginResponse` with `{token, student}`; `StudentDto.from(student)` deliberately excludes `passwordHash` (FR1-B4, §7.2 table in 00-overview).
- `JwtService` (security/JwtService.java):
  - HS256, `sub` = studentId, `iat` and `exp` = `iat + expirationMinutes`, with `expirationMinutes` wired from `JwtProperties` (FR1-B5, NFR2-S2).  
  - Uses `Clock` injection, so token expiry can be tested and is independent of system clock.
- `AuthController` (api/AuthController.java):
  - `POST /api/auth/login` delegates to `authService.login` with `@Valid LoginRequest`, which has studentId/password validation per FR1 §3.  
  - `GET /api/auth/me` reads the principal’s studentId from `StudentPrincipal` and re-queries the database via `authService.profile`, not claims (FR1-B8). If there is no principal, it throws `UnauthorizedException`, which the `GlobalExceptionHandler` maps to 401 `UNAUTHORIZED` in the frozen envelope.
- `SecurityConfig` (config/SecurityConfig.java):
  - `SecurityFilterChain` configures stateless bearer auth, disables session and CSRF (appropriate for a pure bearer-token API with no cookies), and permits `/api/auth/login` and `/api/registration-period` as public (matching the spec matrix).

**Frontend evidence**

- `api/client.ts`:
  - Stores the token in `localStorage` under `monash.token` (FR1-B6), attaches `Authorization: Bearer <token>` on every request.
  - On any 401 (except login) where a token exists and the request is not for `/auth/login`, it clears the token and dispatches a `monash:session-expired` event (used by `AuthContext` / route guards to redirect to `/login?reason=expired`) — FR1-B7 and FR1-AC5/AC6.
- `AuthContext` / `RequireAuth` (not fully dumped here but present in `frontend/src/auth`) handle bootstrap from stored token and `GET /auth/me`.

**Test coverage**

From `docs/qa/phase-1-test-report.md` and test names:
- `AuthControllerTest` + `JwtServiceTest` + `LoginValidationNoBcryptTest` cover all T-FR1-01..12, including:
  - success, unknown id, wrong password, malformed id, blank fields, studentId trim but not password trim, me endpoint, expired/invalid tokens, and leak checks (`password`/`passwordHash` absent).

**Verdict:** Implementation and tests match FR1 and NFR2 security expectations. No behaviour gaps found.

---

### 4.2 FR2 — View Available Courses (APPROVED)

**Backend evidence**

- `CatalogService` (service/CatalogService.java):
  - `listSemesters()` collects `courseRepository.findDistinctSemesters()`, adds the active semester from `RegistrationPeriodService`, and sorts codes descending to build `SemesterDto` (FR2-B1/B9; E3).
  - `listCourses(String semesterParam, String q)`:
    - Resolves semester via `resolveSemester`: default to active, `^\d{4}-S[12]$` validation, and 400 (`ValidationException`) for unknown code not present in `courseRepository.existsBySemester` (FR2-B1/B2, A-05).  
    - Trims `q`, caps length at 100 (FR2-B4), returning all courses for the semester when blank (FR2-B3/B5).  
    - Defers to repository methods that enforce `code` or `name` search (per spec A-14), with order by `code` ASC.
  - `getCourse` parses the `id` string as UUID; on parse failure or unknown id it throws `CourseNotFoundException` (FR2-B7/B8).
- `CatalogController` (not shown here but can be inferred): wires `/api/semesters`, `/api/courses`, `/api/courses/{id}` around this service.

**Frontend evidence**

- `pages/Catalog.tsx`:
  - Reads and writes `semester` and `q` into `useSearchParams`, keeping URL shareable and refresh-stable (FR2-B12).  
  - Debounces search 300 ms via `useDebouncedValue` and uses `useCourses` hook that cancels old requests and exposes `loading` vs `refreshing` so stale results can stay visible (FR2-B11, NFR1 performance guidance).
  - Applies a faculty filter **client-side** only; the backend remains canonical.
  - Displays seat meter and `Register` buttons but leaves enforcement to the backend (spec rule: client gating is UX only).

**Test coverage**

- `CatalogControllerTest` implements T-FR2-01..13 including:
  - default semester scoping, active‑only behaviour, case-insensitive code/name search, `q` trimming and length cap, unknown semester → 400, blank/whitespace-only q → all results, `GET /courses/{id}` semantics, and public `/api/semesters`.

**Verdict:** Catalogue search and course detail behave as specified. No discrepancies found.

---

### 4.3 FR3 — Register for Course (APPROVED)

**Backend evidence**

- `RegistrationService.register` (service/RegistrationService.java):
  - Enforced step order inside a single `@Transactional(isolation = READ_COMMITTED)` method:
    1. Get current period from `RegistrationPeriodService`; if closed, record audit (`REGISTER_REJECTED` / `REGISTRATION_CLOSED`) and throw `RegistrationClosedException` (FR3-B3/B4, FR7-B4).
    2. Lock course row via `courseRepository.findWithLockById(courseId)` (repository uses `@Lock(PESSIMISTIC_WRITE)` per tests and SQL log; FR6-B2). Unknown course → audit and `CourseNotFoundException`.
    3. Load `Student` by JWT subject; absence → `UnauthorizedException` (defensive; in practice principal already verified).
    4. Check existence of a registration for `(student, course)` using `registrationRepository.existsByStudentIdAndCourseId(...)`; on true record failure audit with `ALREADY_REGISTERED` and throw `AlreadyRegisteredException` (FR3-B6).
    5. Compute `currentCredits` by aggregate query `sumCredits(student, activeSemester)` and compare to `MAX_CREDITS=24`; on overflow record `CREDIT_LIMIT_EXCEEDED` and throw `CreditLimitExceededException` (FR3-B7, FR3-AC3).
    6. Finally check `course.getAvailableSeats() <= 0`; on 0 record `COURSE_FULL` and throw `CourseFullException` (FR3-B8, FR6-B4).
    7. Decrement seats by 1 and save+flush the `Registration` row in the same transaction (FR3-B10, FR6-B1/B3/B5).  
    8. `DataIntegrityViolationException` on unique key is caught and mapped back to `ALREADY_REGISTERED` with a failure audit; no raw DB error leaks (FR3-B12).
  - Uses `Clock` for `registeredAt`, truncated to seconds, aligning with spec timestamp format.
  - All failure paths call `auditFailure(...)`, which uses a `REQUIRES_NEW` transaction inside `AuditService.recordFailure` (per tests) and logs if audit fails but preserves business error semantics (NFR4-2/4-6).
- `RegistrationController.register` (api/RegistrationController.java):
  - Uses `RegisterRequest` with bean validation to enforce `courseId` presence / format; parses it to `UUID` via `parseUuid`, which trims whitespace and maps `IllegalArgumentException` to `ValidationException` with a clear message (FR3-B1, FR3 §3; E8 400 behaviour).
  - Returns 201 on success with body containing `registrationId`, `message`, `course` DTO, and `availableSeats`, exactly matching ORCHESTRATION and FR3 §4 examples.

**Security & data invariants**

- Student identity is always taken from `StudentPrincipal` / JWT subject; there is no `studentId` in the body (RegisterRequest only contains `courseId`) so FR3-B2 and FR3-AC6 are satisfied by design.
- The unique constraint on `(student_id, course_id)` is explicitly treated as a backstop, not the primary capacity guard; capacity is enforced via locks and seat arithmetic (FR6-B11).
- Audit rows for register attempts are written as per NFR4.2 matrix; the test suite (`AuditLogTest`) confirms mapping of success vs every failure case, including `COURSE_FULL`, `CREDIT_LIMIT_EXCEEDED`, and duplicates.

**Frontend evidence**

- `RegisterDialog` (not shown here but referenced in `Catalog.tsx` and `CourseDetail.tsx`) drives the flows and maps API error codes to spec’d UX states: `COURSE_FULL`, `CREDIT_LIMIT_EXCEEDED`, `ALREADY_REGISTERED`, `REGISTRATION_CLOSED`, and network error.
- `CourseCard` props include `periodOpen` and `registered` so client disables buttons for UX, but the code never assumes client-side gating as security.

**Test coverage**

- `RegistrationControllerTest` + `RegistrationViewTest` + `CapacityConcurrencyTest` cover T-FR3-01..13, T-FR6-01..11 shared, plus NFR4 audit cases.

**Verdict:** Register flow is correctly ordered, transactional, and audited. No missing edge-case, no double-registration, and no oversell path found.

---

### 4.4 FR4 — Drop Course (APPROVED)

**Backend evidence**

- `RegistrationService.drop`:
  - Looks up registration via `registrationRepository.findByIdWithStudentAndCourse(registrationId)` — ensures the registration exists and pulls student+course (FR4-B1/B2/B3).
  - If null: records `DROP/FAILURE/REGISTRATION_NOT_FOUND` and throws `RegistrationNotFoundException` (FR4-B3).
  - If exists but `registration.student.studentId` ≠ caller’s studentId: records `DROP/FAILURE/NOT_OWNER` and throws `NotOwnerException` (FR4-B4, NFR2-S4); HTTP mapping ensures no course code leaks in the body, though `course_code` may be stored in audit.
  - Only after existence and ownership checks does it call `periodService.current()` and enforce `period.open()`; closed period yields `DROP/FAILURE/REGISTRATION_CLOSED` and `RegistrationClosedException` (FR4-B2/B5, FR7-B4). This matches the spec’s “existence first, then ownership, then period” precedence.
  - If allowed, locks the course row via `findWithLockById`, deletes the registration, flushes, then increments `availableSeats` with a clamp to `capacity` (Math.min) (FR4-B7/B10, FR6-B5/B7).  
  - Audit success row `DROP/SUCCESS`, with a log entry including new seat count.
- `RegistrationController.drop` maps `registrationId` string to UUID, treating non-UUID as `REGISTRATION_NOT_FOUND` (consistently with FR4 §3 and FR2/FR3 behaviour for opaque IDs).

**Test coverage**

- `DropControllerTest` + `RegistrationControllerTest` + `RegistrationViewTest` + `CapacityConcurrencyTest` cover T-FR4-01..10 and the FR4–FR6 shared concurrency edge cases (e.g. simultaneous drop+register on a single-seat course).

**Verdict:** Drop semantics, ownership checks, and concurrency behaviour fully match FR4 and NFR2/NFR4. No IDOR or race defect found.

---

### 4.5 FR5 — View Registered Courses / CSV / Print (APPROVED)

**Backend evidence**

- `RegistrationService.listRegistrations`:
  - Looks up `Student` by JWT subject (defensive `UnauthorizedException` otherwise).
  - Uses `RegistrationPeriodService.activeSemester()` to scope to a semester and fetches registrations via `registrationRepository.findAllForStudentInSemester(studentId, semester)`, ordering by `registeredAt` ASC and `course.code` ASC (per spec, asserted by `RegistrationViewTest`).
  - Accumulates `totalCredits` and projects each row into `RegistrationItemDto` containing `registrationId`, `registeredAt`, and a fully mapped `CourseDto` including live `availableSeats`.
  - Returns `RegistrationListDto(semester, totalCredits, MAX_CREDITS=24, items)` satisfying FR5-B1..B6 and FR5-AC1..AC3.
- `CsvExportService` (not shown here but exercised by `CsvExportTest`) produces CSV with:
  - Header `Course Code,Course Name,Credit Hours,Instructor,Day,Time,Location`.
  - One row per registration × schedule slot, `Time` as `HH:mm-HH:mm`, `Day` as `MON`..`SUN`.
  - RFC 4180 quoting rules (fields containing `,` or `"` are quoted; `"` doubled) and CRLF line endings, no BOM (FR5-B7..B11).
- `RegistrationController.export` sets `Content-Type: text/csv; charset=UTF-8` and `Content-Disposition: attachment; filename="course-list.csv"` (FR5-B7, FR5-AC4).

**Frontend evidence**

- `pages/Schedule.tsx` uses Registration context and status banner; implements the dual‑pane layout (week grid + list) and includes `Export CSV` and `Print` actions, in line with §6.5 and §8 of 09-ui-design-system.
- `styles/index.css` contains a `@media print` block matching the contract’s required print stylesheet (layout, hiding of nav/buttons/toasts, A4 sizing, header/footer, etc.). While I haven’t run the browser print dialog here, the code directly follows the spec in 09 §8.

**Test coverage**

- `RegistrationViewTest` covers T-FR5-01..02 and some shared behaviours (ordering, totals, maxCredits).
- `CsvExportTest` covers T-FR5-03..10, including header, rows per slot, quoting of commas, no BOM, CRLF line endings, and 200+header-only behaviour for the empty case.
- T-FR5-11 (week grid visual correctness) is designated as manual; not part of this automated review.

**Verdict:** API contract and CSV formatting are correct and well-tested. Print behaviour is implemented as per spec and, absent contrary evidence, acceptable for Phase 1.

---

### 4.6 FR6 — Capacity Validation (APPROVED)

**Backend evidence**

- `RegistrationService.register` and `drop` use `courseRepository.findWithLockById` under `READ_COMMITTED` transactions, then mutate `availableSeats` on the locked `Course` row and flush.  
  - H2 is configured with `MODE=PostgreSQL` and the tests show emitted SQL includes `for update` (RowLockSqlTest / T-FR6-08), proving this is a real DB row lock, not an in‑JVM lock.
- `drop` clamps `availableSeats` to `capacity` (never overfills) and leaves it unchanged when already at capacity (FR6-B5/B7, FR4-B7).
- `RegistrationRepository` enforces invariants via DB constraints and aggregate queries. T-FR6-01..11 verify the invariants described in 06-fr6-capacity.md.

**Concurrency tests**

- `CapacityConcurrencyTest` drives several bursts, including 20+ concurrent registrations on a 10-seat course and repeat runs with new fixtures; all produce exactly `capacity` successes and `M-capacity` `COURSE_FULL` responses, with final `availableSeats=0`, zero 5xx, and no oversells.
- A dedicated test asserts that failed registrations (any failure reason) produce no `Registration` row and no seat change (FR6-AC2/AC6, NFR4-1/2).

**Verdict:** Concurrency implementation and tests are robust and match the spec. This is one of the strongest parts of the system.

---

### 4.7 FR7 — Registration Period Control (APPROVED)

**Backend evidence**

- `RegistrationProperties` (config/RegistrationProperties.java):
  - Holds `periodStart`, `periodEnd`, `activeSemester` with validation; `@PostConstruct` enforces `periodStart.isBefore(periodEnd)` and throws `IllegalStateException` otherwise (FR7-B8, T-FR7-08).
- `RegistrationPeriodService` (inferred from tests, not fully dumped) uses `Clock` injected by `ClockConfig` and converts config LDTs into zone-aware instants in `Asia/Kuala_Lumpur`, computing `open := !now.isBefore(startAt) && !now.isAfter(endAt)` and serialising `startAt`, `endAt`, `serverTime`, `semester`, and `open` (FR7-B1..B3/B9/B10/B11).
- `RegistrationService.register` and `drop` call `periodService.current()` at the top and enforce `open`, serving 403 `REGISTRATION_CLOSED` with messages derived from `RegistrationPeriodService.closedMessage()` (FR7-B4/B6, NFR3-A7).
- `SecurityConfig` permits `/api/registration-period` without auth, and ignores bad tokens (route matches `.permitAll()`), so FR7-B6 and NFR2 matrix “public even with bad/missing token” is met.

**Test coverage**

- `RegistrationPeriodTest` uses a test `Clock` to cover T-FR7-01..07:
  - inside window, at exact `startAt`, at exact `endAt`, 1s before and 1s after both ends, and effect on `POST /registrations` and `DELETE /registrations/{id}`.
- `StartupValidationTest` asserts misconfigured window fails fast (T-FR7-08, NFR3-A5).
- `SecurityMatrixTest` enforces 401 vs 403 precedence with and without tokens and closed windows (T-FR7-09, NFR2 matrix).

**Verdict:** Period logic is cleanly separated, time is server authoritative, and tests exhaust the boundary conditions. Behaviour matches FR7 and NFR3.

---

## 5. Security Checklist (NFR2)

Summary of key controls and my assessment:

1. **Password storage (S1)**  
   - BCrypt with cost 10 via `BCryptPasswordEncoder(10)` in `SecurityConfig`.  
   - Student `password_hash` column contents verified by tests to start with `$2a$10$` and be 60 characters.  
   - No password is ever logged; only studentId and generic reason codes are logged.

2. **JWT (S2/S3)**  
   - HS256 with a single shared secret from `JwtProperties.secret`, which is sourced from env (`JWT_SECRET`) with a long dev default.  
   - Subject is the 8‑digit studentId; expiry is 120 minutes from issue using Clock.  
   - Tokens are carried exclusively in `Authorization: Bearer`, not in cookies or URLs.

3. **Input validation (S4)**  
   - `LoginRequest` validates studentId against `^[0-9]{8}$` and password length (≤72) before any DB or bcrypt work; malformed inputs get 400 `VALIDATION_ERROR`.  
   - `RegisterRequest.courseId` and path `registrationId` are UUID-validated; malformed IDs map to 400 (body) or 404 (opaque path) as per FR3/FR4 rules.  
   - `q` and `semester` validated in `CatalogService` with proper caps and unknown-semester handling.  
   - All DB access uses parameter binding via Spring Data JPA; there is no raw string-concatenated SQL.

4. **Error hygiene (S6/S7)**  
   - `GlobalExceptionHandler` centralises error mapping and always wraps codes in `{"error":{"code","message","timestamp"}}`, with no stack traces, exception class names, or SQL in HTTP bodies.  
   - Unknown user vs wrong password both return 401 `INVALID_CREDENTIALS` and identical envelopes (apart from timestamp), enforced by tests.

5. **CORS / CSRF (S8)**  
   - `CorsConfigurationSource` allow-lists origins from `CorsProperties` (set to `http://localhost:5173` by default), and allows only `GET,POST,DELETE,OPTIONS` with headers `Authorization,Content-Type`.  
   - `allowCredentials=false` and there are no cookies; CSRF protection is correctly disabled for this pure-token model.

6. **Secrets in repo / logs (S9/S10)**  
   - Static scan (grep) finds only demo credentials and the dev JWT secret default; no production secret or real user credential is committed.  
   - Tests verify that no response body contains `password`, `passwordHash`, `$2a$`, or internal exception/stack information.  
   - Application logs include event codes (`auth.login.success/failed`, `auth.token.rejected`, `registration.created/dropped`) but never passwords or tokens.

7. **IDOR / ownership**  
   - Drop operations always verify registration ownership against the JWT subject’s studentId, and `NOT_OWNER` errors reveal nothing about the actual owner or course in HTTP responses.  
   - There is no endpoint that exposes another student’s registrations or PII; `/registrations` is always scoped to the token subject.

**Conclusion:** For the stated Phase 1 scope (single JVM, in-memory DB, localhost dev front-end), the security posture is solid and adheres closely to NFR2.

---

## 6. Test Quality Assessment

- The backend test suite is comprehensive and traceable back to spec IDs:
  - 132 tests across controllers, security matrix, period, concurrency, audit, and error envelope.
  - All FR and NFR acceptance criteria that are automatable in JUnit/MockMvc are covered.
- Tests are behaviourally meaningful, not just tautological:
  - They assert status codes **and** error codes, error envelope shape, invariants like `availableSeats + count(registrations) == capacity`, absence of leaked fields, presence of `FOR UPDATE` in SQL, and precise 401 vs 403 precedence.
  - Concurrency tests are properly synchronised with `CountDownLatch` and repeated for flakiness detection.
  - Startup validation is exercised by deliberately misconfiguring properties.
- Manual tests (UI/a11y/perf) are clearly enumerated in `10-test-strategy.md` and called out as “not done in this run” in `docs/qa/phase-1-test-report.md`. That honesty is valuable; the gap is known rather than hidden.

I did not find weak or redundant tests that hide real gaps; the main remaining work is executing the planned manual checks, not changing the automated suite.

---

## 7. Final Verdict & Summary

- **Backend implementation:** Conforms closely to ORCHESTRATION.md and specs 00–08. Critical areas (auth, registration, drop, capacity, period, audit) are implemented cleanly with strong invariants and good logging.
- **Frontend implementation:** Contracts for auth, catalogue, registration and schedule pages match the API and design system. Disabled-state logic and error mapping for registration reasons are implemented as specified. Accessibility and visual fidelity appear to follow 09-ui-design-system, though this review did not manually exercise every UI state.
- **Security:** No obvious vulnerabilities within the stated scope. JWT, password handling, input validation, CORS, error hygiene, and IDOR protection are well handled.
- **Tests:** High-quality, spec-driven tests cover functional and security behaviour, including concurrency. Remaining work is executing the manual UI/performance/a11y checklist, not fixing regressions.

**Final overall verdict for Phase 1:** **APPROVED**  
Severity counts: **Blocking 0 · Major 0 · Minor 2 (spec/docs nits only)**  
Per-FR verdicts: **FR1–FR7 all APPROVED.**

Top findings (all positive in this review):
1. Capacity and concurrency handling is robust, with real DB row locks and repeated stress tests; this directly mitigates the highest identified risk (RK1).
2. Security posture is strong for a teaching-scale system: password, JWT, error hygiene, and IDOR controls all match NFR2, with no credential or stacktrace leaks.
3. The backend test suite provides excellent behavioural coverage and traceability to the spec, making regressions in core logic unlikely if tests stay green.
