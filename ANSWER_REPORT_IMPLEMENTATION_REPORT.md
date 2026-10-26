# Answer Report Implementation Report

## 0. Scope and project instructions

This change extends the existing EngQA post-reporting flow so a signed-in user can report one
answer, not only a question or a promo. The implementation reuses the existing
`ReportDialogFragment` / `ReportViewModel` / `ReportRepository` / `reports` collection and the
existing admin moderation queue.

### Project instruction directories

There is no `kb/` directory and no `android-skills/` directory in this workspace, its parent
directory, or the global Kilo configuration. The instructions could therefore not be read from
those exact paths. The implementation instead followed:

- `README.md` (architecture, data model, build/test commands, security summary).
- `docs/EngQA_Cau_truc_du_an.md`, `docs/FIREBASE_SETUP.md`, `docs/PROMO.md`,
  `docs/TEAM_DIVISION.md`.
- The existing Java/XML source and `firestore.rules` as the source of truth.

No requirement was invented from filenames alone; every changed execution path was verified in
source first.

---

## 1. Existing implementation before the change

### 1.1 User-facing flow

Question reporting is opened from the question detail overflow menu
(`QuestionDetailActivity.showOptionsMenu`). Promo reporting is opened from
`PromoDetailActivity.reportPromo`. Both create the same `ReportDialogFragment` with a target path:

- question: `questions/{questionId}`
- promo: `promos/{promoId}`

`ReportDialogFragment` inflates `dialog_report.xml`, takes a free-text reason, rejects an empty
reason, disables the positive button while the request runs, and observes
`ReportViewModel.getReportState()`.

### 1.2 Data flow

```
Activity
  -> ReportDialogFragment.newInstance(targetPath)
  -> ReportViewModel.submit(targetPath, reason)
       -> UserRepository.currentUid()
       -> UserRepository.getUser(uid) for reporterName
       -> ReportRepository.report(new Report(...))
            -> Firestore reports/{autoId}
```

`reports/{id}` stores:

| Field | Source |
|---|---|
| `targetPath` | Firestore document path of the reported content |
| `reporterId` | signed-in Firebase Auth UID |
| `reporterName` | profile username, fallback `"user"` |
| `reason` | trimmed free-text reason |
| `status` | `pending` |
| `createdAt` | `FieldValue.serverTimestamp()` |

The existing `Report` POJO has no `targetType`, `answerId`, `questionId` or reported-author field.
The target is represented only by `targetPath`.

### 1.3 Validation and duplicate behavior

- Authentication is checked in `ReportViewModel`; unauthenticated submissions are rejected before
  any Firestore write.
- The dialog rejects empty reasons and limits input to 500 characters.
- The positive button is disabled during a request, which prevents a repeated tap from creating a
  second report while the first is in flight.
- There is **no** duplicate-report prevention in the existing post/promo flow: a user may submit
  more than one report for the same target in separate requests. No new restriction was invented.

### 1.4 Security rules

`firestore.rules` allows a signed-in client to create a report only when:

- `reporterId == request.auth.uid`
- `status == 'pending'`
- `targetPath` matches `questions/.+` or `promos/.+`
- `reason` is a string of 1..500 characters

Reads, updates and deletes are admin-only. Because the existing `questions/.+` expression also
matches a nested path such as `questions/{qid}/answers/{aid}`, **no security-rule change is
required for answer-report creation**. This existing rule was not broadened by this change.

### 1.5 Moderation

`AdminActivity` shows pending `reports` in `AdminReportAdapter`. Before this change, the adapter
labelled every non-promo report as a question, `onOpen` opened
`QuestionDetailActivity` with the substring after `questions/`, and `AdminRepository.parseTarget`
understood only `questions/{qid}` and `promos/{pid}`. Warning/deleting therefore treated every
non-promo report as a whole question.

---

## 2. Implementation summary

### 2.1 Target representation

The real answer document path is used:

```text
questions/{questionId}/answers/{answerId}
```

This reuses the existing `targetPath` field, so no migration and no new Firestore fields are needed.
A small shared utility, `ReportTargets`, builds and parses:

- `questions/{qid}` -> `QUESTION`
- `questions/{qid}/answers/{aid}` -> `ANSWER`
- `promos/{pid}` -> `PROMO`
- anything malformed -> `UNKNOWN`

The answer id and parent question id are therefore both preserved in the report record, and the
report is distinguishable from a question report without changing the schema.

### 2.2 Answer UI entry point

`AnswerAdapter` gained an optional `OnAnswerReportListener`. When a screen sets it, each answer
header shows a report icon (`ic_report`, content description "Báo cáo câu trả lời"). When no listener
is set, the icon is hidden, so the profile's "Câu trả lời của tôi" list is unchanged.

`QuestionDetailActivity` sets the listener. Clicking the icon calls
`showAnswerReportDialog(answer)`, which:

1. Rejects an unauthenticated user with the existing login message.
2. Rejects a missing/empty answer id safely.
3. Uses the current question id as the authoritative parent, with the answer's `questionId` as a
   fallback.
4. Opens the existing `ReportDialogFragment` with the answer target and the title
   "Báo cáo câu trả lời".

The dialog is still `dialog_report.xml`; no second reporting dialog was created. The post flow passes
no title and keeps the old generic "Báo cáo nội dung" title.

### 2.3 Submission validation

`ReportViewModel` now:

- Rejects a null/blank target path.
- Rejects a null/blank reason after trimming.
- Trims the reason before writing.
- Keeps a `submitting` guard so programmatic/repeated calls cannot create a second write while one
  is in flight.
- Keeps the existing loading/success/error `UiState` behavior and `RepoErrors` mapping.
- Clears old state when a new dialog opens, except while a submission is still in flight.

These checks apply to question, promo and answer reports alike and do not change their successful
behavior.

### 2.4 Moderation

- `AdminReportAdapter` labels an answer report as "Câu trả lời bị báo cáo".
- `AdminActivity.onOpen` parses the target path. For an answer it opens the parent question with
  `QuestionDetailActivity.EXTRA_FOCUS_ANSWER_ID`; the answer list scrolls to the reported answer
  once it is loaded. A missing/deleted answer is a no-op, not a crash.
- `AdminRepository.parseTarget` now resolves an answer document reference and its parent question.
- Warning an answer uses the same penalty transaction as a question/promo; the transaction reads
  the answer's real `authorId`, increments the author's warning counters, and marks the answer
  `warned`.
- Deleting a reported answer now calls the existing `AnswerRepository.delete(questionId, answerId,
  authorId)`, which removes the answer and decrements both `question.answerCount` and
  `user.answerCount`. It does **not** delete the parent question.
- Question and promo warning/deletion behavior is unchanged.

---

## 3. Files changed

| File | Change |
|---|---|
| `app/src/main/java/com/example/engqa/util/ReportTargets.java` | **New.** Builds/parses question, answer and promo target paths; single source of truth for target type. |
| `app/src/test/java/com/example/engqa/util/ReportTargetsTest.java` | **New.** Five JVM tests for round-trips and malformed paths. |
| `app/src/main/java/com/example/engqa/ui/AnswerAdapter.java` | Optional report listener + report icon binding; `indexOf(answerId)` for moderation focus. |
| `app/src/main/java/com/example/engqa/QuestionDetailActivity.java` | Sets the answer report listener, validates answer/parent ids, opens the reused dialog with the answer title, and scrolls to the reported answer when opened from moderation. |
| `app/src/main/res/layout/item_answer.xml` | Adds the per-answer report icon (hidden unless a report listener is set). |
| `app/src/main/res/values/strings.xml` | Adds `report_answer`, `report_answer_missing`, `report_target_missing`, `admin_target_answer`. |
| `app/src/main/java/com/example/engqa/ReportDialogFragment.java` | Backward-compatible optional title resource; existing callers keep the old title. |
| `app/src/main/java/com/example/engqa/viewmodel/ReportViewModel.java` | Target/reason validation, trimmed reason, in-flight guard; reuses existing repository. |
| `app/src/main/java/com/example/engqa/ui/AdminReportAdapter.java` | Distinguishes answer reports in the moderation queue. |
| `app/src/main/java/com/example/engqa/AdminActivity.java` | Opens the correct parent question and focuses the reported answer. |
| `app/src/main/java/com/example/engqa/data/AdminRepository.java` | Parses answer targets; warns/deletes the reported answer instead of its question. |
| `app/src/main/java/com/example/engqa/viewmodel/AdminViewModel.java` | Renames `deleteReportedPost` to `deleteReportedContent` to match the broader target type. |

No Gradle dependency was added. No unrelated screen, layout or feature was changed.

---

## 4. Data model

An answer report is stored in the existing `reports` collection:

```text
reports/{autoId}
  targetPath:   "questions/{questionId}/answers/{answerId}"
  reporterId:   "{signed-in Firebase Auth uid}"
  reporterName: "{reporter username}"
  reason:       "{free-text reason}"
  status:       "pending"
  createdAt:    <server timestamp>
```

Existing records remain valid:

```text
questions/{qid}   -> QUESTION report (unchanged)
promos/{pid}      -> PROMO report (unchanged)
```

Association details:

- **Report id**: Firestore auto id / `@DocumentId`.
- **Reporter id**: `reporterId`, forced to `request.auth.uid` by the rules.
- **Target type**: derived by `ReportTargets.parse(targetPath)`.
- **Reported answer id**: parsed from the third path segment.
- **Parent question id**: parsed from the second path segment.
- **Reported author id**: intentionally not stored from the client. `AdminRepository` reads the
  answer document's real `authorId` inside the moderation transaction. This avoids trusting
  client-supplied reported-author metadata and avoids confusing the answer author with
  `reporterId`.
- **Reason / additional explanation**: the existing single free-text `reason` field is used. The
  project has no separate "additional details" field, so none was invented.
- **Status and timestamp**: `pending`, `createdAt` as before.

Example:

```json
{
  "targetPath": "questions/8fA.../answers/2kL...",
  "reporterId": "uid-of-reporter",
  "reporterName": "minh_anh",
  "reason": "Câu trả lời có nội dung xúc phạm.",
  "status": "pending",
  "createdAt": "<server timestamp>"
}
```

---

## 5. Security

### Authentication

- `ReportViewModel` rejects a report when Firebase Auth has no current user.
- The answer report icon remains visible to signed-out users, matching the existing post-report
  behavior; clicking it shows the existing login message and does not write anything.

### Firestore rules

No rule change was necessary or made:

- `targetPath.matches('questions/.+')` already accepts
  `questions/{qid}/answers/{aid}` because `.+` matches slashes.
- The reporter id is still forced to `request.auth.uid`.
- `status` is still forced to `pending`, and the reason length is still 1..500.
- Reports remain readable/updatable/deletable by admins only.

The existing rule is broader than an exact answer pattern, but this change does **not** widen it.
A future hardening could replace `questions/.+` with exact `questions/{qid}` and
`questions/{qid}/answers/{aid}` patterns. That would be a separate rule change and requires
explicit authorization and deployment.

### Trust boundary

The report document never stores a client-supplied reported-author id. The moderation transaction
reads the answer document from Firestore and uses its `authorId`; a forged report cannot redirect a
warning to a different account. The target path is resolved to a real document reference, and a
missing target fails the transaction safely.

---

## 6. Backward compatibility

- Existing `questions/{qid}` and `promos/{pid}` report records parse exactly as before.
- Existing post/promo report UI callers still use the one-argument
  `ReportDialogFragment.newInstance`, so their title and flow are unchanged.
- No Firestore migration, field rename or deletion is performed.
- Admin actions for question and promo reports follow the same branches as before.
- Existing `Answer` documents are unchanged. A warning adds the same `warned` / `warnedAt` fields
  already used for questions; `Answer` uses `@IgnoreExtraProperties`.
- The profile answer list does not show the new report icon because it does not set the listener.

---

## 7. Testing

### 7.1 Commands run

```powershell
.\gradlew.bat clean testDebugUnitTest --console=plain --no-daemon --no-configuration-cache
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug --console=plain --no-daemon --no-configuration-cache
.\gradlew.bat lintDebug --console=plain --no-daemon --no-configuration-cache
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain --no-daemon --no-configuration-cache
.\gradlew.bat connectedDebugAndroidTest --console=plain --no-daemon --no-configuration-cache
```

### 7.2 Results

| Check | Result | Evidence |
|---|---|---|
| JVM unit tests | **PASS** | 74 tests, 0 failures, 0 errors. Includes 5 new `ReportTargetsTest` tests. |
| `assembleDebug` | **PASS** | Debug APK built. |
| `assembleDebugAndroidTest` / connected build | **PASS** | Test APK built and installed by `connectedDebugAndroidTest`. |
| `lintDebug` | **PASS** | Build successful after replacing `android:tint` with `app:tint`. 285 pre-existing warnings; no lint errors. |
| Instrumented tests | **PASS** | 9/9 tests passed on `Pixel_4(AVD) - 10` (Android 10 / API 29). |

One lint reporting quirk occurred: the SARIF reporter could not compute a quick-fix edit for the
pre-existing typo `YSdhyju1` in `strings.xml`. It is a reporter-side warning, not a lint error; the
`lintDebug` task completed successfully.

### 7.3 Scenario matrix

Statuses use **PASS** for verified behavior, **NOT RUN** for runtime checks that require an
authenticated Firebase session/admin account, and **BLOCKED** where verification requires external
services or authorization.

| # | Scenario | Status | Notes |
|---|---|---|---|
| 1 | Reporting a post still works as before | PASS (code/build) / NOT RUN (live submission) | Post routing, dialog, repository and rules are unchanged; shared validation is additive. |
| 2 | Answer report submits the correct target type | PASS (unit) / NOT RUN (live) | `ReportTargets` parses the answer path as `ANSWER`. |
| 3 | Correct answer id is stored/associated | PASS (unit) / NOT RUN (live) | `questions/{qid}/answers/{aid}` round-trip test. |
| 4 | Correct parent question id is associated | PASS (unit) / NOT RUN (live) | Same test asserts `q1` and `a1`. |
| 5 | Author id is not confused with reporter id | PASS (static) | Report writes only `reporterId`; moderator reads the answer's real `authorId`. |
| 6 | Reason/explanation preserved | PASS (code) / NOT RUN (live) | Reason is trimmed, validated and written to the existing `reason` field. No separate details field exists. |
| 7 | Unauthenticated users cannot bypass restrictions | PASS (static) | `ReportViewModel` blocks before writing; rules require `request.auth`. Live rules test NOT RUN. |
| 8 | Invalid answer identifiers handled safely | PASS (code) | Null/empty answer id and missing parent are rejected with Vietnamese messages; no crash. |
| 9 | Duplicate submissions follow established rules | PASS (static) | Existing post flow has no dedupe; answer flow is consistent. In-flight repeats are blocked. |
| 10 | Network/Firebase permission errors handled | PASS (code) / NOT RUN (live) | Existing `RepoErrors.message` path is reused; no exception is swallowed. |
| 11 | Repeated taps do not create unintended duplicates | PASS (code) | Button disabled + `submitting` guard. |
| 12 | Existing report records remain readable | PASS (unit/static) | Parser only adds a nesting case; two-segment question/promo paths still parse. |
| 13 | Existing answer interactions remain functional | PASS (build/instrumented regression) / NOT RUN (manual answer screen) | Votes, accept, profile list, and build tests pass. |
| 14 | Moderators can identify answer reports | PASS (code/build) / NOT RUN (live admin console) | Label "Câu trả lời bị báo cáo", opens parent and focuses answer; warn/delete target the answer. |

### 7.4 Not run

- Actual Firestore writes for a post or answer report.
- Live Firebase permission-denied / offline behavior.
- Live admin warning/deletion of a reported answer.
- Manual visual inspection of the answer report icon and dialog on a device.
- Rules Playground / deployed-rules verification.

No production data was modified. No Firebase rules, indexes or Cloud Functions were deployed.

---

## 8. Unverified areas

1. **Firebase end-to-end submission**: build and unit tests prove path construction and parsing, but
   no authenticated Firebase session was used to create a real `reports` document.
2. **Deployed rules**: the repository `firestore.rules` already permits the answer path; whether the
   live Firebase project has exactly this rules file was not checked or changed.
3. **Admin runtime actions**: `warnReport` / `deleteReportedContent` for an answer were not executed
   against a live admin account.
4. **Device UI**: the new icon and dialog were compiled and linted but not manually tapped through
   in a signed-in session.
5. **Unrelated instrumented coverage**: 9 existing tests passed; none specifically exercise the
   answer-report flow.

---

## 9. Known limitations

- **No duplicate-report prevention** exists for posts, promos or answers. A user can submit another
  report in a later request; only an in-flight double tap is prevented. This matches the existing
  product behavior rather than adding a new restriction.
- **Target type is derived, not stored.** `targetPath` remains the single source of truth. Any
  external backend that expects explicit `targetType`, `answerId` or `parentId` fields would need a
  follow-up schema extension. This was intentionally avoided to keep the report schema
  backward-compatible.
- **No reason picker**: the existing free-text reason dialog is reused; no new reason taxonomy was
  invented.
- **Answer deletion does not cascade the `votes` subcollection.** This is the pre-existing behavior
  of `AnswerRepository.delete`; the admin path reuses it rather than introducing a new deletion
  implementation.
- **Existing rule breadth**: `questions/.+` also accepts deeper `questions/...` strings. Answer
  reports do not widen this, but an exact-pattern hardening remains a possible follow-up.
- **Deleted target**: warning/deleting a report whose target no longer exists fails with the
  existing "Nội dung không còn tồn tại" error instead of silently succeeding.

---

## 10. Final assessment

**Partially verified.**

The feature is implemented at the code level and verified by:

- successful compilation and APK/test-APK builds,
- 74 passing JVM unit tests (including 5 new target-path tests),
- successful `lintDebug`,
- 9/9 passing instrumented regression tests on an Android 10 emulator.

Answer reports use the existing report dialog, view model, repository and `reports` schema; the
answer id and parent question id are carried in `targetPath`; the admin queue can distinguish and
act on an answer instead of the whole question; and existing post/promo reports remain compatible.

It is **not fully verified end-to-end** because no authenticated Firebase submission, live admin
action, or deployed-rules check was performed. Those items are listed in section 8 and require
credentials/authorization rather than code changes.
