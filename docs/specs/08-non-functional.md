# 08 — Non-Functional Requirements

Each NFR is written to be **verifiable**: a measurable threshold, a measurement procedure, and a test
ID the tester can execute. Thresholds are Phase-1 (demo-scale) values, not production SLAs.

---

## NFR1 — Performance

### NFR1.1 Requirement

| ID | Metric | Threshold | Condition |
| --- | --- | --- | --- |
| NFR1-P1 | Catalogue search **end-to-end** (keystroke → results painted) | **< 3 000 ms**, p95 | seeded catalogue, 16 courses, localhost |
| NFR1-P2 | `GET /api/courses` server time | < 250 ms p95 | same |
| NFR1-P3 | `POST /api/registrations` server time | < 300 ms p95 | uncontended |
| NFR1-P4 | `GET /api/registrations` server time | < 250 ms p95 | ≤ 8 registrations |
| NFR1-P5 | Login (`POST /api/auth/login`) server time | < 500 ms p95 | BCrypt cost 10 dominates |
| NFR1-P6 | Initial SPA load (dev build excluded; `vite preview`) | < 2 500 ms on a warm cache, < 4 000 ms cold | localhost |
| NFR1-P7 | Catalogue with 10 concurrent readers | 0 errors, p95 < 1 000 ms | see T-NFR1-04 |

The **binding** number is NFR1-P1: "search feels instant and never exceeds 3 seconds". P2–P7 exist so a
failure can be localised to client or server.

### NFR1.2 Measurement procedure (normative)

Server timings — 30 requests, discard the first 3 (JIT/connection warm-up), report p95:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"studentId":"32012345","password":"Password123!"}' | jq -r .token)

for i in $(seq 1 30); do
  curl -s -o /dev/null -w '%{time_total}\n' \
    -H "Authorization: Bearer $TOKEN" \
    'http://localhost:8080/api/courses?q=algo'
done | sort -n | awk '{a[NR]=$1} END {printf "p50=%.3fs p95=%.3fs max=%.3fs\n", a[int(NR*0.5)], a[int(NR*0.95)], a[NR]}'
```

End-to-end (NFR1-P1) — browser, not curl:

1. `npm run build && npm run preview`, open `/catalog`.
2. DevTools → Performance → CPU **4× slowdown**, Network **Fast 3G** (to model a real student).
3. Type `algo` into the search field; measure from the last keystroke to the frame where the result
   card is painted (`Performance` → `LCP`/paint marker).
4. Repeat 5 times; the worst run must be < 3 000 ms.

Implementation constraints that make the target reachable: 300 ms input debounce, single request per
settled query, no client-side re-sort of large arrays, no `layout` thrash in the seat meter animation
(animate `transform`/`opacity` only).

### NFR1.3 Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-NFR1-01 | performance | backend running, seeded | 30 × `GET /api/courses?q=algo` | p95 < 250 ms, 0 non-200 |
| T-NFR1-02 | performance | as above | 30 × `GET /api/registrations` | p95 < 250 ms |
| T-NFR1-03 | performance | as above | 30 × login with valid credentials | p95 < 500 ms, all 200 |
| T-NFR1-04 | performance | 10 concurrent readers of `/api/courses` | 10 threads × 20 requests | 0 errors, p95 < 1 000 ms |
| T-NFR1-05 | manual | production build in `preview` | search `algo` under 4× CPU throttle | results painted < 3 000 ms, worst of 5 |
| T-NFR1-06 | manual | fresh browser profile | load `/login` then `/catalog` | cold load < 4 000 ms; no layout shift > 0.1 CLS |

---

## NFR2 — Security

### NFR2.1 Requirement

| ID | Control | Requirement |
| --- | --- | --- |
| NFR2-S1 | Password storage | BCrypt, strength 10, `$2a$` prefix. Plaintext never stored, never logged, never returned. |
| NFR2-S2 | Token | JWT HS256, `sub` = studentId, `exp` = 120 min, secret from `JWT_SECRET` (≥ 32 bytes for HS256). No secrets committed. |
| NFR2-S3 | Transport of identity | `Authorization: Bearer`. No tokens in URLs, no cookies, no query params. |
| NFR2-S4 | Input validation | Every request body/param validated before use; `courseId` UUID-parsed; `q` length-capped; `studentId` regex-capped. |
| NFR2-S5 | SQL safety | JPA parameter binding only — no string-concatenated JPQL/SQL. |
| NFR2-S6 | Output hygiene | No `passwordHash`, no stack traces, no exception class names, no SQL fragments, no internal package names in any response. |
| NFR2-S7 | Error uniformity | Auth failures do not distinguish "unknown user" from "wrong password". |
| NFR2-S8 | CORS | Only `http://localhost:5173`; no wildcard; no `allowCredentials`. |
| NFR2-S9 | Headers | `X-Content-Type-Options: nosniff`, `Cache-Control: no-store` on API responses, `Referrer-Policy: no-referrer` on the SPA. |
| NFR2-S10 | Password in transit of logs | The strings `password`, `token`, `Bearer ` must never appear in application logs with a value. Log the *event*, not the secret. |
| NFR2-S11 | Brute force | BCrypt cost 10 provides ~50–100 ms per attempt; no lockout in Phase 1 (accepted, documented). |
| NFR2-S12 | Dependency | No known-critical CVEs in direct dependencies at review time (`mvn dependency:tree` inspected, `npm audit --omit=dev`). |

### NFR2.2 401/403 matrix (must be exactly this)

| Scenario | Endpoint family | Status | Code |
| --- | --- | --- | --- |
| No `Authorization` header | protected | 401 | `UNAUTHORIZED` |
| Header not starting `Bearer ` | protected | 401 | `UNAUTHORIZED` |
| Malformed JWT | protected | 401 | `UNAUTHORIZED` |
| Valid signature, expired | protected | 401 | `UNAUTHORIZED` |
| Valid signature, wrong secret | protected | 401 | `UNAUTHORIZED` |
| Valid token, wrong password supplied | `POST /auth/login` | 401 | `INVALID_CREDENTIALS` |
| Valid token, own registration | `DELETE` | 200 | — |
| Valid token, someone else's registration | `DELETE` | 403 | `NOT_OWNER` |
| Valid token, window closed | `POST`/`DELETE` | 403 | `REGISTRATION_CLOSED` |
| No token at all | public (`/auth/login`, `/semesters`, `/registration-period`) | 200 | — |

Order note: on `POST /registrations` with **no token and a closed window**, the answer is **401**, not
403 — authentication precedes business authorisation (see T-FR7-09).

### NFR2.3 Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-NFR2-01 | security | seeded student | query `student` table | `password_hash` starts `$2a$10$`, length 60, and is not `Password123!` |
| T-NFR2-02 | security | any endpoint returning a student/course/registration | inspect JSON for `password`, `passwordHash`, `$2a$` | zero matches |
| T-NFR2-03 | security | no token / bad token / expired token | call each protected endpoint | 401 `UNAUTHORIZED` for all 8 protected routes |
| T-NFR2-04 | security | valid token | `DELETE` another student's registration | 403 `NOT_OWNER`; body contains no course code |
| T-NFR2-05 | security | any 4xx/5xx | inspect body | matches `{"error":{"code","message","timestamp"}}`, no `trace`, no `exception`, no `at edu.monash` |
| T-NFR2-06 | security | forced 500 (e.g. repository stub throwing) | call endpoint | 500 `INTERNAL_ERROR` with generic message; server log holds the stack trace, response does not |
| T-NFR2-07 | security | `OPTIONS /api/courses` with `Origin: http://evil.example` | preflight | no `Access-Control-Allow-Origin` for that origin |
| T-NFR2-08 | security | `OPTIONS` with `Origin: http://localhost:5173` | preflight | `Access-Control-Allow-Origin: http://localhost:5173`, `Allow-Headers` includes `Authorization` |
| T-NFR2-09 | security | grep the repo | `git grep -n "Password123!"` / secrets scan | the demo password appears only in seed/docs; no real secret; `JWT_SECRET` read from env with a clearly-labelled dev default |
| T-NFR2-10 | security | `POST /api/registrations` with `courseId` = `"1' OR '1'='1"` | send | 400 `VALIDATION_ERROR`; no SQL error surfaced |

---

## NFR3 — Availability & Resilience

### NFR3.1 Requirement

Phase 1 runs as a single Spring Boot process with an in-memory database. Honest framing:

| ID | Property | Phase 1 target | Note |
| --- | --- | --- | --- |
| NFR3-A1 | Availability during a demo window | ≥ 99 % (≤ 9 min unplanned downtime per 15 h) | single node |
| NFR3-A2 | Restart time (RTO) | < 60 s | `mvn spring-boot:run` or jar start |
| NFR3-A3 | Data durability (RPO) | **0 by design — data is intentionally ephemeral** | H2 `create-drop`; restart reseeds |
| NFR3-A4 | Failure isolation | A failing request returns the error envelope; the process stays up | no unhandled exception escapes the controller advice |
| NFR3-A5 | Startup validation | Misconfiguration (bad period, missing secret) fails fast with an actionable message | do not start half-broken |
| NFR3-A6 | Client resilience | Every screen has loading, empty, error and retry states; a failed call never leaves a blank page | see §09 |
| NFR3-A7 | Graceful degradation | If `/api/registration-period` fails, mutations are disabled (fail closed), reads still render | never assume open |

### NFR3.2 Practical approach (what the coders actually do)

1. **Fail fast, fail loud.** `@ConfigurationProperties` validation (`@Validated`) on JWT secret length and
   period ordering; throw at startup.
2. **One error boundary.** `@RestControllerAdvice` maps every exception to the envelope. No 500 without
   `INTERNAL_ERROR`; the stack trace goes to the log with a correlation id.
3. **Health probe.** Add `spring-boot-starter-actuator` only if the operator needs it — **not required**
   by the contract. If added, expose `/actuator/health` only, never `/actuator/env`.
4. **Restart is the recovery plan.** Documented in the README: restart, reseed, re-login. Concurrency
   tests assume this and never rely on persisted state.
5. **No shared mutable statics.** The only mutable global state is the database; the app must be safe to
   restart at any time.
6. **Front end:** axios timeout 10 s, one retry for idempotent GETs with 300 ms backoff, no retry for
   mutations (a retried POST could double-register — the server would 409, but the toast would lie).
7. **Known single points of failure (accepted):** the single JVM and the in-memory DB. Phase 1 has no
   horizontal scaling, no replication, no external queue. Stated so nobody claims otherwise in review.

### NFR3.3 Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-NFR3-01 | resilience | app running | `kill` then restart | app serves `/api/semesters` within 60 s; seed data restored exactly per §8 |
| T-NFR3-02 | resilience | app running | `GET /api/courses` with the DB connection forced to fail (or a stubbed repository throwing) | 500 `INTERNAL_ERROR`, process still alive, next request succeeds |
| T-NFR3-03 | resilience | bad config `period-start > period-end` | start context | startup fails with a message naming both values; no partial start |
| T-NFR3-04 | resilience | frontend, API stopped | load `/catalog` | error panel with Retry, no blank screen, nav still usable |
| T-NFR3-05 | resilience | period endpoint failing | load `/catalog` | Register actions disabled, banner says status unavailable; catalogue still visible |

---

## NFR4 — Audit Logging

### NFR4.1 Requirement

Every **register** and **drop** attempt produces exactly one `audit_log` row — success or failure —
written even when the business transaction rolls back. Reads are not audited.

| ID | Requirement |
| --- | --- |
| NFR4-1 | Exactly one row per attempt. No duplicates from retries inside the same request. |
| NFR4-2 | Failure rows survive rollback → written in `REQUIRES_NEW`. |
| NFR4-3 | Row content is immutable (insert-only; no update/delete in the application). |
| NFR4-4 | `created_at` = server clock at attempt time, UTC. |
| NFR4-5 | No passwords, tokens or PII beyond `studentId`/`courseCode`. |
| NFR4-6 | A failure to write the audit row must **not** silently swallow the original error; log it, keep the original HTTP status. |

### NFR4.2 Event matrix (exhaustive)

| # | Trigger | `action` | `outcome` | `detail` | HTTP |
| --- | --- | --- | --- | --- | --- |
| E-01 | Register success | `REGISTER` | `SUCCESS` | `null` | 201 |
| E-02 | Register, window closed | `REGISTER_REJECTED` | `FAILURE` | `REGISTRATION_CLOSED` | 403 |
| E-03 | Register, unknown course | `REGISTER_REJECTED` | `FAILURE` | `COURSE_NOT_FOUND` | 404 |
| E-04 | Register, duplicate | `REGISTER_REJECTED` | `FAILURE` | `ALREADY_REGISTERED` | 409 |
| E-05 | Register, credit limit | `REGISTER_REJECTED` | `FAILURE` | `CREDIT_LIMIT_EXCEEDED` | 409 |
| E-06 | Register, no seats | `REGISTER_REJECTED` | `FAILURE` | `COURSE_FULL` | 409 |
| E-07 | Drop success | `DROP` | `SUCCESS` | `null` | 200 |
| E-08 | Drop, not found | `DROP` | `FAILURE` | `REGISTRATION_NOT_FOUND` | 404 |
| E-09 | Drop, not owner | `DROP` | `FAILURE` | `NOT_OWNER` | 403 |
| E-10 | Drop, window closed | `DROP` | `FAILURE` | `REGISTRATION_CLOSED` | 403 |

Out of scope for `audit_log` (application log only): login success/failure, token rejection, catalogue
reads, CSV export.

### NFR4.3 Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-NFR4-01 | audit | clean DB | successful register | exactly 1 row: E-01 fields; `created_at` within 5 s of now |
| T-NFR4-02 | audit | closed window | failed register | exactly 1 row E-02 **and** no registration row (proves `REQUIRES_NEW`) |
| T-NFR4-03 | audit | duplicate | second register | row count for that student/course = 1 success + 1 `ALREADY_REGISTERED` failure |
| T-NFR4-04 | audit | full course | failed register | 1 row E-06 with `course_code=FIT3182`/`FIT3143` |
| T-NFR4-05 | audit | other student's registration | failed drop | 1 row E-09 `student_id` = caller, `course_code` may be set |
| T-NFR4-06 | audit | successful drop | `DELETE` | 1 row E-07; no update/delete of previous rows |
| T-NFR4-07 | audit | 20-thread burst on 10 seats (T-FR6-04) | after completion | 10 E-01 + 10 E-06 rows; total 20; no `null` action/outcome |
| T-NFR4-08 | audit | any response body | scan | no audit row content is exposed by any API (no audit endpoint exists) |

---

## NFR5 — Usability

### NFR5.1 Requirement

| ID | Requirement |
| --- | --- |
| NFR5-U1 | Registering a known course takes **≤ 4 clicks** from the Dashboard (target 3). |
| NFR5-U2 | Dropping a course takes **≤ 3 clicks** from My Schedule (target 2). |
| NFR5-U3 | Every destructive action asks for confirmation; every mutation gives feedback within 100 ms (pending state) and a terminal toast. |
| NFR5-U4 | No dead ends: every empty/error state offers the next action. |
| NFR5-U5 | Fully keyboard operable; visible focus; no keyboard traps (dialog traps focus by design and releases on close). |
| NFR5-U6 | WCAG 2.1 AA: 4.5:1 text contrast, 3:1 for UI boundaries and focus indicators. |
| NFR5-U7 | Layout works at 360 px width with no horizontal scroll. |
| NFR5-U8 | Copy is student-facing English, sentence case, no jargon, no emoji. |

### NFR5.2 Click budget — counted explicitly

A **click** = one discrete pointer activation (mousedown+up on a control). Typing, scrolling, tabbing and
page load do not count. A link that navigates counts as one click.

**Flow A — register a course you can already see (3 clicks):**

| Click | Target | Result |
| --- | --- | --- |
| 1 | `Browse catalogue` (Dashboard hero CTA or nav) | `/catalog` loads |
| 2 | `Register` on the `FIT2004` card | Confirm dialog opens |
| 3 | `Confirm register` | 201, toast, card switches to Registered |

**Flow B — register via search (3 clicks + typing):**

| Click | Target | Result |
| --- | --- | --- |
| 1 | Search input | focus (typing `FIT2004` is not a click) |
| 2 | `Register` on the single result | Confirm dialog |
| 3 | `Confirm register` | 201 |

**Flow C — drop (2 clicks):**

| Click | Target | Result |
| --- | --- | --- |
| 1 | `Drop` on the schedule row | Confirm dialog |
| 2 | `Drop course` | 200, row removed, toast |

Budget check: 3 ≤ 4 (U1) and 2 ≤ 3 (U2). If the implementation needs a fourth click (e.g. a separate
"select semester" step), it fails NFR5-U1 — the semester selector must default to the active semester
without user action.

### NFR5.3 Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-NFR5-01 | manual | logged in at `/` | perform Flow A | success with exactly 3 clicks; stopwatch < 20 s |
| T-NFR5-02 | manual | logged in at `/catalog` | perform Flow B | success with exactly 3 clicks |
| T-NFR5-03 | manual | logged in at `/schedule` with 1 course | perform Flow C | course dropped with exactly 2 clicks |
| T-NFR5-04 | manual | any screen | Tab through the page | focus visible on every interactive element; order follows visual order; no trap outside dialogs |
| T-NFR5-05 | manual | dialog open | press `Esc`; then `Tab` repeatedly | `Esc` closes; focus cycles inside the dialog then returns to the trigger |
| T-NFR5-06 | manual | 360 × 640 viewport | browse catalog, open detail, register | no horizontal scroll; all controls ≥ 44 × 44 px |
| T-NFR5-07 | manual | axe DevTools (or Lighthouse a11y) on all 5 pages | audit | 0 critical/serious violations; contrast ≥ 4.5:1 for body text |
| T-NFR5-08 | manual | OS "reduce motion" enabled | navigate, open dialog, register | no transform/opacity animations > 1 frame; content appears instantly |
| T-NFR5-09 | manual | 200 % browser zoom | all pages | no clipped content, no overlapping text |
| T-NFR5-10 | manual | empty registrations | open `/schedule` | empty state visible with a working `Browse catalogue` CTA |

---

## NFR summary matrix

| NFR | Owner | Blocking for Phase 1 | Evidence required |
| --- | --- | --- | --- |
| NFR1 Performance | frontend + backend | Yes (P1, P2) | timing table in `docs/qa/` |
| NFR2 Security | backend (+frontend storage) | Yes (S1–S8) | `SecurityConfigTest`, leak scan |
| NFR3 Availability | backend | Partly (A4, A5, A7) | startup-failure test, error-envelope test |
| NFR4 Audit | backend | Yes (all events) | `AuditLogTest` row assertions |
| NFR5 Usability | frontend | Yes (U1–U3, U6) | manual checklist + screenshot set |

---

*Prev: [07 FR7](./07-fr7-registration-period.md) · Next: [09 UI design system](./09-ui-design-system.md)*
