# Moderation Action Notification Report

> **Superseded for progressive warning counts by
> [`PROGRESSIVE_MODERATION_AND_WARNING_REPORT.md`](PROGRESSIVE_MODERATION_AND_WARNING_REPORT.md).**
> This report remains the history of the earlier moderation-notification integration.

## 0. Scope and project instructions

This change makes successful administrator moderation actions create an in-app notification for the
affected account: a regular user, an English center account, or the author of a reported
question/answer/promo.

There is no `kb/` directory and no `android-skills/` directory in this workspace, its parent
directory, or the global Kilo configuration. The implementation therefore follows:
`README.md`, `docs/EngQA_Cau_truc_du_an.md`, `docs/FIREBASE_SETUP.md`, `docs/PROMO.md`,
`docs/TEAM_DIVISION.md`, the existing Java/XML source, and `firestore.rules` as the source of truth.

No notification collection, field name, account identifier, or moderation action was assumed
without checking its actual usage in the project.

---

## 1. Existing report and notification architecture

### 1.1 Reports and moderation actions

Reports are stored in `reports/{id}` with `targetPath`, `reporterId`, `reporterName`, `reason`,
`status` and `createdAt`. The target path is:
- `questions/{qid}` for a question,
- `questions/{qid}/answers/{aid}` for an answer (added by the preceding answer-report work),
- `promos/{pid}` for a center promo.

`AdminRepository` is the only moderation back-office data layer. The actions that actually change
another account/content are:

| Action | Target | Effect |
|---|---|---|
| `warnReport` | reported question/answer/promo | warning counter, possible timeout/account status change, target `warned` flag, report resolved, audit |
| `deleteReportedContent` | reported question/answer/promo | same warning transaction, then deletion of the target (deleting a question also removes its answers/votes/media) |
| `dismissReport` | report | report dismissed, no penalty |
| `approveCenter` | pending center | center approved, owner promoted to `CENTER`, audit |
| `rejectCenter` | pending center | center rejected with review note, audit |
| `approvePromo` | pending promo | promo approved, audit |
| `rejectPromo` | pending promo | promo rejected with review note, audit |

There is no admin action for suspending/disabling a center. `Center.STATUS_SUSPENDED` exists as a
constant but is not used by any admin workflow, so no notification was invented for it. There is no
separate "comment" collection; comments in this project are answers.

### 1.2 Existing notifications

Notifications are Firestore documents in `notifications/{id}` addressed to one user UID:

```text
recipientId, actorId, actorName, type, questionId, answerId,
title, body, read, createdAt
```

Existing types are `answer`, `vote` and `accepted`. `NotificationRepository` writes them and
`NotificationViewModel` observes `recipientId == currentUid` ordered by `createdAt`. The notification
screen, unread badge, mark-read and mark-all-read behavior already existed.

`NotificationWatcher` shows a local system notification for newly added docs while the app process is
alive; there is **no Firebase Cloud Messaging / server push** implementation in this project.

### 1.3 Users and English centers

Both regular users and center accounts are Firebase Auth users in `users/{uid}`:

- regular user: role `USER`;
- center account: role `CENTER` with `users/{uid}.centerId`;
- center document: `centers/{centerId}` with `ownerUid` and `managerUids`.

Notifications are read by the signed-in UID, so a center's notification recipient must be an
authenticated user UID, not the center document ID. This implementation uses:
- center owner UID (fallback: first manager UID) for center status decisions;
- content author UID (`authorId` on question/answer, `authorUid` on promo) for content penalties.

### 1.4 Existing notification rules

Before this change, `notifications` create allowed only `answer`, `vote`, `accepted`, required a
non-empty `questionId`, and allowed any signed-in actor to write to another user. Moderation
notifications cannot satisfy that rule for centers/promos (no question), and adding a moderation
type without an admin check would let ordinary users fake disciplinary notifications.

---

## 2. Changes implemented

### 2.1 Notification model

`AppNotification` now has optional navigation/audit fields and new moderation types:

- `reportId` — report that produced a warning/deletion;
- `centerId` — center involved in a center/promo notification;
- `promoId` — promo involved in a promo notification;
- types: `moderation_warning`, `moderation_deleted`, `center_approved`, `center_rejected`,
  `promo_approved`, `promo_rejected`.

Existing fields and types are unchanged; old documents remain readable because the model already uses
`@IgnoreExtraProperties` and the new fields are optional.

### 2.2 Notification repository

`NotificationRepository` now exposes one shared payload builder used by:
- the existing standalone `create` path (answer/vote/accepted);
- `addToBatch(...)` for moderation writes that must commit with a batch;
- `set(Transaction, ...)` for warning writes that must commit with the penalty transaction.

Optional IDs are omitted instead of written as `null`. Existing interaction notifications still
include `questionId`; center/promo moderation notifications do not need a fake question id.

### 2.3 Notification content

`ModerationNotifications` builds Vietnamese title/body text from the trusted target document and the
report reason:
- regular user: "Bạn đã nhận được cảnh cáo..." / "Nội dung của bạn đã bị quản trị viên xoá...";
- center content: "Trung tâm của bạn đã nhận được cảnh cáo..." / "Nội dung của trung tâm...";
- center approval/rejection and promo approval/rejection have dedicated titles and bodies.

Titles are clipped to 120 characters and bodies to 300 characters, matching the existing rules.
Missing content/reason is replaced by a neutral Vietnamese fallback; literal `null` is never shown.
When the third promo warning removes the center, the warning body adds "Trung tâm của bạn đã bị gỡ
do vi phạm quy định."

### 2.4 Atomic moderation writes

Notification creation is attached to the actual action, not to a button click:

| Action | Notification write location |
|---|---|
| Warning | Same Firestore transaction as warning counters, target `warned`, report resolution and audit |
| Deletion | Same `WriteBatch` as the actual question/answer/promo deletion |
| Center approve/reject | Same batch as center status/user role update and audit |
| Promo approve/reject | Same batch as promo status update and audit |

The existing question/answer deletion repositories gained optional notification overloads so the
notification is committed together with the content deletion. Self-delete callers continue to use
the old overloads and create no moderation notification.

### 2.5 Notification delivery and navigation

- The existing notification screen displays the new docs automatically (same title/body/time/unread
  layout).
- Tapping a question/answer notification opens the question; an answer notification also focuses the
  answer when it still exists.
- Promo notifications open the promo; center notifications open the center.
- Deleted content notifications avoid dead navigation where the target is gone: a deleted question
  has no target, a deleted answer opens its parent question, a deleted promo opens its center (unless
  the center was also removed).
- The existing unread badge/mark-read behavior needs no change because new docs are `read: false`.
- Local system notifications are routed by target type; the notification id is now derived from the
  Firestore document id instead of a timestamp, so repeated listener callbacks cannot create
  duplicate system notifications.

---

## 3. Recipient identification

| Event | Recipient | Why |
|---|---|---|
| Question warning/deletion | `questions/{qid}.authorId` | content owner |
| Answer warning/deletion | `answers/{aid}.authorId` | answer owner; never the question author |
| Promo warning/deletion | `promos/{pid}.authorUid` | authenticated center account that created/owns the promo |
| Center approved/rejected | `centers/{cid}.ownerUid`, fallback first `managerUids` | authenticated account managing the center |
| Promo approved/rejected | `promos/{pid}.authorUid` | center account that owns the promo |
| Reporter | never notified as the disciplined account | reporter is only `reporterId` on the report |
| Admin/actor | skipped by `recipientId != actorId` | never notify yourself |

Center documents use their own ID for `centerId` navigation only; they are never used as a
notification recipient ID. Center content notifications include `centerId` so the notification stays
associated with the correct center even though delivery is per user UID.

---

## 4. Notification behavior by action

### 4.1 Warning

- Type: `moderation_warning`.
- Recipient: the content author.
- Title: "Cảnh cáo từ quản trị viên".
- Body: warning statement + content identifier (question title / answer snippet / promo title) +
  report reason fallback.
- Center authors/promos use the center-specific wording.
- For a third promo warning that removes the center, the body also says the center was removed.
- A duplicate warning on already-warned content closes the report but creates no new notification.

### 4.2 Deletion

- Type: `moderation_deleted`.
- Recipient: the owner of the deleted question/answer/promo only.
- Title: "Nội dung đã bị xoá".
- Body: deletion statement + content identifier + reason fallback.
- The notification is in the same batch as the deletion; a failed deletion creates no notification.
- A deletion also counts as a warning in the existing transaction, but only one deletion notification
  is sent (no duplicate warning notification).

### 4.3 Center and promo decisions

- Center approval: `center_approved`, recipient owner, center name, opens center.
- Center rejection: `center_rejected`, recipient owner, center name + review note.
- Promo approval: `promo_approved`, recipient promo author, promo title, opens promo.
- Promo rejection: `promo_rejected`, recipient promo author, promo title + review note.
- These notifications are in the same batch as the status change.

### 4.4 Dismiss / review only

`dismissReport` is unchanged and creates no notification. Opening or reviewing a report creates no
notification. There is no "warning" notification unless the warning transaction succeeds.

---

## 5. Duplicate prevention and failure handling

### 5.1 Deterministic notification IDs

`NotificationIds` creates stable Firestore document IDs:

```text
moderation_{warning|deleted}_{reportId}_{recipientId}
moderation_center_{approved|rejected}_{centerId}_{recipientId}
moderation_promo_{approved|rejected}_{promoId}_{recipientId}
```

- Retrying the same report/action/recipient or center/promo/outcome/recipient is an idempotent
  `set`, not a second document.
- Different reports, actions, outcomes, or recipients produce different IDs, so legitimate separate
  events are not collapsed.
- A second warning is blocked earlier by the existing `warned` flag; it creates no notification.

### 5.2 Atomicity

- Warning + notification + report resolution + warning counters are one transaction.
- Deletion + notification is one batch.
- Center/promo status + notification + audit is one batch.
- A failed transaction/batch commits neither the penalty nor its success notification.

### 5.3 Residual partial-failure case

Deletion still spans two operations by design: the warning/report transaction commits first, then the
content deletion batch runs. If the second operation fails, the content remains but the report is
already resolved and warned; no deletion notification is sent (the batch did not commit). This was
already true of the pre-existing moderation flow and is documented rather than hidden. Center cleanup
after a revoked promo author is also best-effort, as before.

---

## 6. Firebase and security changes

`firestore.rules` was updated only for notification creation:

- Existing `answer` / `vote` / `accepted` behavior is preserved and still requires a non-empty
  `questionId`.
- New moderation types are allowed only when `isAdmin()` is true.
- `actorId` must equal `request.auth.uid`.
- `recipientId` must differ from the actor.
- `title`/`body` length limits and `read == false` are preserved.
- Optional `questionId`, `answerId`, `reportId`, `centerId`, `promoId` are validated when present.
- Recipients can still read/delete only their own notification documents. Updates are now limited to
  the `read` field, so a recipient cannot rewrite the type, body, actor or recipient of a notification
  to spoof a moderation decision onto another account. The app only ever updates `read`.

This is a required rule change. Because notification writes are atomic with the moderation action,
**the updated rules must be deployed before these admin actions will succeed with the new build**. No
rules, Cloud Functions, or production data were deployed from this environment.

No Cloud Functions, FCM credentials, or server-side service accounts were added. Notifications are
in-app Firestore documents plus the app's existing local system-notification watcher.

---

## 7. Files changed

| File | Reason |
|---|---|
| `app/src/main/java/com/example/engqa/model/AppNotification.java` | New optional IDs and moderation types. |
| `app/src/main/java/com/example/engqa/data/NotificationRepository.java` | Shared payload, batch/transaction writers, omit null optional fields. |
| `app/src/main/java/com/example/engqa/notification/ModerationNotifications.java` | **New.** Builds Vietnamese moderation notification content, recipients and IDs. |
| `app/src/main/java/com/example/engqa/util/NotificationIds.java` | **New.** Deterministic idempotency keys. |
| `app/src/main/java/com/example/engqa/data/AdminRepository.java` | Writes notifications atomically with warnings, deletions, center and promo decisions. |
| `app/src/main/java/com/example/engqa/data/AnswerRepository.java` | Optional notification overload in the answer-deletion batch. |
| `app/src/main/java/com/example/engqa/data/QuestionRepository.java` | Optional notification in the final question-deletion batch. |
| `app/src/main/java/com/example/engqa/notification/NotificationHelper.java` | Routes local system notifications to question/promo/center/notification list. |
| `app/src/main/java/com/example/engqa/notification/NotificationWatcher.java` | Stable system-notification id from the Firestore doc id. |
| `app/src/main/java/com/example/engqa/NotificationActivity.java` | Opens the related question/answer/promo/center from a notification. |
| `app/src/main/java/com/example/engqa/ui/NotificationAdapter.java` | Documentation comment update only. |
| `app/src/main/res/values/strings.xml` | Vietnamese moderation notification strings; channel/settings wording. |
| `firestore.rules` | Admin-only moderation notification types, optional target IDs. |
| `app/src/test/java/com/example/engqa/util/NotificationIdsTest.java` | **New.** 4 JVM tests for deterministic IDs. |
| `app/src/androidTest/java/com/example/engqa/ModerationNotificationsTest.java` | **New.** 6 instrumentation tests for recipients, wording, IDs and serialized fields. |

No new Gradle dependency was added. The working tree also contains the earlier answer-report feature;
the files above are the ones changed by this moderation-notification task.

---

## 8. Testing

### 8.1 Commands run

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain --no-daemon --no-configuration-cache
.\gradlew.bat connectedDebugAndroidTest --console=plain --no-daemon --no-configuration-cache
.\gradlew.bat lintDebug --console=plain --no-daemon --no-configuration-cache
```

### 8.2 Results

| Check | Result | Evidence |
|---|---|---|
| JVM unit tests | **PASS** | 78 tests, 0 failures, 0 errors; includes 4 new `NotificationIdsTest` tests. |
| `assembleDebug` | **PASS** | Debug APK built. |
| `assembleDebugAndroidTest` | **PASS** | Test APK built. |
| `lintDebug` | **PASS** | 0 errors, 285 pre-existing warnings; no new lint errors. |
| Instrumented tests | **PASS** | 15/15 on `Pixel_4(AVD) - 10` (Android 10 / API 29); 6 new moderation notification tests. |

The lint task still prints the pre-existing typo quick-fix warning for `YSdhyju1` in `strings.xml`;
the task itself completes successfully with 0 errors.

### 8.3 Scenario matrix

“PASS (code/test)” means verified by source inspection, compilation and/or the listed automated test;
live Firebase execution is explicitly called out separately.

| # | Scenario | Status | Evidence |
|---|---|---|---|
| 1 | User warned for a reported post | PASS (code/test) / NOT RUN live | Question warning recipient/content covered by `ModerationNotifications` tests and `AdminRepository` transaction. |
| 2 | User notified when their post is deleted | PASS (code) / NOT RUN live | Question-deletion batch includes notification; deletion wording tested. |
| 3 | User warned for a reported answer | PASS (code) / NOT RUN live | Answer author is the transaction recipient; answer deletion test verifies owner/IDs. |
| 4 | User notified when their answer is deleted | PASS (test) | `deletedAnswer_goesToAnswerAuthorWithParentQuestion`. |
| 5 | Center warned | PASS (test) | `centerWarning_usesCenterWordingAndRevocationSuffix`. |
| 6 | Center notified when its content is deleted | PASS (test) | `deletedCenterContent_usesDeletionWording`. |
| 7 | Other supported actions notify | PASS (test) | Center approval/rejection owner test, promo approval author/center serialization test. |
| 8 | Dismiss without penalty creates no notification | PASS (static) | `dismissReport` unchanged and has no notification write. |
| 9 | Failed action creates no success notification | PASS (static) | Notification is in the same transaction/batch as the action. |
| 10 | Reporter is not the disciplined account | PASS (test) | Recipient is always the target author/owner; reportId is separate. |
| 11 | Answer author is not confused with parent question author | PASS (test) | Answer deletion test uses a distinct answer author and parent question id. |
| 12 | Correct center receives the notification | PASS (test) | Owner is chosen over manager; promo carries `centerId`. |
| 13 | Repeated submissions do not duplicate | PASS (test) | `NotificationIdsTest` verifies deterministic IDs and event separation. |
| 14 | Existing read/unread behavior works | PASS (static) / NOT RUN live | New docs are `read: false`; mark-read code unchanged. |
| 15 | Existing reporting/answer/moderation behavior | PASS (build/regression) / NOT RUN live | Full build and 15 instrumented tests pass; live E2E not run. |
| 16 | Unauthorized users cannot moderate or read others' notifications | PASS (static) / NOT RUN rules emulator | Moderation types require `isAdmin()`; read/update require `recipientId == auth.uid`. |
| 17 | Missing content/network failures handled safely | PASS (code) / NOT RUN live | Missing target throws the existing safe error before any notification; no success notification is created. |

### 8.4 Not run

- Real Firestore writes/reads for a warning, deletion, center decision or promo decision.
- Firebase Security Rules emulator or production deployment verification.
- Manual UI inspection of the notification list/rows with a real admin and recipient.
- Multi-manager center scenario in seeded data.
- FCM/push while the app process is killed (the project has no FCM implementation).

No production data or Firebase configuration was modified.

---

## 9. Unverified areas and limitations

1. **Rules deployment is required and unverified in this environment.** Until the updated
   `firestore.rules` is deployed, moderation actions that include the new notification writes will
   fail atomically under the old rules. This is the most important follow-up.
2. **No live end-to-end test** of a successful moderation action creating a notification was
   performed because no admin/recipient credentials were used and production writes were avoided.
3. **In-app only.** Notifications are delivered through the existing Firestore listener and local
   system notification. There is no FCM push, so a killed app will not receive a push.
4. **Multi-manager centers are not fanned out.** Center status notifications go to `ownerUid` (or the
   first manager); content notifications go to the content author UID. The current app creates one
   owner/manager per center. A future multi-manager feature would need an explicit fan-out policy.
5. **Question deletion cascade** notifies only the question owner. Answer authors whose answers are
   removed by the cascade are not individually notified; this avoids extra notifications and matches
   the existing deletion call's single-owner model.
6. **Deletion partial failure** remains as described in section 5.3: the warning/report transaction is
   separate from the content-deletion batch.
7. **Center revocation corner case** on the third promo warning removes the center and its promos, so
   the notification intentionally carries no promo/center navigation target.
8. **No suspension/disable action** exists in the admin UI, so no such notification was implemented.
9. **Missing recipient identifiers** (legacy center without `ownerUid`/managers, or promo without
   `authorUid`) make the notification write skip while the moderation action still proceeds. Current
   creation flows always set these fields, but legacy/malformed documents would not be notified.

---

## 10. Deployment requirements

1. Deploy the updated `firestore.rules` (required before the new moderation notification writes can
   succeed).
2. Deploy the updated Android build.
3. No Cloud Functions, FCM, indexes, or additional Firebase configuration are required for the
   in-app notification path.
4. Deploying rules/functions was **not** performed here; it requires explicit authorization.

---

## 11. Final assessment

**Partially verified.**

Implemented and automated-test verified:
- warning notifications for questions, answers and promos;
- deletion notifications for questions, answers and promos;
- center approval/rejection and promo approval/rejection notifications;
- correct user, answer-author and center-owner recipient selection;
- reporter/actor separation;
- deterministic duplicate prevention;
- atomic warning/deletion/decision notification writes;
- notification display, navigation and unread behavior integration;
- admin-only security rules for moderation notification types.

Build, 78 JVM tests, 15/15 instrumented tests and lint all pass. The feature is **not fully verified
end-to-end** until the updated Firebase rules are deployed and a real admin/recipient flow is
exercised. No deployment or production data change was made.
