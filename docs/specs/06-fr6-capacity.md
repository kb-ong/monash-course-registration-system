# FR6 — Course Capacity Validation

**Story:** US2, US4 · **Actors:** System (authoritative), Student (P1)
**Endpoints:** `POST /api/registrations` (enforcement), `DELETE /api/registrations/{id}` (release),
`GET /api/courses*` (observation)
**Depends on:** [FR3](./03-fr3-register-course.md), [FR4](./04-fr4-drop-course.md).

> This is the highest-risk feature in Phase 1. Read §2 twice.

---

## 1. Requirement

> A course can never hold more students than its capacity, no matter how many students register at the
> same instant. Exactly `capacity` registrations succeed, the rest receive 409 `COURSE_FULL`, and
> `availableSeats` ends at exactly 0.

---

## 2. Behaviour rules

| # | Rule |
| --- | --- |
| B1 | **Never** read `availableSeats`, decide, then write in separate transactions. The check and the decrement must occur while holding a lock. |
| B2 | Required mechanism: `@Lock(LockModeType.PESSIMISTIC_WRITE)` on a `findWithLockById` repository method, inside the transaction that inserts the registration and decrements the seat count. Optimistic `@Version` + bounded retry (≤ 3) is an acceptable alternative **only** if the concurrency test proves it. |
| B3 | Seat arithmetic is `availableSeats = availableSeats - 1` executed by the database against the locked row — not by re-saving a stale in-memory entity from a concurrent read. |
| B4 | If `availableSeats = 0` at the moment of the lock, return 409 `COURSE_FULL`; do not create a registration; do not change seats. |
| B5 | Every successful registration decrements by exactly 1. Every successful drop increments by exactly 1, clamped to `capacity`. |
| B6 | Invariant **C1** (checked outside transactions): `availableSeats + count(registrations for course) = capacity`. |
| B7 | Invariants **C2**: `0 ≤ availableSeats ≤ capacity`; **C3**: `availableSeats ≥ 0` at all times (DB `CHECK`). |
| B8 | A course with `capacity = 0` is permanently full and can never be registered into. |
| B9 | Failure responses must report the *current* state truthfully: `COURSE_FULL` implies `availableSeats = 0` in the database at that moment. |
| B10 | The 201 response's `availableSeats` is the post-decrement value read inside the same transaction. |
| B11 | The unique constraint on `(student_id, course_id)` must never be the mechanism that protects capacity — it only protects duplicates. Two different students must be handled by the lock. |
| B12 | No queueing, no waitlist, no retry loop that eventually grants a seat after returning 409. A 409 is final for that request. |
| B13 | Capacity is immutable in Phase 1: nothing in the API raises or lowers it. |
| B14 | The front end treats seat counts as advisory: it may disable a full course's button but must still handle 409 `COURSE_FULL` gracefully (another student may have taken the last seat). |

---

## 3. Concurrency design (normative reference implementation)

```java
@Transactional
public RegistrationResult register(String studentId, UUID courseId) {
    RegistrationPeriod period = periodService.current();
    if (!period.open()) throw new RegistrationClosedException();

    Course locked = courseRepository.findWithLockById(courseId)   // SELECT ... FOR UPDATE
            .orElseThrow(CourseNotFoundException::new);

    if (registrationRepository.existsByStudentIdAndCourseId(studentId, courseId))
        throw new AlreadyRegisteredException();

    int credits = registrationRepository.sumCredits(studentId);
    if (credits + locked.getCreditHours() > MAX_CREDITS)
        throw new CreditLimitExceededException();

    if (locked.getAvailableSeats() <= 0)
        throw new CourseFullException(locked.getCode());

    locked.setAvailableSeats(locked.getAvailableSeats() - 1);
    Registration saved = registrationRepository.save(new Registration(student, locked, Instant.now()));
    auditService.record(...);
    return ...;
}
```

Notes for the coder:

- `sumCredits` must be a `JPQL` aggregate over `Registration` joined to `Course` — not a load-all loop.
- H2 in `MODE=PostgreSQL` supports `SELECT … FOR UPDATE`; JPA emits it via `PESSIMISTIC_WRITE`.
- Keep the lock window short: no HTTP calls, no CSV work, no logging I/O inside it.
- Set `@Transactional(isolation = Isolation.READ_COMMITTED)` explicitly so behaviour is identical if the
  datasource changes to a real PostgreSQL.
- Deadlock avoidance: only one row is ever locked per transaction, so no lock-ordering problem.
- Do **not** use `synchronized`, `static` locks or in-JVM `ConcurrentHashMap` counters: they pass a
  single-node test and are wrong. The tester's concurrency test runs one JVM, so additionally assert
  the SQL contains `for update` (see T-FR6-08) to catch this cheat.

---

## 4. Validation rules

| Condition | Response |
| --- | --- |
| `availableSeats >= 1` | proceed |
| `availableSeats = 0` | 409 `COURSE_FULL` |
| `capacity = 0` | 409 `COURSE_FULL` |
| drop when `availableSeats = capacity` | 200, seats unchanged (clamped) |

---

## 5. API surface used

Enforced on `POST /api/registrations`; released on `DELETE /api/registrations/{id}`; observed through
`availableSeats`/`capacity` in `GET /api/courses*` and `GET /api/registrations`.

---

## 6. Audit log events

| Case | `action` | `outcome` | `detail` |
| --- | --- | --- | --- |
| Seat taken | `REGISTER` | `SUCCESS` | `null` |
| No seat | `REGISTER_REJECTED` | `FAILURE` | `COURSE_FULL` |
| Seat released | `DROP` | `SUCCESS` | `null` |

Under a 20-thread burst on a 10-seat course, expect exactly 10 `REGISTER/SUCCESS` and 10
`REGISTER_REJECTED/FAILURE` rows (for 20 distinct students), and `availableSeats = 0`.

---

## 7. UI

**Seat meter** ([09 §5.4](./09-ui-design-system.md#54-seatmeter)) — the single visual language for
capacity:

| Fill | Condition | Colour |
| --- | --- | --- |
| > 50 % free | `availableSeats / capacity > 0.5` | success green |
| 10–50 % free | `0.1 < ratio ≤ 0.5` | gold |
| 1–10 % free | `0 < ratio ≤ 0.1` | amber, label "Only N left" |
| 0 | `availableSeats = 0` | danger red, label `FULL` |

States the UI must handle:

| State | Trigger | Rendering |
| --- | --- | --- |
| Stale seats | 409 `COURSE_FULL` on a course the UI showed as available | toast danger "That course just filled up."; refetch list; card becomes FULL with a 320 ms flash of the danger border |
| Last seat won | 201 when `availableSeats` becomes 0 | success toast + card switches to FULL immediately, "You took the last seat" microcopy |
| Race with drop | student sees FULL, refetches, seat reappears | meter animates from 0 to 1, button re-enables without a reload |

Copy rules: never say "sold out" (it is not commerce), always "full" or "no seats available".

---

## 8. Test scenarios

| ID | Type | Given | When | Then |
| --- | --- | --- | --- | --- |
| T-FR6-01 | happy | `FIT2004` capacity 80, seats 80 | one registration | 201; `availableSeats=79`; invariant C1 holds |
| T-FR6-02 | negative | `FIT3143` capacity 1, seats 1, already taken by Daniel | Aisha registers | 409 `COURSE_FULL`; `availableSeats` still 0; Aisha has no registration |
| T-FR6-03 | concurrency | fresh `FIT3143` (capacity 1) | 5 distinct students POST concurrently via `ExecutorService` + `CountDownLatch` | exactly 1 × 201 and 4 × 409 `COURSE_FULL`; 1 registration row; `availableSeats=0` |
| T-FR6-04 | concurrency | dedicated course capacity 10, seats 10, 20 distinct students | 20 concurrent POSTs, all released by one latch | exactly 10 × 201, 10 × 409; 10 rows; `availableSeats=0`; no 5xx; no duplicate registration ids |
| T-FR6-05 | concurrency | as T-FR6-04 | repeat the burst 3 times with a fresh course each time | same result every run (no flakiness tolerance: 3/3 must pass) |
| T-FR6-06 | concurrency | 1 seat free, 2 students | concurrent register + drop by the incumbent, then register | invariant C1 holds; total successes never exceed capacity |
| T-FR6-07 | edge | `FIT3182` capacity 40, seats 0 (seeded full, A-01) | register | 409 `COURSE_FULL`; seats stay 0 |
| T-FR6-08 | edge | any successful register | enable SQL logging | the emitted SQL for the course read contains `for update` (proves a row lock was used, catches in-JVM locking) |
| T-FR6-09 | edge | course at `availableSeats = capacity` | drop | `availableSeats = capacity`, not `capacity + 1` |
| T-FR6-10 | edge | full course `FIT3143`, one student drops | register immediately after | 201; `availableSeats=0` again |
| T-FR6-11 | negative | any failed register | inspect DB | `availableSeats` unchanged and no registration row inserted (atomicity: no partial write) |

### How to write T-FR6-04 (tester recipe)

```java
int N = 20;
ExecutorService pool = Executors.newFixedThreadPool(N);
CountDownLatch start = new CountDownLatch(1);
List<Future<Integer>> futures = new ArrayList<>();
for (int i = 0; i < N; i++) {
    String token = tokenFor(studentIds.get(i % studentIds.size())); // must be N *distinct* students
    futures.add(pool.submit(() -> {
        start.await();
        return mockMvc.perform(post("/api/registrations")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"courseId\":\"" + courseId + "\"}"))
            .andReturn().getResponse().getStatus();
    }));
}
start.countDown();
```

- Use `MockMvc` with `@SpringBootTest(webEnvironment = MOCK)` — `MockMvc` is thread-safe for concurrent
  `perform` calls, but each thread **must** build its own request.
- Use N distinct students. Reusing one student produces 409 `ALREADY_REGISTERED`, which would mask a
  capacity bug.
- Seed extra students in the test fixture if needed (the seeder only provides 3; the test may insert
  more via the repository inside a `@BeforeEach`).
- Assert on status **counts**, not ordering.
- Never `Thread.sleep`; use latches.

---

## 9. Acceptance criteria

**FR6-AC1** — Given a course with N seats and M > N concurrent registrations by distinct students,
then exactly N succeed, M−N receive 409 `COURSE_FULL`, and `availableSeats = 0`.
**FR6-AC2** — Given any failed registration, then no registration row is created and the seat count is
unchanged.
**FR6-AC3** — Given invariant C1, then after every operation
`availableSeats + count(registrations) = capacity`.
**FR6-AC4** — Given a full course, then the 409 body uses `COURSE_FULL` and the seat count is still 0.
**FR6-AC5** — Given a drop, then exactly one seat returns and capacity is never exceeded.
**FR6-AC6** — Given the implementation, then capacity protection is enforced by a database row lock (or
a version check), not by JVM-level synchronisation.
**FR6-AC7** — Given the seat meter, then a student can tell at a glance whether a course is full,
nearly full, or open.

---

*Prev: [05 FR5](./05-fr5-view-registered.md) · Next: [07 FR7](./07-fr7-registration-period.md)*
