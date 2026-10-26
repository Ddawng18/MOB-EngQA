# Progressive Moderation and Warning Report

## 1. Scope and sources

This change extends the existing report/moderation workflow so that every successful warning or
timeout increase produces a Vietnamese notification that states the committed counter, the next
penalty, and the remaining events before that penalty. It covers regular users and English centres
(`CENTER` accounts) with their separate promo-warning counter.

There is no `kb/` directory and no `android-skills/` directory in this workspace, its parent
directories, or the global Kilo configuration. The source of truth used for this implementation is:

- `app/src/main/java/com/example/engqa/model/User.java` — the actual threshold constants.
- `app/src/main/java/com/example/engqa/data/AdminRepository.java` — the moderation transaction.
- `app/src/main/res/values/strings.xml` — the existing Vietnamese admin policy copy.
- `firestore.rules` — authorization for counters and moderation notifications.
- `docs/PROMO.md`, `docs/EngQA_Cau_truc_du_an.md`, `README.md` — the existing report/promo/account
  architecture.
- The existing Java/XML source for notifications, blocked-account handling and promo ownership.

## 2. Verified policy table

| Account type | Violation scope | Counter | Next penalty threshold | Next penalty | Source |
|---|---|---|---|---|---|
| Regular user | Reported question or answer | `users/{uid}.warningCount` | 3 | Account `status = TIMEOUT` for 24 hours; `warningCount` resets to 0; `timeoutCount` +1 | `User.java:24,25,27`; `AdminRepository.applyWarning`; `strings.xml:410` |
| Regular user | Repeated timeout events | `users/{uid}.timeoutCount` | 3 | `status = DELETED` (soft delete of the profile; the Firebase Auth user is not deleted) | `User.java:25`; `AdminRepository`; `HomeActivity` blocked gate |
| English centre | Promo violation | `users/{promoAuthorUid}.promoWarningCount` | 3 | `role` changes from `CENTER` to `USER`, `centerId` cleared, centre document deleted; courses/posts/promos are cleaned up after the transaction | `User.java:26`; `AdminRepository`; `strings.xml:410` |
| English centre | Account-level repeated timeouts | `users/{uid}.timeoutCount` (the same counter as a regular account; there is no separate centre timeout counter) | 3 | `status = DELETED` (soft delete) | `User.java:25`; `AdminRepository` |
| English centre | Non-promo content (question/answer) | `users/{uid}.warningCount` | 3 | 24-hour account timeout | `User.java:24,27`; `AdminRepository` |

Important distinctions verified in code:

- A promo warning increments **both** `warningCount` (the account can still be timed out) and
  `promoWarningCount` (the centre-revocation counter).
- A non-promo warning never increments `promoWarningCount`.
- There is no warning or timeout counter on `centers/{centerId}`. The counters live on the
  authenticated centre account in `users/{uid}`.
- `Center.STATUS_SUSPENDED` exists as a constant but is not used by any admin action. No
  intermediate promo-publication or centre-suspension restriction is implemented.
- There is no per-promo warning counter. Multiple reports about the same promo do not add multiple
  warnings because the target document is marked `warned = true` after the first successful action.

## 3. Existing counters and their meanings

| Field | Meaning | Reset / scope |
|---|---|---|
| `users/{uid}.warningCount` | Number of content warnings in the current cycle; the third warning triggers a timeout | Reset to 0 when a timeout is applied |
| `users/{uid}.timeoutCount` | Number of timeout events that have occurred | Never reset by the current code |
| `users/{uid}.promoWarningCount` | Number of promo warnings attributed to this centre account | Separate from `warningCount`; not reset by the current code |
| `users/{uid}.status` | `ACTIVE`, `TIMEOUT` or `DELETED` | `DELETED` is a soft delete/tombstone |
| `users/{uid}.timeoutUntil` | End time of the current 24-hour timeout | Cleared when the account is soft-deleted |
| `questions|answers|promos/{id}.warned` | Per-content guard: one warning per reported item | Set in the same transaction as the counters |
| `reports/{id}.status` | `pending`, `resolved`, `dismissed` | No counter change when dismissed |

Report counts are not violation counts. Multiple reports about the same target close as resolved
without incrementing the warning counter again. Dismissing a report creates an audit entry but no
warning and no notification.

## 4. Regular user warning and timeout behavior

Implemented in `AdminRepository.applyWarning`:

1. A successful `warnReport` or `deleteReportedContent` action reads the target and the author's
   profile in a Firestore transaction.
2. `ModerationPolicy.applyWarning(...)` computes the committed state from the current counters.
3. The user document, target `warned` flag, report status, audit entry and warning notification are
   committed in that transaction.
4. A deletion notification is committed in the deletion batch, after the deletion succeeds.
5. A second report on already-warned content closes the report without incrementing any counter or
   creating a new warning notification. If the admin asked to delete already-warned content, the
   content is deleted after the transaction without adding a second warning; the admin is not told
   “deleted” before the deletion batch succeeds.

Notification wording:

- Warning 1 or 2: `Cảnh cáo lần 1/3. Còn 2 cảnh cáo nữa trước timeout 24 giờ. ...`
- Warning 3: the notification says the timeout was applied and reports the timeout ordinal and the
  remaining timeouts before deletion. It does **not** claim that warnings remain before the timeout.
- Third timeout: the notification says the account has been deleted according to the policy.
- Content deletion: the notification starts with `Nội dung đã bị xoá` and still includes the warning
  progress that the deletion caused.

## 5. English centre promo and permission behavior

- The recipient of a promo notification is `promos/{promoId}.authorUid`, the authenticated centre
  account that owns the promo. The notification also carries `centerId` when the centre still exists.
- Promo notifications use `promoWarningCount` and the **promo threshold**, not the account warning
  ordinal. The account timeout details are appended only when that same action also triggers a
  timeout.
- Warning 1 or 2: the centre is told how many promo warnings remain before the centre loses its
  `CENTER` permission.
- Warning 3: the centre is told that its `CENTER` permission has been revoked and the centre has been
  removed.
- If the same action also times out or soft-deletes the centre account, the notification reports that
  separate account-level consequence as well.
- No promo-only publication restriction or temporary centre suspension is implemented because the
  project does not define one. A promo violation does not disable unrelated centre functionality
  unless the third-warning revocation path runs.

## 6. Exact remaining-count rules

The single implementation is
`app/src/main/java/com/example/engqa/util/ModerationPolicy.java`:

```text
remainingWarnings       = max(0, WARNINGS_PER_TIMEOUT - warningCount)
remainingTimeouts       = max(0, TIMEOUTS_BEFORE_DELETE - timeoutCount)
remainingPromoWarnings  = max(0, PROMO_WARNINGS_BEFORE_CENTER_REVOKE - promoWarningCount)
```

Additional rules:

- `WARNINGS_PER_TIMEOUT = 3`, `TIMEOUTS_BEFORE_DELETE = 3`,
  `PROMO_WARNINGS_BEFORE_CENTER_REVOKE = 3`, `TIMEOUT_DURATION_MS = 24h`.
- On the third warning, the stored `warningCount` becomes `0`, so the notification reports the
  timeout event instead of a misleading “0 warnings remaining” message.
- If a threshold has already been reached, the next penalty is reported and the remaining count is
  clamped to zero. No negative count is possible.
- There is no warning-expiration, time-window, weighted-violation or multi-tier policy in the
  project. The formulas above therefore match the actual implementation.
- Threshold display in the admin dialog and the blocked-account screen uses the same
  `ModerationPolicy` constants; they are not independently hardcoded in those screens.

## 7. Notification content and delivery

Every warning notification contains:

1. What happened and the warning/promo-warning ordinal and threshold.
2. The content or promo label, when available.
3. The warning or timeout count and the authoritative threshold.
4. The next penalty and the remaining qualifying events.
5. The report reason, clipped to a safe length.
6. Any restriction that actually took effect in the same committed transaction.

The warning notification and its counters are written in the same Firestore transaction. A warning
that triggers a timeout carries the timeout details in that same document instead of creating a
second notification for one successful action. Deletion notifications are written in the deletion
batch.

Delivery mechanisms verified:

- **In-app:** `notifications/{id}` is read by `NotificationRepository.observeMine` and displayed by
  the existing `NotificationActivity`.
- **Local system notification:** `NotificationWatcher` posts a system notification for a newly added
  document while the app process is alive. There is no Firebase Cloud Messaging implementation in
  this project, so a killed app does not receive push.
- **Blocked-account screen:** a timed-out or soft-deleted account cannot open the notification list.
  `HomeActivity` passes the committed counters to `AccountBlockedActivity`, which displays the timeout
  progress, remaining timeouts, current warning count and unlock time. This is the permitted
  fallback for an account that has lost in-app access.
- A soft-deleted account’s notification documents are retained for moderation history, but the app
  does not claim they were delivered as a push. The deleted account sees the deleted state on the
  blocked screen.

Recipients and privacy:

- Regular user: the content author (`authorId`).
- Answer: the answer author, never the parent question author.
- Promo: the promo owner (`authorUid`), with `centerId` for association.
- Centre approval/rejection: `ownerUid`, falling back to the first manager.
- Reporter identity and administrative notes are not included in the warning notification.
- The notification rules continue to allow only the recipient to read the document.

## 8. Duplicate prevention and failure handling

- `warned` on the target prevents a second warning for multiple reports about the same content.
- All counter updates and the warning notification are in one transaction, so concurrent admin
  actions conflict and retry against the latest counters.
- Warning notification IDs are deterministic:
  `moderation_warning_{reportId}_{recipientId}` and
  `moderation_deleted_{reportId}_{recipientId}`. Retrying the same action/recipient is idempotent.
  Different reports or recipients get different IDs.
- A report dismissal and a review-only action create no notification.
- A failed transaction creates neither the counters nor the warning notification.
- A failed deletion batch creates no deletion notification.
- The admin is told “deleted” only after the deletion batch succeeds, including the already-warned
  delete path.

Residual partial-failure behavior (pre-existing architecture, unchanged):

- Deletion spans two operations: the warning/report transaction commits first, then the content
  deletion batch runs. If the second operation fails, the warning is already committed and the report
  is resolved, but the deletion notification is not written. The content remains and the report no
  longer appears in the pending queue. This is documented rather than hidden.
- Centre cleanup after role revocation (courses, posts, promos) is best-effort after the main
  transaction. The role/centre deletion itself is committed atomically.

## 9. Files changed

| File | Reason |
|---|---|
| `app/src/main/java/com/example/engqa/util/ModerationPolicy.java` | **New.** Single source for thresholds, committed counter calculation and remaining counts. |
| `app/src/main/java/com/example/engqa/notification/ModerationNotifications.java` | Progressive Vietnamese title/body, promo-specific counts, timeout/deletion wording. |
| `app/src/main/java/com/example/engqa/data/AdminRepository.java` | Uses the policy outcome; keeps counters and notification in the same transaction; fixes already-warned delete flow. |
| `app/src/main/java/com/example/engqa/AdminActivity.java` | Builds the warning confirmation from policy constants instead of hardcoded values. |
| `app/src/main/java/com/example/engqa/AccountBlockedActivity.java` | Shows committed timeout/warning progress to accounts that cannot open the notification list. |
| `app/src/main/java/com/example/engqa/HomeActivity.java` | Passes committed moderation counters to the blocked screen. |
| `app/src/main/res/values/strings.xml` | Progressive Vietnamese notification copy, blocked-screen copy and policy placeholders. |
| `app/src/test/java/com/example/engqa/util/ModerationPolicyTest.java` | **New.** 8 JVM tests for counters, remaining values and user/centre separation. |
| `app/src/androidTest/java/com/example/engqa/ModerationNotificationsTest.java` | **Updated.** 11 instrumentation tests for progressive and centre notification content. |
| `app/src/androidTest/java/com/example/engqa/AccountBlockedTest.java` | **Updated.** Verifies timeout progress on the blocked screen. |

No new Gradle dependency was added. The working tree also contains the earlier answer-report and
moderation-notification work; the paths above are the ones changed by this task.

## 10. Firebase, backend and security changes

- No Cloud Functions, FCM credentials or backend service were added.
- No new Firestore fields or collections were introduced for this task.
- `firestore.rules` was not changed by this task. The existing rules already:
  - let only `admins/{uid}` accounts write moderation notification types;
  - let only the recipient read or delete a notification;
  - let only an admin update `users/{uid}` counter/status fields;
  - enforce title ≤ 120 characters and body ≤ 300 characters.
- The working tree already contains the earlier moderation-notification rule changes from the
  preceding task. Those rules must be deployed for the moderation notification writes to succeed in
  a live Firebase project. No rules, functions or production data were deployed from this
  environment.

## 11. Tests performed and actual results

### 11.1 Commands

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain --no-daemon --no-configuration-cache
.\gradlew.bat connectedDebugAndroidTest --console=plain --no-daemon --no-configuration-cache
```

### 11.2 Results

| Check | Result | Evidence |
|---|---|---|
| JVM unit tests | **PASS** | 86 tests, 0 failures, 0 errors, 0 skipped. Includes 8 new `ModerationPolicyTest` tests. |
| `assembleDebug` | **PASS** | Debug APK built. |
| `assembleDebugAndroidTest` | **PASS** | Instrumentation APK built. |
| `lintDebug` | **PASS** | 0 errors, 289 warnings; the task completes successfully. The pre-existing `YSdhyju1` typo quick-fix warning still prints. |
| Instrumented tests | **PASS** | 20/20 on `Pixel_4(AVD) - 10` (Android 10 / API 29), including the 11 progressive notification tests. |

### 11.3 Required scenario matrix

| # | Scenario | Status | Evidence / limitation |
|---|---|---|---|
| 1 | Each warning increase notifies the affected regular user | **PASS** | Transaction writes the notification with the counter; `firstUserWarning_showsCurrentCountAndRemainingBeforeTimeout` verifies content. Live Firebase not run. |
| 2 | Each promo warning notifies the correct centre | **PASS** | Promo notification uses `promoWarningCount`; `promoWarning_usesPromoCounterAndCentreRemaining` verifies the 2/3 title and centre recipient. Live Firebase not run. |
| 3 | Remaining warnings before timeout | **PASS** | `ModerationPolicyTest` covers 2 → 1 → 0; notification test verifies “Còn 2 cảnh cáo”. |
| 4 | Remaining timeouts before account deletion | **PASS** | `thirdWarningAppliesTimeoutAndResetsWarningCounter`, `thirdTimeoutSoftDeletesAccountWithNoNegativeRemainingCount`, and notification tests. |
| 5 | Centre remaining count before the next penalty | **PASS** | `promoWarningUsesSeparateCentreCounter`, `thirdPromoWarningRevokesCentre`, and promo notification tests. |
| 6 | Centre promo restriction and permission consequences | **PASS** | Third promo warning revokes `CENTER`; notification says the centre was revoked and removed. No intermediate restriction is defined, so none is invented. |
| 7 | Repeated actions do not increment counters or create duplicate notifications | **PASS** | `warned` guard + transaction + deterministic `NotificationIdsTest`. Live concurrent admin actions not run. |
| 8 | Dismissed reports do not increase counters | **PASS** | `dismissReport` never touches warning counters or notifications (static). |
| 9 | Failed moderation actions do not create false success notifications | **PASS (code)** | Warning notification is in the transaction; deletion notification is in the deletion batch; already-warned delete now waits for the deletion before reporting success. Live failure injection not run. |
| 10 | Notification reflects the final committed counter | **PASS (warning path)** | The outcome is calculated and written in the same transaction. The later deletion notification uses that committed outcome; a concurrent later warning can make the absolute latest counter higher, which is a documented concurrency limitation. |
| 11 | User and centre counters remain separated | **PASS** | `userWarningNeverChangesPromoCounter` and `promoViolationAlsoCountsTowardAccountTimeout`. |
| 12 | Existing notification history and report workflows remain functional | **PASS** | Full build, 86 JVM tests and 20/20 instrumented tests. Live report processing not run. |
| 13 | Account deletion and permission revocation leave consistent state | **PASS (transaction) / limitation** | Role, `centerId`, centre document and account status commit together. Courses/posts/promos cleanup is best-effort; the Auth user is not deleted (soft delete only). |
| 14 | Security restrictions prevent unauthorized counter manipulation | **PASS (static rules)** | Only admins can update `users` counters or create moderation notifications; recipients can only read their own notifications. Rules emulator/deployment not run. |

### 11.4 Not run

- Real Firestore transactions/reads for warnings, timeouts, promo revocation and deletion.
- Firebase Security Rules emulator or production deployment verification.
- Real push delivery (the project has no FCM implementation).
- Manual UI inspection with seeded multi-manager centres.
- Concurrent admin actions against the same report/account.
- Failure injection for a deletion after the warning transaction commits.

No production data or Firebase configuration was modified.

## 12. Missing policy definitions

The following requirements are not defined by the project materials and were **not** invented:

1. **Per-promo warning count.** The project has one `promoWarningCount` per centre account, not one
   counter per promo. A second report about the same promo is blocked by `warned`, but the policy for
   counting multiple distinct violating promos is only implied by the account-level counter.
2. **Centre-specific timeout counter.** Centres use the same `users.timeoutCount` as regular
   accounts. There is no separate centre timeout count or centre deletion threshold.
3. **Intermediate centre restrictions.** No action restricts only promo publication, temporarily
   suspends a centre, or revokes a specific permission before the third warning. `STATUS_SUSPENDED`
   exists but is unused. Only the third-promo-warning revocation/removal is implemented.
4. **Multi-manager fan-out.** Centre status notifications go to `ownerUid` (or the first manager);
   promo warnings go to the promo author. The policy for notifying every manager of a centre is not
   defined.
5. **Auth-account deletion.** `DELETED` is a profile tombstone. Deleting the Firebase Auth user would
   require an Admin SDK/Cloud Function and is not part of the current architecture.
6. **Push/pre-deletion delivery.** There is no FCM or server push. The app cannot guarantee delivery
   to a killed app or before Auth access is revoked.
7. **Warning expiration / time windows / weighted violations.** The project has no such rules; the
   counters are simple cumulative values with the reset after timeout described above.
8. **Deleting already-warned content.** The current implementation deletes it without adding another
   warning or a second notification. The project does not define whether a separate notification is
   required for that edge case.

## 13. Unverified behavior and deployment requirements

1. **Live Firebase not exercised.** No real user/centre/admin transaction was performed in this
   environment.
2. **Rules not deployed or emulator-tested.** The earlier moderation-notification rules in the
   working tree must be deployed before live warning notifications can be written. This task adds no
   new rule, but it depends on those rules.
3. **No push.** Only in-app Firestore documents, the process-alive `NotificationWatcher`, and the
   blocked-account screen are verified.
4. **Partial deletion failure.** If the content deletion batch fails after the warning transaction,
   the warning is committed and the report is resolved, but the deletion notification is not sent.
   This pre-existing split is documented in section 8.
5. **Centre cleanup is best-effort.** The role and centre document are committed atomically; deleting
   the centre's courses, posts and promos happens afterwards. A cleanup failure can leave orphaned
   content.
6. **Concurrent later warnings.** A deletion notification is written after the warning transaction.
   If another admin warns the same account in that short window, the deletion notification still
   reflects the state committed by its own action, not the later action. Each warning has its own
   notification.
7. **Multi-manager centres.** Only the promo author or the centre owner/first manager is notified.

## 14. Final implementation status

**Implemented and verified by build/unit/instrumentation tests:**

- One authoritative `ModerationPolicy` for the existing 3/3/3/24h thresholds.
- Progressive warning content for regular users, including remaining warnings before timeout.
- Timeout content, timeout ordinal, remaining timeouts before soft delete, and actual deletion
  wording.
- Separate centre promo-warning content, remaining promo warnings, and third-warning revocation.
- Account-level timeout consequences for centre accounts when the same action triggers a timeout.
- Atomic warning notification writes and deletion-batch notification writes.
- Deterministic notification IDs and the existing `warned` guard against duplicate counting.
- Blocked-account screen progress for accounts that cannot open the notification list.
- No new counters, no new Firebase dependencies, no new security weakening.

**Not fully verified or not implemented:**

- Live Firestore execution, rules deployment/emulator checks and push delivery.
- A separate per-promo counter, per-centre timeout counter, intermediate centre restriction, and
  multi-manager fan-out (not defined by the project).
- Atomicity between the warning transaction and the content-deletion batch for all target types.
- A guaranteed pre-deletion push or Auth-account deletion.

The feature is therefore **implemented and locally verified**, with the Firebase/live and undefined-
policy items explicitly listed above rather than presented as working.
