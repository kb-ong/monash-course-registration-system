# FR7 — Registration Period Control

**Story:** US4 · **Actors:** Student (P1), System (authority), Operator (config only)
**Endpoints:** `GET /api/registration-period`; enforced on `POST /api/registrations` and
`DELETE /api/registrations/{id}`
**Depends on:** [FR3](./03-fr3-register-course.md), [FR4](./04-fr4-drop-course.md).

---

## 1. Requirement

> Registration and dropping are only possible inside an officially configured window. The window is a
> closed interval `[startAt, endAt]`. The server decides — the browser only displays.

---

## 2. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | Window comes from configuration: `app.registration.period-start`, `period-end`, interpreted in the university zone `Asia/Kuala_Lumpur` (`+08:00`). No database table, no admin endpoint (NG1). |
| B2 | `open := !serverTime.isBefore(startAt) && !serverTime.isAfter(endAt)` — **both ends inclusive** (A-07). |
| B3 | `serverTime` is produced from an injectable `java.time.Clock` bean so tests can freeze/shift time. Production bean = `Clock.system(ZoneId.of("Asia/Kuala_Lumpur"))`. |
| B4 | `POST` and `DELETE` re-evaluate the window **inside** the request, server-side, on every attempt. A token issued while open does not grant a grace period. |
| B5 | Client clock is never trusted; the SPA computes a `serverOffset` from `GET /api/registration-period` and renders countdowns against it (A-12). |
| B6 | Closed → 403 `REGISTRATION_CLOSED` for both mutations. Reads (`GET /api/courses`, `GET /api/registrations`, export) stay **available** when closed — students can still see their enrolment. |
| B7 | The response always includes `serverTime`, even when closed, so the UI can say when it opens. |
| B8 | `startAt >= endAt` is a configuration error: application fails fast at startup with a clear message. Do not silently treat the window as always closed. |
| B9 | Exactly at `startAt` → open. Exactly at `endAt` → open. 1 ms after `endAt` → closed. 1 ms before `startAt` → closed. |
| B10 | The period returned by `GET /api/registration-period` is the **active semester's** period (`semester` field = configured active semester). Phase 1 has one window for all courses. |
| B11 | Time zone of stored/compared instants is irrelevant — comparisons are on `Instant`/`OffsetDateTime` values, never on formatted strings. |
| B12 | A DST-style anomaly is out of scope (`Asia/Kuala_Lumpur` has no DST). |

---

## 3. Validation rules

| Condition | Response |
| --- | --- |
| `startAt ≤ now ≤ endAt` | proceed |
| `now < startAt` | 403 `REGISTRATION_CLOSED`, message "Registration opens on 1 October 2026, 12:00 am." |
| `now > endAt` | 403 `REGISTRATION_CLOSED`, message "Registration closed on 20 December 2026, 11:59 pm." |
| config `startAt ≥ endAt` | startup failure (`IllegalStateException` with both values) |

---

## 4. API surface used

```http
GET /api/registration-period
```

```json
{
  "semester": "2026-S2",
  "startAt": "2026-10-01T00:00:00+08:00",
  "endAt": "2026-12-20T23:59:59+08:00",
  "open": true,
  "serverTime": "2026-11-08T11:26:41+08:00"
}
```

Public endpoint (no token). A bad token is ignored, not rejected (`SecurityConfig` must `permitAll`
this path).

Mutation failure:

```json
{"error":{"code":"REGISTRATION_CLOSED","message":"Registration closed on 20 December 2026, 11:59 pm.","timestamp":"2026-12-21T00:00:00+08:00"}}
```

---

## 5. Audit log events

| Case | `action` | `outcome` | `detail` |
| --- | --- | --- | --- |
| Register rejected (closed) | `REGISTER_REJECTED` | `FAILURE` | `REGISTRATION_CLOSED` |
| Drop rejected (closed) | `DROP` | `FAILURE` | `REGISTRATION_CLOSED` |

The audit row's `created_at` is the server time of the attempt — this is the evidence a support officer
uses. Period *queries* are not audited.

---

## 6. UI

**Global element:** `StatusBanner` under the header on Dashboard, Catalog, Detail and Schedule.

| State | Condition | Banner | CTA state |
| --- | --- | --- | --- |
| Open | `open = true` | green-tinted glass strip: `● Registration open · closes 20 Dec 2026, 11:59 pm` (relative: "in 42 days") | Register/Drop enabled |
| Opening soon | `now < startAt` and `startAt − now ≤ 7 days` | gold strip: `Registration opens 1 Oct 2026 · in 3 days` + live countdown `Dd HH:mm:ss` | disabled, label "Opens 1 Oct" |
| Not yet open | `now < startAt`, > 7 days | navy strip: `Registration opens 1 Oct 2026` | disabled |
| Closed | `now > endAt` | danger-tinted strip: `Registration closed 20 Dec 2026` | disabled, label "Registration closed" |
| Unknown | period fetch failed | neutral strip: `Registration status unavailable` + `Retry` | disabled until known |

Rules:

- The countdown ticks once per second but is `aria-hidden`; an `aria-live="polite"` element announces
  only the minute-level change, not every second.
- When the countdown reaches zero the SPA refetches `/api/registration-period` and the catalogue; it
  never flips state locally and assumes it is open.
- If a mutation returns 403 `REGISTRATION_CLOSED` while the banner shows open, the banner wins the
  argument: immediately refetch the period, show info toast "Registration has closed.", disable actions.
- Disabled actions always have a tooltip/`title` and an `aria-describedby` explaining why.

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR7-01 | happy | `Clock` frozen at `2026-11-08T11:26:41+08:00` (inside window) | `GET /api/registration-period` | 200; `open=true`; `semester=2026-S2`; `startAt` before `serverTime` before `endAt` (compare parsed instants) |
| T-FR7-02 | edge | clock = exactly `startAt` | GET period + `POST /registrations` | `open=true`; registration 201 |
| T-FR7-03 | edge | clock = exactly `endAt` | GET period + `POST /registrations` | `open=true`; registration 201 |
| T-FR7-04 | edge | clock = `startAt − 1s` | `POST /registrations` | 403 `REGISTRATION_CLOSED`; `open=false`; no row created |
| T-FR7-05 | edge | clock = `endAt + 1s` | `POST /registrations` | 403 `REGISTRATION_CLOSED`; audit `REGISTER_REJECTED/FAILURE/REGISTRATION_CLOSED` |
| T-FR7-06 | edge | clock = `endAt + 1s` | `DELETE /api/registrations/{ownId}` | 403 `REGISTRATION_CLOSED`; row still exists |
| T-FR7-07 | happy | clock = `endAt + 1s` (closed) | `GET /api/courses`, `GET /api/registrations`, `GET /api/registrations/export` | all 200 — reads are never blocked by the window |
| T-FR7-08 | negative | `period-start >= period-end` (property override) | application context start | context fails to start with a message naming both values (or a dedicated startup test asserts `IllegalStateException`) |
| T-FR7-09 | security | clock closed, no token | `POST /api/registrations` | 401 `UNAUTHORIZED` (auth precedes period) |
| T-FR7-10 | edge | clock inside window | `GET /api/registration-period` with a garbage `Authorization` header | 200 — endpoint is public and ignores the header |

### Tester recipe for time control

```java
@SpringBootTest
class RegistrationPeriodTest {
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock clock() {
            return Clock.fixed(OffsetDateTime.parse("2026-12-20T23:59:59+08:00").toInstant(),
                               ZoneId.of("Asia/Kuala_Lumpur"));
        }
    }
    // ...
}
```

The bean must be consumed by `RegistrationPeriodService` (constructor-injected `Clock`), **not**
`Instant.now()` or `LocalDateTime.now()` called statically. If the coder used `now()` directly, T-FR7-02…06
cannot pass — that is a spec violation, not a test problem.

---

## 8. Acceptance criteria

**FR7-AC1** — Given the window, when `GET /api/registration-period` is called, then it returns
`semester`, `startAt`, `endAt`, `open` and `serverTime`, with `open` computed from server time.
**FR7-AC2** — Given `serverTime` is inside `[startAt, endAt]` inclusively, then mutations are accepted.
**FR7-AC3** — Given `serverTime` is outside the window, then both `POST` and `DELETE` return 403
`REGISTRATION_CLOSED` and no data changes.
**FR7-AC4** — Given a closed window, then all read endpoints still return data.
**FR7-AC5** — Given a client whose clock is wrong, then server-side decisions are unaffected.
**FR7-AC6** — Given a misconfigured window, then the application refuses to start rather than silently
granting or denying all registration.
**FR7-AC7** — Given the UI, then a student can always tell whether registration is open, opening soon,
or closed, and why a disabled action is disabled.

---

*Prev: [06 FR6](./06-fr6-capacity.md) · Next: [08 Non-functional](./08-non-functional.md)*
