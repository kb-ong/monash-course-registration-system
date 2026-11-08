# 10 — Test Strategy

**Owner:** tester (`backend/src/test/**`, `docs/qa/**`) · **Consumers:** reviewer, orchestrator.
**Spec baseline:** [00](./00-overview.md) … [09](./09-ui-design-system.md). Every test ID in this
document exists in a FR/NFR file; this file does not invent requirements.

---

## 1. Objectives

1. Prove every acceptance criterion (FR1–FR7) and every NFR threshold is met, or report precisely which
   one is not.
2. Prove the **capacity invariant under concurrency** — the single most likely defect in Phase 1.
3. Prove the API contract exactly as frozen: status codes, error codes, envelope shape, headers.
4. Prove the UI is usable, accessible and complete in all its states (no blank screens, no dead ends).
5. Produce evidence a reviewer can audit without re-running anything: logs, reports, screenshots.

Non-objectives: performance at production scale, security penetration testing, cross-browser matrix
beyond the two most recent Chrome/Firefox/Safari, load testing beyond 20 concurrent requests.

---

## 2. Pyramid

```
                     ▲
                    ╱ ╲        Manual UI / exploratory        10 %   17 checks
                   ╱───╲       (human, screenshot evidence)
                  ╱     ╲
                 ╱ MockMvc╲    API / slice tests             35 %   102 automated scenarios
                ╱──────────╲   (Spring context + HTTP layer)
               ╱            ╲
              ╱    Unit      ╲ Domain + service logic         55 %   pure JUnit, no Spring
             ╱────────────────╲
```

| Layer | Tooling | Scope | Speed budget |
| --- | --- | --- | --- |
| **Unit** | JUnit 5, AssertJ, Mockito | `RegistrationService` decision order, period maths, CSV formatting, seat clamping, JWT claim building | < 50 ms/test |
| **Slice / integration** | `@SpringBootTest` + `MockMvc` (full context, H2) | every endpoint: status, body, headers, persistence, audit rows | < 2 s/test |
| **Concurrency** | `@SpringBootTest(MOCK)` + `ExecutorService`/`CountDownLatch` | seat decrement atomicity, register/drop races | < 10 s/test |
| **Manual UI** | browser + DevTools + axe | flows, states, a11y, print, responsive | human time |

There is **no Selenium/Playwright** in Phase 1 (no browser automation dependency in the frozen stack).
UI verification is manual with a written checklist and screenshots in `docs/qa/screens/`.

### 2.1 What belongs where (decision table)

| Behaviour | Layer | Why |
| --- | --- | --- |
| `open = start ≤ now ≤ end` | unit (fixed `Clock`) | pure function |
| Error → HTTP status mapping | MockMvc | needs the advice + serialisation |
| `ALREADY_REGISTERED` from unique constraint | MockMvc | needs the DB |
| Seat decrement under 20 threads | concurrency | needs real transactions |
| CSV quoting of commas | unit (formatter) + MockMvc (headers) | both matter |
| Colour contrast, focus ring | manual | visual |
| Click budget (NFR5) | manual | human counting |

---

## 3. Environment

| Item | Value |
| --- | --- |
| OS | Linux (WSL2) or macOS; Windows native not supported for the test run |
| JDK | 21+ (`java -version` must report ≥ 21; project targets release 21, may compile on 23) |
| Maven | 3.9+ |
| Node | 20+ with npm 10+ |
| Backend | `http://localhost:8080`, H2 `jdbc:h2:mem:registration` |
| Frontend (manual) | `http://localhost:5173` with `/api` proxy |
| DB state | `create-drop` per context; **every test run starts from seed** |
| Time zone | `Asia/Kuala_Lumpur`; tests must not depend on the machine's zone |
| Network | none required (no external services, no Testcontainers) |

Environment variables (all optional; defaults in `application.yml`):

```bash
export JWT_SECRET='local-dev-secret-min-32-bytes-long-000000'
export REGISTRATION_START='2026-10-01T00:00:00'
export REGISTRATION_END='2026-12-20T23:59:59'
export ACTIVE_SEMESTER='2026-S2'
```

---

## 4. Data seeding for tests

1. `DataSeeder` (main scope) runs on every context start and is **idempotent** (`if count > 0 return`).
   It produces exactly the dataset in [00 §8](./00-overview.md#8-seed-data-normative-for-tests): 3
   students, 16 `2026-S2` courses, 3 `2026-S1` courses, fixed UUIDs and codes.
2. Tests **read** seeded rows by fixed code/UUID. Tests never assert on auto-increment ids.
3. Tests that need more students/courses create them through a test-only `TestDataFactory`
   (`backend/src/test/java/.../support/TestDataFactory.java`) using the repositories, with:
   - unique `studentId` = `"99" + 6-digit counter` (e.g. `99000001`),
   - unique `code` = `TST` + 4 digits (e.g. `TST0001`),
   - `capacity` and `availableSeats` passed explicitly.
4. Mutating tests are **not** `@Transactional` (they must commit so other threads see data).
   Isolation comes from `@DirtiesContext(classMode = AFTER_CLASS)` on mutating classes and from unique
   fixtures per test.
5. Never rely on test execution order. Never `Thread.sleep`.
6. Concurrency fixtures use a dedicated course per test (`TST01xx`) so a previous burst cannot pollute
   the next.

`TestDataFactory` contract (tester owns it):

```java
Student student(String studentId);                    // password "Password123!"
Course course(String code, int capacity, int seats);  // semester 2026-S2
Course course(String code, int capacity, int seats, String semester);
String token(Student s);                              // signs a real JWT via JwtService
```

---

## 5. Concurrency test approach (normative)

**Goal:** prove FR6 — never oversell.

Rules:

| # | Rule |
| --- | --- |
| C1 | Use `@SpringBootTest(webEnvironment = MOCK)` with `MockMvc`; `MockMvc` is safe for concurrent `perform` when each thread builds its own `MockHttpServletRequest`. |
| C2 | Synchronise thread start with `CountDownLatch start(1)`; release all at once after all threads are parked. |
| C3 | **N distinct students** for N concurrent registrations. Reusing a student yields 409 `ALREADY_REGISTERED` and hides capacity bugs. |
| C4 | Assert on **counts per status**, not on which thread won. |
| C5 | Repeat the burst 3× with fresh fixtures; 3/3 must pass (a flaky concurrency test is a failing test). |
| C6 | Assert the invariant after the burst: `availableSeats + registrationCount == capacity`, `availableSeats == 0`, `count(201) == capacity`, `count(409 COURSE_FULL) == N - capacity`. |
| C7 | Assert zero 5xx. A deadlock that surfaces as 500 is a failure, not "retry later". |
| C8 | Additionally assert the SQL contains `for update` (or that the entity carries a `@Version` and retries were observed) — this catches in-JVM `synchronized` cheats that pass single-node tests. |
| C9 | Timeout the whole burst (`future.get(20, SECONDS)`) so a lock bug fails fast instead of hanging CI. |
| C10 | Always `pool.shutdownNow()` in `@AfterEach`. |

Reference implementation: [06 §8](./06-fr6-capacity.md#how-to-write-t-fr6-04-tester-recipe).

SQL assertion recipe:

```java
@ExtendWith(OutputCaptureExtension.class)
class CapacityConcurrencyTest {
    @Test
    void registerUsesRowLock(CapturedOutput out) {
        // ... one successful register ...
        assertThat(out.getAll()).containsIgnoringCase("for update");
    }
}
```

(Enable `spring.jpa.show-sql=true` and `spring.jpa.properties.hibernate.format_sql=false` in the test
properties for that class.)

---

## 6. Test inventory (by class)

| Test class | Layer | Covers | Notes |
| --- | --- | --- | --- |
| `RegistrationServiceTest` | unit | FR3 decision order (supports T-FR3-04…10), FR6 clamping, FR7 open/closed | Mockito repositories + fixed `Clock` |
| `CsvFormatterTest` | unit | T-FR5-04, 05, 09 (quoting, CRLF, BOM) | pure string test |
| `JwtServiceTest` | unit | T-FR1-10, 11 (signature, expiry), claim `sub` | fixed clock |
| `AuthControllerTest` | MockMvc | T-FR1-01 … 12 | asserts absence of password keys |
| `CatalogControllerTest` | MockMvc | T-FR2-01 … 13 | seed-order assertions |
| `RegistrationControllerTest` | MockMvc | T-FR3-01 … 13, T-FR4-01 … 10 | state machine coverage |
| `CapacityConcurrencyTest` | concurrency | T-FR6-01 … 11, T-NFR4-07 | 3 repeats |
| `RegistrationPeriodTest` | MockMvc + fixed clock | T-FR7-01 … 10 | `@TestConfiguration` clock |
| `CsvExportTest` | MockMvc | T-FR5-03 … 09 | headers + body bytes |
| `AuditLogTest` | MockMvc + repository | T-NFR4-01 … 08 | row counts, `REQUIRES_NEW` proof |
| `SecurityConfigTest` | MockMvc | T-NFR2-03, 04, 07, 08; T-FR1-09 | route matrix |
| `ErrorEnvelopeTest` | MockMvc | T-NFR2-05, 06 | no leakage |
| `StartupValidationTest` | context | T-FR7-08, T-NFR3-03 | expect startup failure |
| `PerformanceSmokeTest` | integration (tagged `perf`) | T-NFR1-01 … 04 | excluded from the default run |

`PerformanceSmokeTest` is tagged `@Tag("perf")` and must be excluded by default so `mvn test` stays
fast; it is run explicitly (see §8).

---

## 7. Traceability matrix

### 7.1 Functional

| Requirement | Acceptance criteria | Automated tests | Manual |
| --- | --- | --- | --- |
| FR1 Login | AC1–AC7 | T-FR1-01…08, 12 (Auth), T-FR1-09…11 (Security), T-FR1-05 (Service) | T-NFR5-04 (keyboard) |
| FR2 Catalogue | AC1–AC8 | T-FR2-01…13 | T-NFR5-06 (360 px) |
| FR3 Register | AC1–AC8 | T-FR3-01…13 | T-NFR5-01, 02 (clicks) |
| FR4 Drop | AC1–AC6 | T-FR4-01…10 | T-NFR5-03 (clicks), T-NFR5-05 (dialog Esc) |
| FR5 Registered view + CSV + print | AC1–AC6 | T-FR5-01…10 | T-FR5-11, print check §9.4 |
| FR6 Capacity | AC1–AC7 | T-FR6-01…11 | seat-meter states (T-NFR5-07 visual) |
| FR7 Period | AC1–AC7 | T-FR7-01…10 | T-NFR3-05 (fail-closed banner) |

### 7.2 Non-functional

| Requirement | Tests |
| --- | --- |
| NFR1 Performance | T-NFR1-01…04 (automated, tagged `perf`), T-NFR1-05, 06 (manual) |
| NFR2 Security | T-NFR2-01…10 |
| NFR3 Availability | T-NFR3-01…05 |
| NFR4 Audit | T-NFR4-01…08 |
| NFR5 Usability | T-NFR5-01…10 |

### 7.3 Coverage completeness check (tester must confirm before sign-off)

| Check | Expected |
| --- | --- |
| Distinct test IDs in FR/NFR files | **119** |
| Implemented (automated or manual evidence) | 119 |
| Automated (JUnit/MockMvc/concurrency) | 102 |
| of which tagged `perf`, run explicitly | 4 |
| Manual / static with screenshot or transcript | 17 |
| Not implemented | **0** |

Count by file: FR1 12 · FR2 13 · FR3 13 · FR4 10 · FR5 11 · FR6 11 · FR7 10 · NFR1 6 · NFR2 10 ·
NFR3 5 · NFR4 8 · NFR5 10 = **119** distinct IDs.

The 17 manual/static items are: `T-FR5-11`, `T-NFR1-05`, `T-NFR1-06`, `T-NFR2-09`, `T-NFR3-01`,
`T-NFR3-04`, `T-NFR3-05`, `T-NFR5-01` … `T-NFR5-10`.

---

## 8. Commands

Run from the repo root.

### 8.1 Build gates (must pass before testing)

```bash
# backend compiles (Definition of Done #2)
cd backend && mvn -q -DskipTests package

# frontend builds (Definition of Done #3)
cd ../frontend && npm ci && npm run build
```

### 8.2 Full backend suite

```bash
cd backend && mvn test
```

Expected: `BUILD SUCCESS`, `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`.
Surefire reports: `backend/target/surefire-reports/`.

### 8.3 Targeted runs

```bash
cd backend
mvn -Dtest=CapacityConcurrencyTest test          # the critical one; run 3× before sign-off
mvn -Dtest=RegistrationPeriodTest test
mvn -Dtest='AuthControllerTest,CatalogControllerTest' test
mvn -Dtest=AuditLogTest test
mvn -Dtest=SecurityConfigTest test
```

Run the concurrency test repeatedly (flakiness gate):

```bash
cd backend
for i in 1 2 3; do mvn -q -Dtest=CapacityConcurrencyTest test || exit 1; done
```

### 8.4 Performance (excluded from the default run)

```bash
cd backend && mvn -Dtest=PerformanceSmokeTest test
```

Then the manual end-to-end measurement from [08 §NFR1.2](./08-non-functional.md#nfr12-measurement-procedure-normative).

### 8.5 Static checks (manual, no plugin required)

```bash
cd frontend && npm run lint
cd ../backend && mvn -q -DskipTests compile
```

### 8.6 Manual UI session

```bash
# terminal 1
cd backend && mvn spring-boot:run

# terminal 2
cd frontend && npm run dev
# open http://localhost:5173, login 32012345 / Password123!
```

Screenshot set to capture into `docs/qa/screens/`:

```
login-desktop.png        login-error.png         login-mobile.png
dashboard-full.png       dashboard-empty.png     dashboard-loading.png
catalog-grid.png         catalog-empty.png       catalog-error.png
catalog-full-course.png  catalog-mobile.png      catalog-closed.png
detail-registered.png    detail-full.png         detail-404.png
schedule-populated.png   schedule-empty.png      schedule-mobile.png
print-preview.pdf        axe-login.png           axe-catalog.png
```

### 8.7 Reset between manual runs

Restart the backend (H2 is `create-drop`). There is no reset endpoint by design.

---

## 9. Manual UI checklist (abridged; full list in `docs/qa/manual-checklist.md`)

### 9.1 Flows

- [ ] Login → Dashboard → Catalog → Detail → Register → Toast → Schedule (happy path, 3 clicks per NFR5).
- [ ] Register a full course → error toast → card becomes FULL without a reload.
- [ ] Drop → confirm → row disappears → seat count rises.
- [ ] Logout → protected URL redirects to `/login` → login returns to the intended page.
- [ ] Reload mid-session → still signed in (`/auth/me` rehydration).

### 9.2 States

- [ ] Every page: loading (skeleton), empty, error (with Retry), success.
- [ ] Registration closed: banner + disabled actions with reasons; reads still work.
- [ ] Full course: badge, meter, disabled button, tooltip.
- [ ] 404 route and 404 course.

### 9.3 Accessibility

- [ ] Keyboard-only pass on all 5 pages; focus visible everywhere; `Esc` closes dialogs; focus returns.
- [ ] axe DevTools: 0 critical/serious on Login, Dashboard, Catalog, Detail, Schedule.
- [ ] 200 % zoom and 360 px width: no clipping, no horizontal scroll.
- [ ] OS reduce-motion: no animation beyond 1 frame.
- [ ] Screen reader smoke (VoiceOver or NVDA): login errors announced, result count announced, toast
      announced.

### 9.4 Print

- [ ] `Ctrl/Cmd+P` on `/schedule` → preview shows header block, timetable, course table only.
- [ ] No nav, buttons, toasts, background colours or shadows.
- [ ] "Save as PDF" A4 → single page for ≤ 6 courses; text selectable.

---

## 10. Defect reporting

File in `docs/qa/defects.md` (append-only) with:

```
ID:        DEF-001
Test:      T-FR6-04
Severity:  blocker | major | minor | cosmetic
Owner:     coder-backend | coder-frontend
Evidence:  surefire report path / screenshot / curl transcript
Expected:  <quoted spec line, e.g. FR6-AC1>
Actual:    <observed>
Repro:     <exact commands or clicks>
```

Severity ladder:

| Severity | Definition | Effect on exit |
| --- | --- | --- |
| `blocker` | Data corruption, oversell, auth bypass, crash, no way to complete a core flow | Blocks sign-off |
| `major` | Wrong status code, missing audit row, broken state, inaccessible control | Blocks sign-off |
| `minor` | Visual inconsistency, awkward copy, non-blocking edge case | Fix or explicitly waive by reviewer |
| `cosmetic` | Pixel/typography nit | May ship |

The tester **reports**, never fixes production code.

---

## 11. Exit criteria

Phase 1 is test-complete when **all** hold:

1. `mvn -q -DskipTests package` succeeds.
2. `mvn test` → `Failures: 0, Errors: 0`; the only skipped tests are `@Tag("perf")`.
3. `CapacityConcurrencyTest` passes 3 consecutive runs.
4. `npm run build` succeeds; `npm run lint` has no errors.
5. All 119 verification items have evidence: automated report line, or manual note + screenshot.
6. Zero open `blocker` and zero open `major` defects.
7. NFR1-P1 measured and recorded (< 3 000 ms, p95).
8. NFR2 leak scan clean (no `$2a$`, `passwordHash`, `Bearer` values, or stack traces in responses).
9. NFR4 audit matrix E-01…E-10 each observed at least once.
10. NFR5 click budget measured (3 register, 2 drop).
11. axe: 0 critical/serious on all 5 pages.
12. `docs/qa/report.md` written with: environment, commit SHA, command transcripts, results table,
    defect list, screenshots index, and a signed verdict (`PASS` / `PASS WITH WAIVERS` / `FAIL`).

**Coverage policy decision:** line/branch coverage is *informational*, not a gate, because JaCoCo is not
in the frozen dependency set. Behavioural coverage (the 112 items) is the gate. Do not add a coverage
plugin without orchestrator approval.

---

## 12. Known limitations to state in the report

- Single JVM + in-memory DB: concurrency evidence covers thread-level races, not multi-node.
- `MockMvc` bypasses the network: it validates the Spring stack, not TLS/proxy behaviour.
- No automated UI: regressions in layout/state must be caught by the manual checklist.
- No cross-browser automation: Safari/Firefox verified by hand only.
- No load testing beyond 20 concurrent requests.

---

*Prev: [09 UI design system](./09-ui-design-system.md) · Up: [00 Overview](./00-overview.md)*
