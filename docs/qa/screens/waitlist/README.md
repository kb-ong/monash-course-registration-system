# FR11 waitlist management — visual evidence

**Baseline revision:** `e5ff2f6` (current `origin/main` after FR9/FR10, the merge base of this PR)
**Changed revision:** the FR11 feature branch `agent/waitlist-management-19b23cdd1744` after merging
`e5ff2f6` (the PR names the exact merge-commit SHA)
**Scenario:** the seeded demo. `FIT3182` is full (`capacity = 1`; Daniel holds the only seat).
Aisha is seeded at waitlist position 1 and Priya at position 2. Registration window open on the
seeded demo clock. Both revisions include FR9 clash detection and FR10's configured credit limit, so
the only differences between 01 and 02 are FR11's queue behaviour and its copy.
**Environment:** Google Chrome headless, 1440 × 1000 viewport, light theme, frontend on
`http://127.0.0.1:5173`, seeded backend on port 8080. Screenshots are actual application captures,
not mockups. `prefers-reduced-motion` is set for capture consistency.

| # | Revision | Shot | What it shows |
| --- | --- | --- | --- |
| 01 | baseline `e5ff2f6` | [course detail](01-before-course-detail.png) | Aisha viewing the full `FIT3182`: disabled **Full** button, "there is no waitlist", FR9 clash note and FR10 credit note already present. No notification bell. |
| 02 | FR11 | [course detail](02-after-course-detail.png) | The same page for the same student: **Waitlist #1**, the position sentence, **Leave waitlist**, and the notification bell with one unread. |
| 03 | FR11 | [dashboard](03-after-dashboard-waitlist.png) | Aisha's dashboard with the new **Waitlist** section (position #1, join date, leave action) and the "Waitlisted courses" count. |
| 04 | FR11 | [inbox](04-after-notifications.png) | The header bell panel with the seeded `WAITLIST_JOINED` notification ("You are #1 …"). |
| 05 | FR11 | [promotion notification](05-after-promotion-notification.png) | After Daniel drops `FIT3182`: Aisha is enrolled automatically (6 credits, "Your courses"), the queue section is gone, and the inbox shows the new `WAITLIST_PROMOTED` notification. |
| 06 | FR11 | [promoted course](06-after-promoted-registered.png) | The same course detail now shows **Registered**, **View in schedule** and **Drop course** — the queue place became a real registration. |
| 07 | FR11 | [join action](07-after-join-waitlist-action.png) | Daniel (who dropped) sees the full course with a gold **Join waitlist** action instead of a dead end. |
| 08 | FR11 | [join dialog](08-after-waitlist-dialog.png) | The waitlist confirmation explains that waiting reserves neither credits nor a seat and that promotion is automatic. |
| 09 | FR11 | [joined dashboard](09-after-joined-dashboard.png) | Daniel's dashboard after joining: position #2 behind Priya, unread join notification, queue count 1. |
| 10 | FR11 | [queued course](10-after-queued-course-detail.png) | Daniel's course detail in the queued state (**Waitlist #2**, leave action). |

The only intended differences between 01 and 02 are FR11's queue behaviour and its copy; viewport,
theme, seeded catalogue, course, student and registration window are identical. The footer copy was
also updated to name the full Phase 2 feature set ("Prerequisites, clashes, credit limit &
waitlists"). Baseline was run from a separate worktree at `e5ff2f6`; the changed branch was run from
the ticket worktree with the shipped seed. The screenshots were refreshed after resolving the merge
conflicts so they match the final merged code.
