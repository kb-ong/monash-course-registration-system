# FR1 — Student Login

**Story:** US1 · **Actors:** Student (P1), System
**Endpoints:** `POST /api/auth/login`, `GET /api/auth/me`
**Depends on:** none. **Depended on by:** everything (all mutations require a bearer token).

---

## 1. Requirement

> A student authenticates with their 8-digit student number and password and receives a time-limited
> bearer token. Every protected screen and API call requires that token. The token identifies exactly
> one student and never carries privileges beyond "this is student X".

---

## 2. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | Credentials are `studentId` (8 digits) + `password`. `studentId` is trimmed; `password` is **not** (leading/trailing spaces are significant). |
| B2 | Lookup by `student_id`. Missing student and wrong password both yield **401 `INVALID_CREDENTIALS`** with an identical body (except `timestamp`). |
| B3 | On a missing student, the server still performs one BCrypt comparison against a fixed dummy hash so response timing does not reveal account existence. |
| B4 | Success returns `{token, student}` where `student` has exactly `studentId, name, email, program`. The password hash is never serialised. |
| B5 | Token: HS256, `sub = studentId`, `iat`, `exp = iat + 120 min`, no roles claim. Secret from `app.jwt.secret` (`JWT_SECRET` env). |
| B6 | The client stores the token in `localStorage` under the key `monash.token` and sends `Authorization: Bearer <token>` on every `/api` request via an axios interceptor. |
| B7 | Any `401 UNAUTHORIZED` from a protected endpoint clears the token, clears in-memory auth state, and redirects to `/login?reason=expired` preserving the intended path in `location.state.from`. |
| B8 | `GET /api/auth/me` returns the profile for the token subject (fresh from the database, not from claims) — this is how the SPA rehydrates after a reload. |
| B9 | Logout is client-side only (delete token, redirect to `/login`). No server endpoint, no token blacklist. |
| B10 | Open-redirect protection: after login, redirect only to a value in `location.state.from` that starts with `/` and not `//`; otherwise `/`. |
| B11 | Login failures are logged to the **application log** only (`WARN auth.login.failed studentId=…`), never to `AuditLog` (see A-10). Never log the password or the token. |
| B12 | Login is rate-limited only by BCrypt cost in Phase 1; no lockout (documented limitation, NFR3). |

---

## 3. Validation rules

| Field | Rule | Violation |
| --- | --- | --- |
| `studentId` | required, not blank, matches `^[0-9]{8}$` | 400 `VALIDATION_ERROR` |
| `password` | required, not blank, length ≤ 72 (BCrypt truncation limit) | 400 `VALIDATION_ERROR` |
| body | valid JSON object | 400 `VALIDATION_ERROR` |
| token (E2) | present, signature valid, not expired | 401 `UNAUTHORIZED` |

Validation runs **before** any database lookup; a malformed request never reaches BCrypt.

---

## 4. API surface used

| Call | When | Notes |
| --- | --- | --- |
| `POST /api/auth/login` | submit credentials | public |
| `GET /api/auth/me` | app bootstrap with a stored token | 200 → hydrate; 401 → clear + `/login` |

### Example — success

```http
POST /api/auth/login
Content-Type: application/json

{"studentId":" 32012345 ","password":"Password123!"}
```

```http
HTTP/1.1 200 OK
Cache-Control: no-store

{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzMjAxMjM0NSIsImlhdCI6MTc2MjU2ODgwMSwiZXhwIjoxNzYyNTc2MDAxfQ.…",
  "student": {
    "studentId": "32012345",
    "name": "Aisha Rahman",
    "email": "aisha.rahman@student.monash.edu",
    "program": "BCS"
  }
}
```

### Example — failure

```http
HTTP/1.1 401 Unauthorized

{"error":{"code":"INVALID_CREDENTIALS","message":"Invalid student ID or password.","timestamp":"2026-11-08T11:26:41+08:00"}}
```

---

## 5. Audit log events

None in `audit_log`. Emitted to the application log:

| Event | Level | Fields |
| --- | --- | --- |
| `auth.login.success` | INFO | `studentId` |
| `auth.login.failed` | WARN | `studentId` (as supplied, truncated to 12 chars), `reason=INVALID_CREDENTIALS` |
| `auth.token.rejected` | WARN | `reason` ∈ {`MISSING`, `MALFORMED`, `EXPIRED`, `BAD_SIGNATURE`} |

---

## 6. UI

**Screen:** Login (`/login`) — see [09 §6.1](./09-ui-design-system.md#61-login-login).

States:

| State | Trigger | Rendering |
| --- | --- | --- |
| Idle | first paint | navy gradient hero split-panel, form enabled |
| Submitting | submit | button shows spinner + "Signing in…", inputs `disabled`, no layout shift |
| Field error | client validation | inline message under field, `aria-invalid="true"`, focus first invalid field |
| Credential error | 401 | inline alert above the form: "We couldn't sign you in. Check your student ID and password." form re-enabled, password cleared, focus returns to password |
| Expired session | `?reason=expired` | inline info alert: "Your session expired. Please sign in again." |
| Network error | no response | inline alert: "Can't reach the server. Check your connection and try again." |
| Success | 200 | store token, replace history to destination, no flash of login screen |

Accessibility: `<form>` with `onSubmit`; labels are real `<label for>`; error region is
`role="alert"` `aria-live="assertive"`; the whole flow is operable with Tab/Enter only; the password
field has a show/hide toggle (`aria-pressed`) that does not submit the form.

---

## 7. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR1-01 | happy | seeded student `32012345` / `Password123!` | `POST /api/auth/login` with exact credentials | 200; `token` non-empty and has 3 dot-separated segments; `student.studentId=32012345`, `name="Aisha Rahman"`, `program="BCS"`; body contains no `password`/`passwordHash` key |
| T-FR1-02 | happy | valid token from T-FR1-01 | `GET /api/auth/me` with `Bearer <token>` | 200; profile equals the login response's `student`; no hash field |
| T-FR1-03 | negative | seeded student `32012345` | `POST /api/auth/login` with `password="wrong-password"` | 401; `error.code=INVALID_CREDENTIALS`; no `token` key; error envelope has `code`,`message`,`timestamp` |
| T-FR1-04 | negative | no such student | `POST` with `studentId="99999999"`, any password | 401 `INVALID_CREDENTIALS`; response body identical to T-FR1-03 apart from `timestamp` |
| T-FR1-05 | negative | — | `POST` with `studentId="3201234"` (7 digits) | 400 `VALIDATION_ERROR`; no BCrypt work performed (assert response < 50 ms or verify via a spy on `PasswordEncoder`) |
| T-FR1-06 | negative | — | `POST` with `{}` (both fields absent) | 400 `VALIDATION_ERROR`; password not echoed in the message |
| T-FR1-07 | edge | valid student | `POST` with `studentId=" 32012345 "` (padded) | 200 — leading/trailing whitespace trimmed |
| T-FR1-08 | edge | valid student | `POST` with `password="Password123! "` (trailing space) | 401 — password is not trimmed |
| T-FR1-09 | security | — | `GET /api/auth/me` with no `Authorization` header | 401 `UNAUTHORIZED`; `WWW-Authenticate: Bearer` present |
| T-FR1-10 | security | token signed with a different secret | `GET /api/auth/me` with that token | 401 `UNAUTHORIZED` (bad signature), not 500 |
| T-FR1-11 | security | expired token (issue with `exp` in the past, e.g. via `Clock` offset or a 0-minute property) | `GET /api/auth/me` | 401 `UNAUTHORIZED` |
| T-FR1-12 | security | any response from E1/E2 | inspect JSON | no `password`, `passwordHash`, `$2a$` substring, no stack trace, no `Exception` class name anywhere in the body |

### Notes for the tester

- T-FR1-01/05 must assert the **absence** of `passwordHash` — deserialise into a `Map` and check keys,
  not into a typed DTO (a DTO would hide the leak).
- For T-FR1-11, prefer overriding `app.jwt.expiration-minutes=0` in a nested `@SpringBootTest(properties=…)`
  class over sleeping.
- Assert `Cache-Control: no-store` on E1 in T-FR1-01.

---

## 8. Acceptance criteria

**FR1-AC1** — Given a seeded student, when they POST valid credentials, then the response is 200 with
a bearer token and a student object containing no password material.
**FR1-AC2** — Given an unknown student id, when they attempt login, then the response is 401
`INVALID_CREDENTIALS` and is indistinguishable from a wrong-password response.
**FR1-AC3** — Given a malformed `studentId` (not 8 digits), when they attempt login, then the response
is 400 `VALIDATION_ERROR` and no credential comparison occurs.
**FR1-AC4** — Given a valid token, when the SPA calls `GET /api/auth/me`, then it receives the current
profile and can render the authenticated shell.
**FR1-AC5** — Given a missing, malformed, expired or wrongly-signed token, when any protected endpoint
is called, then the response is 401 `UNAUTHORIZED` and the SPA clears its session and returns to `/login`.
**FR1-AC6** — Given a successful login, when the student reloads the page, then the session survives
(token rehydrated from `localStorage`, `me` succeeds) without re-entering credentials.
**FR1-AC7** — Given the login form, when the student uses only the keyboard, then every control is
reachable in a logical order, focus is visible, and Enter submits.

---

*Prev: [00 Overview](./00-overview.md) · Next: [02 FR2](./02-fr2-view-courses.md)*
