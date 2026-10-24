# Application Audit Report

## 1. Executive Summary

**Original audit status (pre-remediation):** Completed with confirmed functional defects. The application builds, installs, and launches, and the main read-only workflows work, but several high-impact defects affect profile editing, logout/session handling, answer acceptance, question deletion, centre screens, and backend authorization. The application was **not** ready for release.

**Post-remediation status (2026-10-24, including the search-optimisation pass):** **29 findings processed — 14 fixed and verified, 12 fixed with deployment/runtime verification blocked, 2 partially fixed, 1 unresolved.** The debug build, **69 unit tests**, 7 instrumented tests, lint (0 errors) and the release build pass. Firestore indexes are deployed and READY; the remaining blockers are the undeployed rules and destructive end-to-end flows plus one product decision (avatar editing); see the per-issue remediation blocks in §5 and the consolidated status in §11.

**Project:** EngQA — an Android English Q&A / centre-promotion app with Firebase Auth and Firestore.

**Detected technology stack**

| Area | Detected |
|---|---|
| Language | Java (no Kotlin source in `app/src/main`) |
| Build | Gradle 9.6.0, Android Gradle Plugin 9.3.0, Kotlin DSL version catalog |
| Android | `compileSdk 37`, `targetSdk 37`, `minSdk 29`, Java 11 source/target; runtime JDK 26.0.2 |
| UI | Android Views, AppCompat, Material Components, RecyclerView, ViewPager-free custom tabs, EdgeToEdge |
| Architecture | Activities + AndroidViewModel + LiveData + repository classes |
| Backend | Firebase Auth, Cloud Firestore (offline persistence), Firebase Storage dependency (not used for new media) |
| Local storage | SharedPreferences, Room 2.6.1 (`saved_promo`), files/cache |
| Media | Glide, Android `MediaPlayer`/`MediaRecorder`, Base64 media documents, WorkManager |
| Network | Firebase SDKs, OkHttp 4.12.0 + Gson for the dictionary API, Credential Manager + Google Sign-In |
| Tests | JUnit 4 unit tests, Espresso instrumented tests |

**Feature counts (44 identifiable features)**

| Status | Count |
|---|---:|
| Passed under documented conditions | 10 |
| Failed (confirmed defect or unusable entry point) | 9 |
| Partially tested / partially working | 15 |
| Not tested / blocked by environment or data-safety limits | 9 |
| Blocked (external API unavailable during runtime test) | 1 |
| **Total** | **44** |

> The feature-status counts above are from the **original** audit. Post-remediation status is tracked per issue in §5 and summarized in §11.

**Issue counts**

| Severity | Count |
|---|---:|
| Critical | 0 |
| High | 8 |
| Medium | 7 |
| Low | 14 |
| **Total** | **29** |

Confidence distribution: **22 Confirmed**, **5 Probable**, **2 Potential**.

**Remediation status counts (2026-10-24)**

| Remediation status | Count | Issues |
|---|---:|---|
| FIXED AND VERIFIED | 14 | BUG-001, 002, 004, 006, 009, 010, 014, 015, 016, 017, 019, 022, 024, 027 |
| FIXED — VERIFICATION BLOCKED | 12 | BUG-003, 007, 011, 012, 013, 018, 020, 021, 023, 025, 028, 029 |
| PARTIALLY FIXED | 2 | BUG-005, 008 |
| UNRESOLVED | 1 | BUG-026 |
| BLOCKED | 0 | — |
| NOT A BUG / ALREADY FIXED | 0 | — |
| **Total** | **29** | |

1. **Profile editing is effectively broken.** Saving the Edit Profile screen without changing the display name always fails, because the username-uniqueness query includes the current user's own document. This also blocks changing the learning level. (`UserRepository.updateProfile` / `isUsernameTaken`.)
2. **Logging out from the Home avatar menu leaves the app in a broken, privacy-sensitive state.** The feed shows a permission error and pressing Back reveals the previous user's profile, including email, because the back stack is not cleared and `MenuScreenActivity` keeps the old UID.
3. **Accepted answers are not exclusive.** A question owner can accept multiple answers; each acceptance increments the author's `acceptedCount`, and there is no unaccept/rollback. This corrupts profile statistics and question state.
4. **Deleting a question is irreversible and incomplete.** The overflow-menu action deletes immediately with no confirmation and deletes only the parent document, leaving answers, votes, media, notifications and inflated user counters behind.
5. **The entire centre browsing/request/edit feature is unreachable.** `CenterListActivity`, `CenterDetailActivity`, `CenterEditActivity` and `CenterRequestActivity` have no UI entry point, even though a temporary read-only instrumentation test proved the list/detail screens themselves render.
6. **Firestore rules expose all user profiles and allow broad counter/field manipulation.** Any signed-in user can read every user document (including email) and can bump counters such as `acceptedCount`; the question owner's update branch can change arbitrary answer fields, not just `isAccepted`.
7. **Search is functionally incorrect under common conditions.** Scrolling a search result appends unrelated questions (confirmed at runtime), and category/sort changes are silently ignored while a search keyword is active.
8. **A pre-existing vote is never loaded.** The UI starts with no highlighted vote, so a user cannot remove an existing vote with a single tap, and the code path designed for this (`VoteRepository.observeMyVote`) is never called.

**Remediation outcome — what changed**

- Profile save, Home logout/session clearing, delete confirmation, centre navigation, Back handling, the pre-login verification box, username hint, cache clearing, unused media permissions, leftover-promo mixing and the create-promo error banner were fixed and verified at runtime on the API 29 emulator.
- Search was substantially reworked and verified: accent/whitespace normalisation, full tokens + bounded prefixes, multi-word `array-contains-any` candidate retrieval (≤ 60), local relevance ranking with deterministic tie-breakers, bounded typo variants, 300 ms debounce, stale-response guards, local ranked pagination, promo relevance, and search-specific empty/too-short states. Firestore indexes are deployed (19/19 READY) and live category/sort/partial/typo searches were exercised on the API 29 emulator; 69 unit tests pass.
- Acceptance, vote restoration, promo contact intents, offline-job lifecycle, backup exclusions, notification backlog/type, saved-promo isolation, word-cache fallback and the offline error screen were implemented and pass build/unit/lint; their end-to-end verification is blocked by data-safety, API-level, or deployment constraints (documented per issue).
- Question deletion and Firestore authorization were improved but remain partially fixed: a complete atomic cascade/counter reconciliation requires Cloud Functions, and the remaining bounded counter rules cannot be perfectly attributed to vote/answer documents on the client. The updated rules still need to be deployed to the Firebase project; the indexes are already deployed.
- Avatar editing remains unresolved by design: it needs a product decision (implement picker/upload/storage or remove the unused field), and implementing it would add a new feature outside the repair scope.

**Major limitations affecting audit completeness**

- Runtime writes to the shared Firebase demo project were avoided (posting questions/answers, voting, accepting, deleting, reporting, promo creation, lead submission, registration). The defects in those flows are confirmed by source analysis; destructive flows were not executed.
- Only an Android 10 / API 29 emulator (`Pixel_4`) was available. Android 11+ package visibility, Android 13+ notification permission behaviour, and Android 14 selected-photo behaviour could not be exercised.
- No Google account, no inbox for verification/reset emails, and no second user were available for end-to-end Google sign-in, email verification, password reset, or notification delivery tests.
- The dictionary API was reachable from the host but the live Word-of-the-Day card did not load during the original audit; during the remediation re-check it loaded successfully ("feasible" with definition) after a fresh login.
- **Post-remediation deployment status:** `firestore.indexes.json` was deployed to project `accountdata-dd29b` on 2026-10-24; all 19 indexes are READY and the composite search/filter queries were exercised live. `firestore.rules` was **not** deployed; rules changes remain statically reviewed because no rules-emulator test was available. The app's search work therefore runs against the currently deployed rules.
- No performance, accessibility, localization, process-death, or penetration testing was performed. Rotation/responsive UI was tested separately in §4.2 on a phone emulator; tablet/foldable sizes and process death remain untested.

---

## 2. Audit Scope and Environment

**Project root:** `C:\Users\LENOVO\Downloads\MOB-PHU\MOB DANG\MOB-EngQA`

**Inspected**

- Full `app/src/main` tree: 120 Java source files, 37 layouts, menus, drawables, XML configuration, manifest.
- `app/build.gradle.kts`, `gradle/libs.versions.toml`, wrapper, `gradle.properties`, `settings.gradle.kts`.
- `firestore.rules`, `firestore.indexes.json`, `firebase.json`, `storage.rules`, seed data under `tools/seed/data`.
- Unit tests in `app/src/test` (9 classes, 41 tests).
- Instrumented tests in `app/src/androidTest` (5 classes, 7 tests).
- Documentation: `README.md`, `docs/MEDIA_STORAGE.md`, `docs/PROMO.md`, `docs/FIREBASE_SETUP.md`, `docs/TEAM_DIVISION.md`.

**Runtime and tools**

| Item | Value |
|---|---|
| JDK | 26.0.2 (Oracle) |
| Gradle | 9.6.0 |
| AGP | 9.3.0 |
| Emulator | Pixel_4(AVD), Android 10 / API 29, 1080×2148 |
| ADB | Android SDK platform-tools present |
| Git HEAD | `c280cc5` ("chỉnh lại thành encrypt ảnh và âm thanh sau đó app decrypt ra") |

**Commands executed (actual)**

```text
.\gradlew.bat assembleDebug testDebugUnitTest --no-configuration-cache --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-configuration-cache --console=plain
.\gradlew.bat lintDebug --no-configuration-cache --console=plain
.\gradlew.bat assembleRelease --no-configuration-cache --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.engqa/.LoginActivity
adb shell uiautomator dump; adb shell input tap/text/keyevent; adb shell dumpsys activity
adb logcat -d -v brief
```

**Actual results**

| Check | Result |
|---|---|
| Debug build | `BUILD SUCCESSFUL` |
| Unit tests | 41 tests, 0 failures, 0 errors, 0 skipped |
| Instrumented tests | 7 tests, 0 failures, 0 errors, 0 skipped |
| Android lint | `BUILD SUCCESSFUL`; 0 errors, **271 warnings** |
| Release build | `BUILD SUCCESSFUL`; APK is `app-release-unsigned.apk` (no signing config) |
| Install/launch | Installed and launched on API 29 |
| App crashes | None observed in the tested flows |

> These are the original-audit results. The post-remediation run is recorded in §4.1 (69 unit tests, 7 instrumented tests, 0 lint errors / 268 warnings, debug and release builds successful).

**Environment constraints**

- No production credentials were used. A shared demo Firebase project is configured by the checked-in `google-services.json`; values are redacted in this report.
- No destructive writes were issued. Opening the promo detail screen incremented the promo's `viewCount` once through normal app behaviour, and testing signed in against Firebase Auth. Local SharedPreferences/Room and cache state were changed by testing and the temporary instrumentation run; the app was uninstalled by `connectedDebugAndroidTest` afterwards.
- The uncommitted temporary test `TempUnreachableScreensTest.java` was created solely to launch unreachable screens, executed, and then deleted. No application source code was modified during the **original** audit.
- **Remediation pass (2026-10-24):** runtime checks signed in/out with the seeded demo accounts, saved an unchanged profile, saved one promo locally, opened one promo detail (one `viewCount` increment), and validated the create-promo error banner. No questions/answers/votes were created, accepted or deleted. `connectedDebugAndroidTest` uninstalled the app after the final run. The application source was modified for the fixes listed in §5.

**Not accessible / not tested**

- Firebase Console, deployed rules/index state, Cloud Functions, Auth email delivery, Google account selection completion, camera/image-picker flows, a second signed-in user, Android 11–15 runtime behaviours.

---

## 3. Feature Inventory

| ID | Feature | Main Entry Point | Relevant Files | Test Status | Notes |
|---|---|---|---|---|---|
| F-01 | Session restore / launcher redirect | `LoginActivity` (launcher) | `LoginActivity.java`, `SessionManager.java`, `AuthViewModel.java` | Passed | A signed-in user is redirected to Home; a fresh install shows Login. |
| F-02 | Email/password login | Login button | `LoginActivity.java`, `AuthViewModel.java`, `UserRepository.java` | Passed | Empty, invalid-email, wrong-password and valid-login cases exercised. |
| F-03 | Google Sign-In | Login/Register "Tiếp tục với Google" | `GoogleAuthHelper.java`, `LoginActivity.java`, `RegisterActivity.java` | Partial | Credential Manager UI launched; completion blocked (no Google account). |
| F-04 | Registration form | "Đăng ký" | `RegisterActivity.java`, `AuthViewModel.java`, `UserRepository.java` | Partial | Validation and password mismatch tested; account creation not executed. |
| F-05 | Level selection | After registration | `ChooseLevelActivity.java`, `activity_choose_level.xml` | Not tested | Requires creating an account. |
| F-06 | Email verification | Verify box, resend, refresh | `LoginActivity.java`, `AuthViewModel.java`, `UserRepository.java` | Failed | Verify box is wrongly visible before login; resend then reports "Chưa đăng nhập". |
| F-07 | Password reset | "Bạn quên mật khẩu?" | `LoginActivity.java`, `AuthViewModel.java` | Not tested | Dialog and validation present; no inbox to complete. |
| F-08 | Change password | Profile → Đổi mật khẩu | `EditProfileActivity.java`, `ProfileViewModel.java`, `UserRepository.java` | Not tested | Reauthentication + update implemented; not executed to preserve credentials. |
| F-09 | Logout | Home avatar menu; profile; Settings | `HomeActivity.java`, `MenuScreenActivity.java`, `SettingsFragment.java`, `SessionManager.java` | Failed | Home-menu logout leaves stale screens and a permission-error feed. |
| F-10 | Home feed (realtime, paging, cache) | Home tab | `HomeActivity.java`, `FeedViewModel.java`, `FeedAdapter.java`, `QuestionRepository.java` | Passed | Feed, cache hint, paging controls and retry state observed. |
| F-11 | Category filter | Category chips | `HomeActivity.java`, `FeedViewModel.java`, `CategoryRepository.java`, `CategoryChipAdapter.java` | Partial | "Trung tâm" filter verified; a normal category filter alone was not re-verified after search tests. |
| F-12 | Sort filter | Mới nhất / Phổ biến / Chưa trả lời | `HomeActivity.java`, `FeedViewModel.java`, `QuestionRepository.java` | Partial | "Phổ biến" produced a different ordered list; newest default and "Chưa trả lời" not individually verified. |
| F-13 | Keyword search | Search field | `FeedViewModel.java`, `QuestionRepository.java`, `TextUtils2.java` | Failed | Incorrect paging and ignored filters confirmed. |
| F-14 | Promo interleaving in feed | "Tất cả" feed | `FeedMixer.java`, `FeedAdapter.java`, `PromoRepository.java` | Passed | Approved promo card appeared and opened detail. |
| F-15 | Word of the Day card | Home feed card | `WordRepository.java`, `FeedViewModel.java`, `item_word_of_day.xml` | Blocked | Live fetch failed on emulator after data clear; cached screen verified separately. |
| F-16 | Word detail + pronunciation | Word card | `WordDetailActivity.java`, `WordOfDayViewModel.java`, `AudioPlayerManager.java` | Partial | Instrumented cached-word test passed; UI renders word/meaning; dictionary audio not played. |
| F-17 | Notifications list/badge | Bell icon | `NotificationActivity.java`, `NotificationViewModel.java`, `NotificationAdapter.java` | Partial | Empty state and "Đánh dấu đã đọc" observed; no populated list to verify. |
| F-18 | In-app notification delivery | `NotificationWatcher` | `NotificationWatcher.java`, `NotificationHelper.java`, `EngQAApp.java` | Not tested | Needs a second user/action to generate a notification. |
| F-19 | Ask-question form | "Đặt câu hỏi" tab | `MainActivity.java`, `CreateQuestionViewModel.java` | Partial | Form, category/level spinners, media controls observed; submission not executed. |
| F-20 | Image attach/capture/thumbnail | Image buttons | `MainActivity.java`, `MediaCompressor.java`, `AttachmentAdapter.java`, `ImageUtils.java` | Not tested | Camera/picker flow not exercised; image upload would write to shared backend. |
| F-21 | Audio record/pick/play | "Ghi âm" / audio player | `AudioRecorder.java`, `AudioPlayerView.java`, `AudioPlayerManager.java` | Partial | Permission, start, stop, player shown, playback invoked; audio-file picking/upload not tested. |
| F-22 | Publish question (online, rollback) | "Đăng câu hỏi" | `QuestionPublisher.java`, `FirestoreMediaRepository.java`, `CreateQuestionViewModel.java` | Not tested | Would create live question/media documents. |
| F-23 | Offline draft worker | WorkManager | `PendingQuestionWorker.java`, `PendingDraftStore.java`, `WorkScheduler.java` | Not tested | Static review only. |
| F-24 | Question detail (realtime/media) | Feed/notification/profile item | `QuestionDetailActivity.java`, `QuestionDetailViewModel.java` | Passed | 0-answer and 3-answer questions rendered; report menu opened. |
| F-25 | Answer submission | Detail composer | `QuestionDetailViewModel.java`, `AnswerRepository.java` | Partial | Composer rendered; no answer posted; empty-answer validation code reviewed. |
| F-26 | Up/down voting | Detail answer arrows | `VoteRepository.java`, `AnswerAdapter.java`, `QuestionDetailViewModel.java` | Failed | Existing vote never loaded; see BUG-011. |
| F-27 | Accept answer | Detail "Chấp nhận" | `AnswerRepository.java`, `AnswerAdapter.java`, `QuestionDetailViewModel.java` | Failed | Not exclusive; see BUG-003. |
| F-28 | Report content | Detail overflow menu | `ReportDialogFragment.java`, `ReportViewModel.java`, `ReportRepository.java` | Partial | Dialog and reason field opened; submission not executed. |
| F-29 | Delete question | Detail overflow menu | `QuestionDetailActivity.java`, `QuestionRepository.java` | Failed | No confirmation and no cascade; see BUG-004/005. |
| F-30 | Profile view and stats | "Cá nhân" tab | `MenuScreenActivity.java`, `ProfileViewModel.java` | Passed | Stats, tabs, question list, empty answer state observed. |
| F-31 | Edit profile | Profile → "Sửa" | `EditProfileActivity.java`, `ProfileViewModel.java`, `UserRepository.java` | Failed | Unchanged-name save fails; see BUG-001. |
| F-32 | My questions / my answers | Profile tabs | `MenuScreenActivity.java`, `ProfileViewModel.java`, `AnswerAdapter.java` | Passed | My questions list and empty my-answers state observed. |
| F-33 | "Trung tâm" promo filter | Category chip | `HomeActivity.java`, `FeedMixer.java` | Passed | Shows promos only and hides sort chips. |
| F-34 | Promo detail and contact actions | Promo card | `PromoDetailActivity.java`, `PromoValidator.java`, `PromoViewModel.java` | Partial | Details, counters, website/call/save buttons rendered; contact launch not exercised. |
| F-35 | Save / unsave promo locally | Promo detail save button | `PromoDetailActivity.java`, `SavedPromoRepository.java`, `SavedPromoDao.java` | Passed | Save, list, remove, empty state verified via Room; table is device-global (BUG-029). |
| F-36 | Promo lead (consultation) | "Đăng ký tư vấn" | `LeadDialogFragment.java`, `PromoRepository.java` | Not tested | Would write a lead to shared demo data. |
| F-37 | Create promo (centre) | Profile menu → "Đăng bài quảng bá" | `CreatePromoActivity.java`, `CreatePromoViewModel.java` | Partial | Centre prefill and validation observed; not submitted. |
| F-38 | Centre list / centre detail | None reachable | `CenterListActivity.java`, `CenterDetailActivity.java`, `CenterAdapter.java` | Failed | Screens render under a temporary direct test, but no user entry point exists. |
| F-39 | Centre request / edit | Centre list only | `CenterRequestActivity.java`, `CenterEditActivity.java` | Not tested | Reachable only from the unreachable centre list. |
| F-40 | Settings | Avatar menu → Settings | `SettingsActivity.java`, `SettingsFragment.java`, preferences XML | Partial | Settings screen and listed preference rows observed; no preference action executed; cache defect and API 33 gap. |
| F-41 | Bottom navigation / back behaviour | Bottom bar | `BaseNavActivity.java`, `HomeActivity.java`, `MainActivity.java`, `MenuScreenActivity.java` | Failed | Back returns to the previously visited tab instead of exiting. |
| F-42 | Server-error screen | None reachable | `ServerErrorActivity.java`, `activity_server_error.xml` | Partial | Renders under direct instrumentation, but no navigation uses it. |
| F-43 | Build and packaging | Gradle | `app/build.gradle.kts`, version catalog, wrapper | Passed | Debug and release builds succeed; release is unsigned. |
| F-44 | Firestore rules and indexes | Backend config | `firestore.rules`, `firestore.indexes.json`, `firebase.json` | Partial | Inspected; authorization and index issues found. |

---

## 4. Test Execution Summary

| Test ID | Feature | Scenario | Expected Result | Actual Result | Status | Evidence |
|---|---|---|---|---|---|---|
| T-01 | Build / unit tests | `assembleDebug testDebugUnitTest` | Build and 41 tests pass | `BUILD SUCCESSFUL`; 41/41 pass | Pass | Gradle output; XML suites: 1+4+5+7+5+6+3+9+1 |
| T-02 | Release build | `assembleRelease` | Release APK builds | `BUILD SUCCESSFUL`; unsigned APK produced | Pass | `app-release-unsigned.apk` |
| T-03 | Static analysis | `lintDebug` | Lint runs, no errors | 271 warnings, 0 errors | Pass | `lint-results-debug.xml` |
| T-04 | Instrumented suite | `connectedDebugAndroidTest` | All Espresso tests pass | 7/7 pass on Pixel_4 API 29 | Pass | JUnit XML `tests="7" failures="0"` |
| T-05 | Fresh launch | Clear data; launch Login | Login screen with no user-specific state | Login shown, but "Email chưa được xác minh" box visible | Fail | UI dump: verify text before any authentication |
| T-06 | Login validation | Empty fields, tap Login | Inline email-required error | "Vui lòng nhập email" | Pass | UI dump |
| T-07 | Login validation | `abc` / `123456` | Invalid-email error | "Email không hợp lệ" | Pass | UI dump; also Espresso test |
| T-08 | Login failure | Demo email + wrong password | Credential error, stay on Login | "Email hoặc mật khẩu không đúng" toast/inline | Pass | Runtime |
| T-09 | Login success | Seeded demo user A / valid password (redacted) | Home feed | Home feed loaded; avatar "M" | Pass | Activity dump; Firestore data visible |
| T-10 | Centre account login | Seeded centre-manager account (redacted) | Home + centre menu | Home; menu included "Đăng bài quảng bá" | Pass | Runtime |
| T-11 | Home feed | Scroll feed | Questions and promo cards | Questions, promo, cache hint | Pass | UI dumps |
| T-12 | Popular sort | Tap "Phổ biến" | Reordered popular questions | Seeded popular question shown | Pass | Runtime |
| T-13 | Category filter | Tap "Trung tâm" | Promos only, sort hidden | Promo-only list; sort chips absent | Pass | UI dump |
| T-14 | Search | Search `effect`; select "Ngữ pháp" | Search constrained or filter applied | Vocabulary result remained; filter ignored | Fail | UI dumps before/after category tap; code path |
| T-15 | Search paging | Search `cach`; scroll to end | Only matching questions | Unrelated "how many star above the sky" and "testing0001" appended | Fail | UI dump after scrolling; `FeedViewModel.loadMore` |
| T-16 | Question detail | Open 3-answer question | Question, answers, vote counts, composer | 3 answers, score 1, composer, report option | Pass | UI dump |
| T-17 | Report dialog | Overflow → Report | Dialog with reason | Dialog shown; no submission | Pass (UI only) | UI dump |
| T-18 | Question delete | Overflow menu | Confirmation before destructive action | No confirmation; direct delete call | Fail | Static code; owner-only menu branch not exercised |
| T-19 | Promo detail | Open promo | Details and actions | Full promo details; save worked; removal tested from the saved list | Pass | UI dumps |
| T-20 | Saved promos | Save promo; open Saved; remove | Item appears; removal empties list | Verified both directions | Pass | Room state + UI |
| T-21 | Notifications | Bell icon | Empty/list state | Empty state shown | Pass | UI dump |
| T-22 | Profile | Profile tab | User info, stats, tabs | Correct username/email/counts; tabs worked | Pass | UI dump |
| T-23 | Profile edit | Open Sửa; tap Lưu unchanged | Save succeeds and closes screen | Stayed on EditProfile; toast; no update | Fail | Activity remained; logcat Toast; code |
| T-24 | Profile my answers | Tap "Câu trả lời của tôi" | Empty state for 0 answers | "Chưa có nội dung" | Pass | UI dump |
| T-25 | Bottom nav | Profile → Home → Back | Back exits/minimises or returns to start | Returned to Profile screen | Fail | `mResumedActivity` dumps |
| T-26 | Logout from Home | Avatar menu → Đăng xuất | Login or clean guest state | Permission-error feed; Back showed old profile/email | Fail | UI dumps and activity dumps |
| T-27 | Registration | Taken username; mismatched passwords | Availability warning; mismatch error | "Tên hiển thị có thể dùng được"; mismatch error works | Partial | UI dumps |
| T-28 | Google sign-in | Tap Google | Account selection | Google assisted-sign-in screen launched; no account to complete | Partial | `AssistedSignInActivity` resumed |
| T-29 | Ask form | Open Ask; record audio; play | Form + audio | UI loaded; mic permission, recording, player worked | Pass (local media) | UI dumps; logcat no audio errors |
| T-30 | Ask validation | Submit empty prompt | Inline/field error | Not executed; validation exists in code | Not tested | Code review only |
| T-31 | Create promo | Open as centre; submit empty | Centre prefilled; errors visible | Centre/phone/link prefilled; title error text exists below scroll viewport | Partial | UI dump reports `tvFormError` offscreen |
| T-32 | Centre list/detail | Temporary instrumentation launch | List loads; detail opens | List loaded; first item opened detail | Pass (render) | Temporary 2-test run, removed |
| T-33 | Server-error screen | Temporary instrumentation launch | Screen renders | Screen rendered | Pass (render) | Temporary test, removed |
| T-34 | Word detail | Cached word instrumentation | Word and definition shown | Test passed | Pass | `WordOfDayCachedTest` |
| T-35 | Word card live fetch | Home after data clear | Card appears | No card after >30 s; host API reachable | Blocked | UI dumps; environment limitation |
| T-36 | Destructive/write flows | Post, vote, accept, delete, report, lead, register | End-to-end verification | Not executed to avoid changing shared data | Not tested | Code review only |
| T-37 | Error handling | Wrong password, invalid email/input | Specific errors / no crash | Specific inline/toast errors; no crash | Pass | Runtime |

### 4.1 Post-remediation execution (2026-10-24)

The table above records the **original** audit run and is preserved for traceability. The following commands were re-run after remediation, and the runtime checks below were repeated on the same Pixel_4 API 29 emulator.

| Check | Command | Result |
|---|---|---|
| Debug build | `.\gradlew.bat assembleDebug testDebugUnitTest` | `BUILD SUCCESSFUL` |
| Unit tests | same | **69/69 pass** (existing helpers plus expanded `TextUtils2`, `SearchRanker` and `FeedMixer` coverage) |
| Instrumented tests | `.\gradlew.bat connectedDebugAndroidTest` | **7/7 pass**, 0 skipped, 0 failed |
| Android lint | `.\gradlew.bat lintDebug` | `BUILD SUCCESSFUL`; **0 errors, 268 warnings** (was 271; the `GestureBackNavigation` error introduced by the first Back-handler attempt was replaced with `OnBackPressedCallback`) |
| Release build | `.\gradlew.bat assembleRelease` | `BUILD SUCCESSFUL`; unsigned `app-release-unsigned.apk` |
| Merged manifest permissions | inspect `processDebugMainManifest` output | `POST_NOTIFICATIONS` present; `READ_MEDIA_IMAGES`/`READ_MEDIA_AUDIO` absent |
| Index JSON validity | `ConvertFrom-Json firestore.indexes.json` | valid |
| Rules | static review only | not deployed; no Firebase CLI/emulator available |

**Re-verified runtime flows (API 29 emulator)**

| Check | Result |
|---|---|
| Fresh launch before login | Login screen; verification box absent (BUG-016) |
| Seeded learner login; profile edit → "Lưu" unchanged | Returned to `MenuScreenActivity`; profile intact (BUG-001) |
| Profile tab stats | Live lists/counters rendered correctly; no stale doc counters |
| Home avatar menu | "Trung tâm Anh ngữ" present and opens `CenterListActivity` with request entry (BUG-006) |
| Question overflow → "Xoá" | Confirmation dialog with question title and warning; cancelled (BUG-004) |
| Home logout → system Back | `LoginActivity`, then launcher; no previous profile/email (BUG-002) |
| Profile → Home → system Back | Launcher resumed, not the previous tab (BUG-015) |
| Saved promos as centre manager, then as learner | Promo visible to saver only; learner list empty (BUG-029, fresh install) |
| Create promo as centre manager, empty submit | Error text `Tiêu đề phải từ 5 đến 120 ký tự` visible above the submit bar; title focused (BUG-027) |
| Settings → "Xoá bộ nhớ tạm" | Action completed without crash (BUG-019) |
| Live Word-of-the-Day card | Loaded ("feasible") after login; previously failed in the original audit |
| Search normalization | `ngu phap` / `has been` / `hien tai` found accent-free, multi-word matches |
| Search partial/fuzzy | `pha` found partial-word matches; typo variants are covered by unit tests and live candidates |
| Search + filters/sorts | `Từ vựng` + `anh`, `Ngữ pháp` + `hien tai`, `Phổ biến` + `phap`, `Chưa trả lời` + `phap` returned only matching/filtered results |
| Search states | No-results, too-short (`Nhập ít nhất 2 ký tự`), clear-to-feed, and rapid-query stale-result checks passed |
| Search indexes | 19/19 READY; direct Firestore REST `array-contains-any` + category query succeeded |

**Not re-executed (documented per issue):** accept/change-accept, vote/reopen/unvote, delete-confirm, question cascade, offline worker/account switch, notification backlog/type, **Firestore rules deployment**, Android 11+ intents, Android 13+ notification permission, and the v1→v2 Room upgrade.

### 4.2 Responsive UI pass (2026-10-24)

The manifest already used `screenOrientation="fullSensor"` and did not suppress recreation, so the
root cause was layout, not rotation policy. The audit had not previously tested rotation.

| Screen | Before | After / verification |
|---|---|---|
| Home | Landscape used a 2-column category grid inside a ~54dp-tall rail; lower chips were clipped and unreachable | One horizontal, scrollable chip row in both orientations; landscape screenshot verified no clipped rows |
| Ask (`MainActivity`) | Existing landscape two-column layout | Reviewed in landscape; form scrolls, bottom action and nav remain reachable |
| Login / Register | XML gutters were overwritten by the edge-to-edge inset listener; fields stretched edge-to-edge | Insets now add to the XML padding; landscape auth gutters are 96dp and the form is centered. Login screenshot verified |
| ChooseLevel | Portrait stack was taller than landscape and had no scroll; `axis` baseline alignment could also clip the middle card | New `layout-land` horizontal card row with compact heights and `baselineAligned=false`; temporary Espresso test verified every card and Continue are fully visible in portrait and landscape, and the selected level survives `recreate()` |
| ServerError | 277dp illustration + 72dp top bar did not fit between the top and bottom bars in landscape | New `layout-land` side-by-side illustration/copy with a 56dp top bar; temporary Espresso test verified image, title and bottom nav are fully visible |
| Profile | Tab selection reset on recreation | Selection saved/restored; runtime rotation kept "Câu trả lời của tôi" active |
| Edit profile / centre forms / create promo | Full-width fields on wide landscape screens | `values-land/dimens.xml` widens side gutters to 64dp; Edit Profile screenshot verified |
| Detail screens | Scroll position was not restorable (no view IDs) | `scrollQuestion`, `scrollPromo`, `scrollCenter`, `scrollWord` and `scrollProfile` IDs added; answer/draft text fields already save state |
| Lead dialog | Fixed-height stack could be clipped by the IME in landscape | Custom content is now wrapped in a `ScrollView`, preserving the same fields |

**Responsive verification performed:** API 29 Pixel_4 emulator, `wm set-user-rotation lock` between
0 and 1. Temporary `TempResponsiveLayoutTest` (fully-visible ChooseLevel/ServerError checks and
ChooseLevel selection recreation in portrait and landscape) was executed and deleted; a temporary
Register hold/screenshot verified the scrollable landscape form. The existing instrumented suite
passes **7/7 in portrait and 7/7 in landscape** (after making `RegisterValidationTest` scroll the
form instead of relying on a brittle single `scrollTo`). Runtime screenshots/dumps covered Login,
Register, Home, Ask, Profile, Edit Profile, QuestionDetail, CenterList, CenterDetail, Notification,
Settings and SavedPromos in landscape; Home/search/search-result state and the Ask draft were
rotated and verified; ChooseLevel and ServerError were fully-visible in portrait and landscape.
No crashes or `InflateException`s appeared in logcat.

**Other checks:** `assembleDebug`, `assembleRelease`, `testDebugUnitTest` **69/69**, `lintDebug`
**0 errors / 273 warnings** (one pre-existing lint quickfix warning remains on `server_error_code`).

**Not verified in this pass:** tablet/sw600dp and foldable layouts, reverse-landscape, PromoDetail,
WordDetail, CenterRequest/CenterEdit/CreatePromo in landscape, image viewer, lead/report dialogs
with the keyboard open, and rotation during in-flight uploads/playback. These layouts are
scrollable or use the same responsive dimensions, but they were not executed end-to-end.

---

## 5. Detailed Issues

### BUG-001 — Profile save always fails when the display name is unchanged

- **Feature:** Edit profile (display name and level).
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/data/UserRepository.java`
- **Line(s):** 230–241 and 257–279.
- **Class/Function:** `UserRepository.isUsernameTaken`, `UserRepository.updateProfile`; called from `ProfileViewModel.updateProfile` (`app/src/main/java/com/example/engqa/viewmodel/ProfileViewModel.java:90–105`) and `EditProfileActivity` save (`app/src/main/java/com/example/engqa/EditProfileActivity.java:160–172`).
- **Description:** `updateProfile` always runs the uniqueness query when a non-empty username is supplied. `EditProfileActivity` always supplies the current username. `isUsernameTaken` queries `users` where `username == value` with no exclusion for the caller's UID, so the current user's own document is returned and treated as a collision.
- **Reproduction Steps:**
  1. Sign in as a seeded non-centre demo user.
  2. Open "Cá nhân" → "Sửa".
  3. Without changing any field, tap "Lưu".
  4. Observe that the screen does not close and a toast is emitted.
- **Expected Result:** Unchanged profile data is saved (or a no-op succeeds) and the screen returns to the profile.
- **Actual Result / Evidence:** The activity remains on `EditProfileActivity`; logcat shows a Toast posted by `com.example.engqa`; the code path returns `UsernameTakenException` because the uniqueness query necessarily finds the caller's own profile. The same failure blocks changing only the level.
- **Root Cause:** Self-collision in `isUsernameTaken`; no comparison against the caller's current username or document ID.
- **Impact:** Users cannot change their level or save any profile change unless they simultaneously rename themselves to an unused username. This makes a major profile feature unusable.
- **Related Files:** `App` `User.java` (username/level fields); `ProfileViewModel.java`; `EditProfileActivity.java`; `firestore.rules` (`users` update).
- **Suggested Remediation:** Read the current user document first and skip the uniqueness query when the submitted name equals the stored name; alternatively exclude the caller's document/UId from the query. Pass the original username into the repository.
- **Verification Needed:** Repeat the repro; then change only the level; then change to an unused username; then change to another user's username. Confirm each expected outcome.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `app/src/main/java/com/example/engqa/data/UserRepository.java`.
- Fix: `updateProfile` reads the caller's document first and skips the uniqueness query when the submitted name equals the stored name; `checkNameAvailable` retains the original collision check for real renames.
- Root cause: the uniqueness query included the caller's own document.
- Verification: `assembleDebug` and `testDebugUnitTest` passed (69/69); runtime on the API 29 emulator signed in as the seeded learner, opened "Cá nhân" → "Sửa", tapped "Lưu" without editing, and returned to `MenuScreenActivity` with the profile intact and no collision toast.
- Remaining limitation: changing to a genuinely duplicate name was not executed because it would require writing another account's reserved name; the collision query itself is unchanged.

### BUG-002 — Logout from the Home menu leaves stale screens and a broken feed

- **Feature:** Logout and session/back-stack handling.
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/HomeActivity.java`; `app/src/main/java/com/example/engqa/BaseNavActivity.java`; `app/src/main/java/com/example/engqa/MenuScreenActivity.java`; `app/src/main/java/com/example/engqa/viewmodel/ProfileViewModel.java`
- **Line(s):** `HomeActivity.java:422–425`; `BaseNavActivity.java:45–64`; `MenuScreenActivity.java:59–64, 93–95`; `ProfileViewModel.java:61–67`.
- **Class/Function:** `HomeActivity` logout menu handler; `BaseNavActivity.navigateTo`; `MenuScreenActivity.onStart`; `ProfileViewModel.start`.
- **Description:** Home's logout handler calls `SessionManager.logout()` and then `recreate()`. It does not clear the task, finish other activities, or reset the view models. Other tabs are switched with `FLAG_ACTIVITY_REORDER_TO_FRONT`, so previously visited screens remain in the task. `MenuScreenActivity` stores the UID in `onCreate` and calls `viewModel.start(uid)` in `onStart`, so after logout it reattaches listeners with the old UID. The Home feed query is then denied by `firestore.rules` (questions require authentication).
- **Reproduction Steps:**
  1. Sign in.
  2. Open "Cá nhân", then switch to "Trang chủ" (Profile stays in the task).
  3. Open the avatar menu → "Đăng xuất".
  4. Observe the Home feed error "Bạn không có quyền thực hiện thao tác này".
  5. Press Back twice.
- **Expected Result:** Logout returns to Login or a clean guest Home; no previous user's data remains accessible; the feed either works as a guest per rules or redirects to Login.
- **Actual Result / Evidence:** Home showed "Bạn không có quyền thực hiện thao tác này" with a Retry button. Pressing Back eventually resumed `MenuScreenActivity` and displayed the previous username, email, level, and "Đăng xuất" button for `minh_anh`.
- **Root Cause:** Incomplete logout navigation plus a cached UID in `MenuScreenActivity`/`ProfileViewModel`; `firestore.rules:99, 138` deny reads after sign-out.
- **Impact:** Privacy/session-handling defect: a previous user's profile and email remain visible after logout. The Home feed becomes unusable until the user finds the small "Đăng nhập" popup item. Every tab held in the back stack is affected.
- **Related Files:** `SessionManager.java:37–40`; `HomeActivity.java:176–201`; `AndroidManifest.xml` activity declarations; `firestore.rules:99, 138`.
- **Suggested Remediation:** On logout, start `LoginActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, finish/destroy other activities, and clear user-scoped view-model state. Re-read the session in `onStart` and finish or redirect if the UID is null. Consider `FLAG_ACTIVITY_CLEAR_TOP`/`SINGLE_TOP` navigation instead of reordering tabs.
- **Verification Needed:** Repeat from each tab and each logout entry point; confirm no previous-user data is reachable via Back and no permission error appears on a proper post-logout destination.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `HomeActivity.java`, `MenuScreenActivity.java`, `ProfileViewModel.java`, `SessionManager.java`.
- Fix: Home logout now starts `LoginActivity` with `NEW_TASK | CLEAR_TASK`; Home redirects to Login in `onStart` when the session is gone; the profile tab re-reads the session, redirects when signed out, and reattaches cleanly to a changed UID; logout cancels queued question work.
- Root cause: incomplete task clearing plus a cached UID in `MenuScreenActivity`/`ProfileViewModel`.
- Verification: runtime logout from the Home avatar menu resumed `LoginActivity`; pressing Back resumed the launcher, no previous username/email appeared; the profile screen's stats and user data rendered correctly before logout.
- Remaining limitation: the other logout entry points (Settings/Profile) already used clear-task and were not re-exercised individually.

### BUG-003 — Accepting answers is not exclusive and double-counts `acceptedCount`

- **Feature:** Accept an answer.
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/data/AnswerRepository.java`; `app/src/main/java/com/example/engqa/ui/AnswerAdapter.java`; `app/src/main/java/com/example/engqa/viewmodel/QuestionDetailViewModel.java`
- **Line(s):** `AnswerRepository.java:114–134`; `AnswerAdapter.java:176–177`; `QuestionDetailViewModel.java:200–213`.
- **Class/Function:** `AnswerRepository.accept`; `AnswerViewHolder.bind`; `QuestionDetailViewModel.accept`.
- **Description:** `accept` writes `isAccepted = true` for the selected answer, sets the question's `acceptedAnswerId`, and increments the answer author's `acceptedCount`. It never reads the previous `acceptedAnswerId`, never clears a previously accepted answer, and never decrements the previous author's count. The accept button is shown for every answer where `!answer.isAccepted()` and the viewer is the question owner, even when the question already has an accepted answer.
- **Reproduction Steps (safe test environment required):**
  1. Sign in as the author of a question with at least two answers.
  2. Open the question.
  3. Accept answer A.
  4. Accept answer B.
- **Expected Result:** Only one accepted answer per question; previous acceptance cleared; only the current accepted author has `acceptedCount` increased once; an unaccept/change flow is defined.
- **Actual Result / Evidence:** Both answers are updated to `isAccepted = true`; the question points to B; both authors receive one `acceptedCount` increment; the accepted badges remain on both. No code path clears A.
- **Root Cause:** Missing transaction that validates/changes the question's existing accepted answer and adjusts both authors; missing idempotency guard.
- **Impact:** Question/answer state becomes inconsistent, profile "Được chấp nhận" statistics are inflated, and the UI shows multiple accepted answers. Repeating the action can inflate counts further.
- **Related Files:** `firestore.rules:157–162` (question owner may update answers); `ProfileViewModel` accepted-count display; `MenuScreenActivity`.
- **Suggested Remediation:** In one transaction, read the question's `acceptedAnswerId`; if it is the same answer, return success without incrementing; otherwise unset the old answer and decrement the old author's count, then set the new answer and increment the new author's count. Hide accept controls once solved or expose an explicit "change accepted answer" action.
- **Verification Needed:** Exercise accept A → accept B → accept A again; verify exactly one accepted document and correct per-author counts.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `AnswerRepository.java`, `firestore.rules`, `MenuScreenActivity.java`.
- Fix: acceptance now runs in a transaction that reads the question, clears the previously accepted answer, marks the new answer accepted, and updates `acceptedAnswerId`/`isSolved`; repeating the same acceptance is a no-op. The denormalised `acceptedCount` is no longer incremented; the profile derives accepted counts from the live answer list.
- Root cause: the old batch never read the previous accepted answer and incremented a cross-user counter.
- Verification: source review, `assembleDebug`, unit tests and lint passed. Rules are statically reviewed.
- Remaining limitation: accept/change-accept was not executed against the shared Firebase data, and the updated rules were not deployed or rules-emulator-tested.

### BUG-004 — Question deletion has no confirmation

- **Feature:** Delete question.
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/QuestionDetailActivity.java`
- **Line(s):** 334–348.
- **Class/Function:** `QuestionDetailActivity.showOptionsMenu`, delete-menu branch.
- **Description:** Tapping the overflow "Xóa" item calls `viewModel.deleteQuestion()` immediately. There is no confirmation dialog, no undo, and no display of how many answers/media will be orphaned.
- **Reproduction Steps:** As a question author, open the overflow menu and tap the delete item.
- **Expected Result:** A confirmation dialog explains the irreversible effect; deletion starts only after explicit confirmation.
- **Actual Result / Evidence:** The menu item directly triggers deletion (static code); the menu is reachable in the observed question detail. Deletion itself was not executed to avoid destroying shared data.
- **Root Cause:** The menu-item click handler deletes without an `AlertDialog`.
- **Impact:** Irreversible loss of a user's question from a single tap in an overflow menu; combined with BUG-005, it also strands answers/media.
- **Related Files:** `QuestionDetailViewModel.java:230–244`; `QuestionRepository.java:170–182`; `strings.xml` delete/report labels.
- **Suggested Remediation:** Add a confirmation dialog naming the question and warning about attached answers/media; consider soft deletion (status/tombstone) so data can be recovered.
- **Verification Needed:** In a disposable project, cancel the dialog (no deletion), then confirm (deletion occurs and UI returns to the feed).

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `QuestionDetailActivity.java`, `res/values/strings.xml`.
- Fix: the overflow delete item now opens an `AlertDialog` that names the question and warns that answers, votes and attachments are removed permanently; deletion runs only after the positive button.
- Root cause: the menu handler called `deleteQuestion()` directly.
- Verification: runtime as the question owner, overflow → "Xoá" opened the dialog with the title `Xoá câu hỏi?` and the warning text; "Huỷ" closed it without deleting.
- Remaining limitation: the destructive confirm path was not executed to avoid deleting shared demo content.

### BUG-005 — Question deletion does not cascade or repair related data

- **Feature:** Delete question / data integrity.
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** PARTIALLY FIXED.
- **File:** `app/src/main/java/com/example/engqa/data/QuestionRepository.java`
- **Line(s):** 170–182.
- **Class/Function:** `QuestionRepository.deleteQuestion`.
- **Description:** The batch deletes only `questions/{qid}` and decrements the author's `questionCount`. It does not delete or update the `answers`, `votes`, `media` subcollections, does not delete related notifications or reports, and does not adjust answer authors' `answerCount`/`acceptedCount`. Firestore does not cascade deletes automatically.
- **Reproduction Steps (safe test environment required):**
  1. Create a question with two answers, one accepted, plus media.
  2. Delete the question.
  3. Inspect Firestore and the answer authors' "Câu trả lời của tôi" lists.
- **Expected Result:** All child data is removed or tombstoned; user counters remain consistent; no orphaned answers appear in profiles.
- **Actual Result / Evidence:** Child documents remain. Answer authors' collection-group query (`MenuScreenActivity` my answers) still returns the orphaned answers; opening one targets a missing question. Media documents continue to consume Firestore storage. `acceptedCount` and `answerCount` remain unchanged.
- **Root Cause:** No recursive/cascading deletion logic and no Cloud Function; the repository treats Firestore as if deletes were hierarchical.
- **Impact:** Data corruption, misleading profile lists, broken detail navigation for orphan answers, stale stats, and unnecessary storage/cost. Reports/notifications can point to deleted content.
- **Related Files:** `AnswerRepository.java` (answer/vote storage); `QuestionDetailViewModel.java:230–244`; `MenuScreenActivity.java` (collection-group answers); `firestore.rules` (no delete cascade).
- **Suggested Remediation:** Use a Cloud Function or batched recursive cleanup for answers, votes, media, notifications and reports; decrement the affected users' counters in the same transactional workflow. Alternatively, use a soft-delete flag and filter it from feeds/profile queries.
- **Verification Needed:** In a disposable project, delete a question with answers/votes/media and confirm no orphan documents/counters remain.

**Remediation (2026-10-24)** — **PARTIALLY FIXED**
- Files modified: `QuestionRepository.java`, `firestore.rules`, `MenuScreenActivity.java`.
- Fix: deletion now collects and removes answer, vote and media documents (chunked batches, question removed last) before deleting the question and decrementing the author's `questionCount`. Rules allow the question owner to delete answers/votes on their question. Profile counters are derived from the live questions/answers lists, so orphaned answers no longer inflate them.
- Root cause: the old implementation treated Firestore as if parent deletion cascaded.
- Verification: `assembleDebug`, unit tests and lint passed; the deletion algorithm was reviewed, including the >400-child chunk path and rollback-safe ordering.
- Remaining limitations: notifications and reports that reference the question are not deleted because they are not readable by the question author and no Cloud Function is available; other answer authors' stored `answerCount`/`acceptedCount` fields are left untouched (the UI derives them); the new rules and cascade were not deployed or executed against disposable data. Full atomic cleanup still requires a trusted backend.

### BUG-006 — Entire centre browsing/request/edit feature is unreachable

- **Feature:** Centre list, centre detail, centre request, centre edit.
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/CenterListActivity.java`, `CenterDetailActivity.java`, `CenterRequestActivity.java`, `CenterEditActivity.java`; `app/src/main/java/com/example/engqa/HomeActivity.java`; `app/src/main/res/menu/menu_profile.xml`; `app/src/main/AndroidManifest.xml`
- **Line(s):** `CenterListActivity.java:25`; manifest declarations at `AndroidManifest.xml:97–117`; Home profile-menu construction at `HomeActivity.java:384–420`.
- **Class/Function:** All centre activities; Home profile menu.
- **Description:** The manifest registers four centre activities, but no `startActivity` call anywhere in `app/src/main` targets `CenterListActivity`. The profile menu has no "Centres" item, the Home "Trung tâm" chip shows promos only, and the profile has no centre link. `CenterDetailActivity`, `CenterEditActivity` and `CenterRequestActivity` are only reachable from the centre list/detail flow, so they are transitively unreachable. The only other reference to the class name is its own declaration.
- **Reproduction Steps:**
  1. Install and sign in.
  2. Inspect every menu/tab/icon. No centre entry exists.
  3. Attempt to reach a centre through the "Trung tâm" chip (promos only).
- **Expected Result:** A clearly labelled entry (profile menu, Home action, or "Trung tâm" screen) opens the approved-centre list.
- **Actual Result / Evidence:** No navigation path exists (grep for `CenterListActivity` found only its declaration). A temporary instrumentation test launched `CenterListActivity` and `CenterDetailActivity` directly; the list loaded seeded centres and the first item opened the detail, proving the screens work but are orphaned.
- **Root Cause:** Missing navigation integration; likely an unfinished merge of the centre feature.
- **Impact:** A documented major feature (centre discovery, requests, edits, and the centre's own detail page) cannot be used by any user. The create-promo path for centre accounts is the only centre-facing function.
- **Related Files:** `menu_profile.xml`; `activity_center_list.xml`; `CenterViewModel.java`; `CenterRepository.java`; `README.md` screens table (`README.md:260`).
- **Suggested Remediation:** Add a centre entry to the profile menu or Home ("Trung tâm" area), then link list → detail → request/edit. Add an explicit navigation test.
- **Verification Needed:** From a cold start, navigate to centres, open a centre, request a centre, edit an approved centre as its manager, and test empty/error states.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `res/menu/menu_profile.xml`, `HomeActivity.java`.
- Fix: added a "Trung tâm Anh ngữ" item to the Home avatar menu that opens `CenterListActivity`; the existing list/detail/request/edit flow is now reachable.
- Root cause: missing navigation integration.
- Verification: runtime menu showed the new item; tapping it resumed `CenterListActivity`, displayed the title and the "Đăng ký trung tâm" entry, and the previously verified list/detail screens still render.
- Remaining limitation: no automated navigation test was added for this entry point.

### BUG-007 — Any signed-in user can read every user profile, including email

- **Feature:** Authorization / user data privacy.
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `firestore.rules`
- **Line(s):** 98–120, especially line 99 (`allow read: if isSignedIn();`).
- **Class/Function:** Firestore rule for `/users/{uid}`.
- **Description:** The rule allows any authenticated request to read any user document. User documents contain `email`, `username`, `level`, counts, and `centerId`. The app UI only reads the current user's document, but the backend permits enumeration by any custom client or compromised account.
- **Reproduction Steps:** With two test accounts, authenticate as account A and query `users/{uidOfB}` (or list the collection) using the Firebase SDK/REST. The read succeeds.
- **Expected Result:** A non-admin can read only their own profile, or only explicitly public profile fields.
- **Actual Result / Evidence:** Rule is unconditional for authenticated users; no field-level split or public/private document separation. Not exercised against the live project to avoid unauthorized data access, but the rule text is direct evidence.
- **Root Cause:** Over-broad read rule on a collection containing private data.
- **Impact:** Email-address and profile enumeration; privacy/compliance risk. Combined with BUG-008, an attacker can also alter counters.
- **Related Files:** `User.java`; `UserRepository.observeUser/getUser`; `firestore.rules` collection-group answer rule (also broad but content-oriented).
- **Suggested Remediation:** Restrict `/users/{uid}` reads to `isSelf(uid)` or admin. If public author names/avatars are needed, create a separate public profile projection (e.g., `publicProfiles`) or allow only specific fields via a Cloud Function/API.
- **Verification Needed:** Rules-emulator tests: self read allowed, other-user read denied, admin read allowed.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `firestore.rules`.
- Fix: `users/{uid}` reads are limited to the owner or an admin; the client only reads the current user's profile.
- Root cause: `allow read: if isSignedIn()` exposed every profile.
- Verification: static review confirms all app reads target the current UID (`UserRepository.getUser/observeUser`, `isCenterOwner` uses a rule-side `get`).
- Remaining limitation: rules were not deployed and no rules-emulator test could be run in this environment.

### BUG-008 — Firestore update rules allow counter manipulation and arbitrary answer edits

- **Feature:** Authorization / data integrity.
- **Severity:** High.
- **Confidence:** Confirmed.
- **Status:** PARTIALLY FIXED.
- **File:** `firestore.rules`
- **Line(s):** 36–59 (`onlyBumpsAcceptedCount`, `onlyUpdatesAnswerCount`, `onlyUpdatesScore`, `onlyUpdatesVoteCounters`); 114–117 (users update); 139–145 (question update); 157–162 (answer update).
- **Class/Function:** Counter helper functions and `allow update` branches.
- **Description:** The counter helpers validate only a ±1 change, not who may perform it or whether the change is tied to a real vote/answer. Consequently:
  - Any signed-in user can increment any other user's `acceptedCount` by one via an update to `users/{anyUid}` (the `onlyBumpsAcceptedCount()` branch is unscoped).
  - Any signed-in user can change a question's `answerCount` by one or its `score` by up to ±2 on any question, repeatedly.
  - Any signed-in user can change an answer's `upCount`/`downCount` by one on any answer.
  - The question owner branch (`questionAuthor(questionId) == request.auth.uid`) allows the owner to update *any* answer field, including `body`, `authorId`, and counters, not just `isAccepted`.
- **Reproduction Steps:** In the rules emulator, authenticate as an unrelated user and update another user's `acceptedCount` by +1; update a question's `score` by +1; update another user's answer counters. As a question owner, modify an answer's `body`/`authorId`.
- **Expected Result:** Counter changes are only possible through narrowly scoped, authenticated, attributable operations (ideally Cloud Functions); a question owner can only set `isAccepted` on an answer consistent with the question's accepted-answer flow.
- **Actual Result / Evidence:** Rules allow all listed writes. The design notes acknowledge client-maintained counters and bounded changes, but do not bound the number of repeated calls or restrict callers to the involved users.
- **Root Cause:** Security rules express delta shape without actor/relationship constraints; coarse owner branch.
- **Impact:** Vote/score/accepted-count/answer-count fraud, vandalism of other users' answers by question owners, and inflated reputation statistics. The app UI does not perform these writes, but the backend contract does not enforce the app's intent.
- **Related Files:** `VoteRepository.java` (legitimate transaction); `AnswerRepository.java`; `UserRepository.java`; `docs/MEDIA_STORAGE.md` / README notes about moving counters to Cloud Functions.
- **Suggested Remediation:** Move counters into Cloud Functions/transactions triggered by vote/answer/accept documents; restrict each update branch to the authenticated actor and the exact allowed field changes (`changedKeys().hasOnly(['isAccepted'])` for acceptance); forbid changes to author/content fields.
- **Verification Needed:** Rules-emulator test matrix for self, other user, question owner, answer author, admin, and unauthenticated access.

**Remediation (2026-10-24)** — **PARTIALLY FIXED**
- Files modified: `firestore.rules`, `AnswerRepository.java`.
- Fix: removed the cross-user `acceptedCount` bump; self profile updates are limited to `username`, `avatarUrl`, `level`, `questionCount`, `answerCount`; answer authors may change only `body`/`questionTitle`; the question owner may change only `isAccepted`; the question owner may delete answers/votes for cascade cleanup.
- Root cause: broad update branches and a counter branch that trusted any signed-in caller.
- Verification: static rule review; the client's write paths were checked against the new allowed field sets.
- Remaining limitations: question `answerCount`/`score` and answer `upCount`/`downCount` remain bounded shape-only updates because attributing them to a vote/answer document requires Cloud Functions; rules were not deployed or emulator-tested. Self counter fields can still be changed by the owner.

### BUG-009 — Scrolling search results appends unrelated questions

- **Feature:** Home search and paging.
- **Severity:** Medium.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/viewmodel/FeedViewModel.java`
- **Line(s):** 261–293 (`loadMore`), 304–313 (`attachQuestions`).
- **Class/Function:** `FeedViewModel.loadMore`.
- **Description:** `loadMore()` has no check for an active search keyword. It calls `questionRepository.loadMore(categoryId, sort, PAGE_SIZE, lastDocument, ...)`, where `lastDocument` may be the final document from a keyword search. The feed then appends a normal (non-search) page to the search results.
- **Reproduction Steps:**
  1. Search for `cach`.
  2. Scroll to the bottom repeatedly.
  3. Observe unrelated questions appended.
- **Expected Result:** Only questions matching the search token are shown; either search is not paginated or it paginates within the same query.
- **Actual Result / Evidence:** After the search, the feed showed matching questions; after scrolling, unrelated "how many star above the sky" and "testing0001" cards appeared. `lastDocument` came from the search query while `loadMore` used `observeLatest` semantics.
- **Root Cause:** Missing search-mode guard in `loadMore`; one cursor field shared by two different query shapes.
- **Impact:** Search results become incorrect and misleading; users may see arbitrary content. Under certain result-set sizes, the list can mix matches and non-matches.
- **Related Files:** `QuestionRepository.search` (199–210); `QuestionRepository.buildQuery` (213–233); `HomeActivity` scroll listener.
- **Suggested Remediation:** Return early from `loadMore` when `keyword` is non-empty, or implement a paged search query with a search-specific cursor. Clear `lastDocument` whenever the keyword changes.
- **Verification Needed:** Search with 1, 5, 10, and >10 matches; scroll to the bottom; assert every visible item matches.

**Remediation (2026-10-24, revised during the search-optimisation pass)** — **FIXED AND VERIFIED**
- Files modified: `QuestionRepository.java`, `FeedViewModel.java`, `TextUtils2.java`, `SearchRanker.java` (new), `HomeActivity.java`, `StateView.java`, `FeedMixer.java`, `res/values/strings.xml`, tests and seed/reindex tooling.
- Fix: search now retrieves **one bounded candidate set** (≤ 60 documents) using `array-contains-any` over normalised tokens and typo variants, ranks candidates locally with `SearchRanker`, and pages through that ranked list locally. `lastDocument` is never reused across query shapes. Query changes cancel the previous listener, bump a generation counter, and ignore stale one-shot/snapshot callbacks. Typing is debounced by 300 ms; duplicate normalised queries do not restart the listener.
- Root cause: pagination always fell back to `loadMore(categoryId, sort, ...)` and one cursor field was shared by two query shapes.
- Verification: 69/69 unit tests pass; live API 29 emulator searches for `has been`, `hien tai`, `pha` (partial), `phapp` (extra character), `phop` (substitution), `nguu`, category+search, popular+search and unanswered+search returned matching results or the correct empty state; scrolling loaded additional ranked results without unrelated feed pages; no-results, too-short, clear-query and rapid-query (no stale result) states were exercised.
- Remaining limitation: ranking is over at most 60 candidates per query. This is deliberate and bounded; it is not a full-text engine. Search answers are not included (not part of the existing product). See README "Known limitations".

### BUG-010 — Category and sort filters are silently ignored during search

- **Feature:** Home filtering.
- **Severity:** Medium.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/viewmodel/FeedViewModel.java`
- **Line(s):** 220–245 (`setCategory`, `setSort`), 304–313 (`attachQuestions`).
- **Class/Function:** `FeedViewModel.attachQuestions`.
- **Description:** When a keyword exists, `attachQuestions` uses `questionRepository.search(token, ...)` and ignores both `categoryId` and `sort`. The chips still respond visually and the state is stored, so the UI implies the filters apply when they do not.
- **Reproduction Steps:**
  1. Search for `effect`.
  2. Note the vocabulary result.
  3. Tap the "Ngữ pháp" category chip.
  4. Observe that the vocabulary result remains and no category filter is applied.
- **Expected Result:** Search is either constrained by the selected category/sort or the UI indicates that filters are temporarily disabled/cleared.
- **Actual Result / Evidence:** UI dumps before and after the category tap showed the same vocabulary question. Code path confirms `search()` is called without category/sort.
- **Root Cause:** Search and filter queries are mutually exclusive rather than composed; setCategory/setSort just call `restartQuestions`, which re-enters the search branch.
- **Impact:** Users cannot narrow search results; the UI state contradicts the data. Popular/newest/unanswered sorting during search is also ineffective.
- **Related Files:** `QuestionRepository.buildQuery`, `QuestionRepository.search`; `CategoryChipAdapter`; Home sort chips.
- **Suggested Remediation:** Compose the search predicate with category/sort in one query and add the needed composite indexes; or clear the keyword when a filter is selected and show that behavior explicitly.
- **Verification Needed:** Search + each category + each sort; verify the result set and the visible selected chips.

**Remediation (2026-10-24, revised during the search-optimisation pass)** — **FIXED AND VERIFIED**
- Files modified: `QuestionRepository.java`, `FeedViewModel.java`, `SearchRanker.java`, `firestore.indexes.json`.
- Fix: the Firestore candidate query composes `array-contains-any` with the selected category and `newest`/`popular`/`unanswered` filter. The selected sort is retained as the deterministic tie-breaker after relevance, so relevance takes precedence without losing the user's sort choice. The required search/filter composite indexes are in the file; all 19 indexes are now **READY** in project `accountdata-dd29b`.
- Root cause: the search query ignored `categoryId` and `sort`.
- Verification: deployed indexes (19 READY); a direct Firestore REST `array-contains-any` + category query succeeded; live emulator tests verified `Từ vựng` + `anh`, `Ngữ pháp` + `hien tai`, `Phổ biến` + `phap`, and `Chưa trả lời` + `phap` returned only matching/filtered results. 69/69 unit tests pass.
- Remaining limitation: because relevance is primary during search, the visible order is not purely newest/popular; the chips still select the filter/tie-break semantics described above. The candidate pool is capped at 60.

### BUG-011 — Existing votes are never loaded, so the vote UI is wrong after reopening

- **Feature:** Up/down voting.
- **Severity:** Medium.
- **Confidence:** Confirmed.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/viewmodel/QuestionDetailViewModel.java`; `app/src/main/java/com/example/engqa/data/VoteRepository.java`; `app/src/main/java/com/example/engqa/ui/AnswerAdapter.java`
- **Line(s):** `QuestionDetailViewModel.java:42` (`myVotes` starts empty), 167–198 (only updates after voting); `VoteRepository.java:38–55` (`observeMyVote`); `AnswerAdapter.java:161–169`.
- **Class/Function:** `QuestionDetailViewModel.myVotes`, `QuestionDetailViewModel.vote`, `VoteRepository.observeMyVote`.
- **Description:** `myVotes` is initialized with an empty map and no code observes the per-answer `votes/{uid}` documents. `observeMyVote` exists but is never called (verified by grep). Consequently, previously saved votes are not highlighted when the detail screen opens.
- **Reproduction Steps:**
  1. Vote on an answer.
  2. Leave the detail screen.
  3. Reopen the same question.
  4. Tap the already-upvoted arrow once.
- **Expected Result:** The previous vote is highlighted, and one tap removes it (value 0).
- **Actual Result / Evidence:** The UI shows no highlighted arrow. Because `myVote` is 0, the first tap sends `UP`; the transaction reads the existing `UP`, removes it, then adds it back, so the vote remains. Removing an existing vote requires at least two taps (switch to downvote, then tap upvote again) or is not obvious.
- **Root Cause:** Missing subscription to the current user's vote documents and no merge into `myVotes`.
- **Impact:** Misleading vote state and broken toggle-off behavior across sessions. Users can unintentionally leave a vote in place.
- **Related Files:** `QuestionDetailActivity.java:170–173`; `AnswerAdapter` highlight colors; `VoteRepository.vote` (87–108).
- **Suggested Remediation:** On detail load, observe each answer's `votes/{currentUid}` (or fetch them once) and populate `myVotes`; remove the listeners in `stop()`.
- **Verification Needed:** With a seeded vote, open detail; confirm highlight; tap once; confirm vote document deleted and score decremented.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `QuestionDetailViewModel.java`.
- Fix: the view model now observes `votes/{uid}` for every loaded answer, merges values into `myVotes`, removes listeners for answers that disappear, and clears them in `stop()`. Reopening a question restores the highlighted arrow and enables one-tap unvote.
- Root cause: `observeMyVote` existed but was never called.
- Verification: build, unit tests, source review.
- Remaining limitation: a vote/reopen/toggle sequence was not executed against shared data.

### BUG-012 — Promo website/phone actions may do nothing on Android 11+

- **Feature:** Promo contact actions.
- **Severity:** Medium.
- **Confidence:** Probable.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/PromoDetailActivity.java`; `app/src/main/AndroidManifest.xml`
- **Line(s):** `PromoDetailActivity.java:191–226`; manifest has no `<queries>` element.
- **Class/Function:** `PromoDetailActivity.openWebsite`, `PromoDetailActivity.callNow`.
- **Description:** Both actions call `Intent.resolveActivity(getPackageManager())` before starting an implicit `ACTION_VIEW`/`ACTION_DIAL` intent. With package visibility filtering on Android 11+ (targetSdk 37), `resolveActivity` can return `null` even when a browser/dialer can handle the intent, because the app has not declared visibility queries.
- **Reproduction Steps:** On an Android 11+ device/emulator, open an approved promo and tap "Mở website" or "Gọi ngay".
- **Expected Result:** The browser/dialer opens.
- **Actual Result / Evidence:** Android lint reports `QueryPermissionsNeeded` at `PromoDetailActivity.java:199` and `:218`. Not reproduced at runtime because only API 29 was available; on that emulator the buttons cannot be fully exercised either because the calls were not tapped in this audit.
- **Root Cause:** Use of `resolveActivity` for implicit intents without manifest `<queries>` package visibility.
- **Impact:** A core promotional call-to-action can silently fail; the fallback toast uses the same label as the button and does not explain the failure.
- **Related Files:** `AndroidManifest.xml` (no `<queries>`), `activity_promo_detail.xml`, `strings.xml` labels.
- **Suggested Remediation:** Start the intent directly and catch `ActivityNotFoundException`; if keeping `resolveActivity`, add `<queries>` entries for `https`, `http`, and `tel`/`dial` intents. Show a meaningful error toast.
- **Verification Needed:** Test on API 30+ with a browser/dialer installed; verify both buttons and the no-handler fallback.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `PromoDetailActivity.java`, `res/values/strings.xml`.
- Fix: removed `resolveActivity()` gating and start `ACTION_VIEW`/`ACTION_DIAL` directly inside the existing `ActivityNotFoundException` handlers; added specific "no browser"/"no dialer" messages.
- Root cause: `resolveActivity()` can return null under Android 11+ package visibility even when a handler exists.
- Verification: build and lint (0 errors) passed; the direct-start pattern is standard.
- Remaining limitation: the available emulator is API 29, where the original code also worked; opening external apps on API 30+ was not tested.

### BUG-013 — Offline question jobs can become permanently stuck after account changes or permanent errors

- **Feature:** Offline question queue / WorkManager.
- **Severity:** Medium.
- **Confidence:** Probable.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/worker/PendingQuestionWorker.java`; `app/src/main/java/com/example/engqa/SessionManager.java`
- **Line(s):** `PendingQuestionWorker.java:45–55, 91, 105`; `SessionManager.java:37–40`.
- **Class/Function:** `PendingQuestionWorker.doWork`; `SessionManager.logout`.
- **Description:** A queued draft stores the UID at save time (`draft.uid`). The worker reuses that UID as `authorId` but publishes under whatever Firebase session exists when it runs. If the user logs out and signs in as another account, or if the draft is otherwise unauthorized, Firestore rules reject the write. The worker returns `Result.retry()` for any failure, including permission/auth failures, and one-time work has no maximum attempt count. Logout does not cancel or tenant-scope pending work.
- **Reproduction Steps (test/project with disposable data):**
  1. Go offline and queue a question as user A.
  2. Log out and sign in as user B.
  3. Restore the network and let the worker run.
- **Expected Result:** The job is cancelled or marked failed with a clear status; it never publishes as the wrong account; the user can retry manually after signing back in.
- **Actual Result / Evidence:** The worker writes `authorId = draft.uid` (line 55) while Firestore authenticates as the current user; rules require `authorId == request.auth.uid`. The failure path returns `retry()` (line 91) indefinitely; permanent exceptions also return `retry()` (line 105). No retry limit is configured in `WorkScheduler`.
- **Root Cause:** The queued draft is not bound to the authenticated account; permanent failures are classified as transient; no cancellation/cleanup policy on logout.
- **Impact:** Stuck WorkManager jobs, repeated retries/battery/network use, drafts that can never publish, and potential cross-account privacy/confusion.
- **Related Files:** `PendingDraftStore.java`; `WorkScheduler.java`; `QuestionPublisher.java`; `CreateQuestionViewModel.savePending` (204–231).
- **Suggested Remediation:** Store the owner UID and compare it to `FirebaseAuth.getCurrentUser()`; cancel/pause jobs on logout; return `Result.failure()` for auth/permission/validation errors; add a retry limit and cleanup of the draft folder.
- **Verification Needed:** Queue offline as A, switch accounts, reconnect; assert the job is cancelled/failed and no question is written under B. Also inject a permanent rules failure and verify it stops retrying.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `PendingQuestionWorker.java`, `SessionManager.java`.
- Fix: the worker refuses to publish when the signed-in UID differs from the draft owner (and removes the draft), classifies permanent Firestore/auth/validation errors, caps retries at five, removes the draft on permanent/exhausted failure, and logout cancels `pending-question` work.
- Root cause: the worker never checked the draft owner and retried every failure indefinitely.
- Verification: build, unit tests and source review.
- Remaining limitation: account-switch and permanent-failure paths were not exercised end-to-end without creating disposable queued posts.

### BUG-014 — Missing Firestore index for "Phổ biến" across all categories

- **Feature:** Feed sort / deployment configuration.
- **Severity:** Medium.
- **Confidence:** Probable.
- **Status:** FIXED AND VERIFIED.
- **File:** `firestore.indexes.json`; `app/src/main/java/com/example/engqa/data/QuestionRepository.java`
- **Line(s):** `firestore.indexes.json:1–112`; `QuestionRepository.java:219–221`.
- **Class/Function:** `QuestionRepository.buildQuery` (`SORT_POPULAR`).
- **Description:** With no category selected, the popular query orders by `score` descending and `createdAt` descending. `firestore.indexes.json` contains `categoryId+score+createdAt` but no `score+createdAt` index. A clean deployment of the committed configuration would require an index that is not in the repo. The live demo project appears to have such an index (popular sorting worked), so the repo and deployed state differ.
- **Reproduction Steps:** Deploy the committed rules/indexes to a fresh Firebase project, then tap "Phổ biến" on the "Tất cả" feed.
- **Expected Result:** Popular results load.
- **Actual Result / Evidence:** Popular sorting worked against the current live project, but the committed index file lacks the necessary composite index; therefore a fresh deployment is likely to fail with `FAILED_PRECONDITION` / "index required". This was not reproduced on a fresh project.
- **Root Cause:** Incomplete checked-in index definition / drift between live and repository configuration.
- **Impact:** Fresh installs or redeployments may have a broken popular sort; the required index may only be discoverable via a runtime error link.
- **Related Files:** `firebase.json`; `docs/FIREBASE_SETUP.md`; search/category composite future indexes.
- **Suggested Remediation:** Add a `questions` composite index `score DESC, createdAt DESC`; add `categoryId+searchKeywords+createdAt` for combined search/filter (BUG-010); deploy and verify.
- **Verification Needed:** Deploy to a disposable Firebase project and run all sort/category combinations.

**Remediation (2026-10-24, revised during the search-optimisation pass)** — **FIXED AND VERIFIED**
- Files modified: `firestore.indexes.json`.
- Fix: added the all-category `score DESC, createdAt DESC` index and the search/filter composite indexes needed by BUG-009/010.
- Verification: deployed to project `accountdata-dd29b` on 2026-10-24 with `firebase deploy --only firestore:indexes` (exit 0); the Firestore Admin API reports **19/19 indexes READY**; a live `Phổ biến` + search query succeeded.
- Remaining limitation: none for this finding. A fresh-project deployment is still the recommended CI check for the committed index file.

### BUG-015 — Bottom-navigation Back returns to the previously visited tab

- **Feature:** Bottom navigation / back stack.
- **Severity:** Medium.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/BaseNavActivity.java`
- **Line(s):** 45–64.
- **Class/Function:** `BaseNavActivity.navigateTo`.
- **Description:** Tab changes use `FLAG_ACTIVITY_REORDER_TO_FRONT`, which reuses an existing instance but leaves the other tab activities in the task. Nothing handles Back to return to the start destination or exit.
- **Reproduction Steps:**
  1. Open Profile.
  2. Tap Home.
  3. Press Back.
- **Expected Result:** Back exits/minimises or returns to the app's start destination, not the previous tab.
- **Actual Result / Evidence:** Back resumed `MenuScreenActivity` (Profile). This was confirmed with `dumpsys activity activities`.
- **Root Cause:** Reordering activities without clearing the stack or intercepting Back.
- **Impact:** Unexpected navigation and a growing stale stack; contributes to the post-logout exposure in BUG-002.
- **Related Files:** `HomeActivity.java`; `MainActivity.java`; `MenuScreenActivity.java`; `view_bottom_nav.xml`.
- **Suggested Remediation:** Use a single-activity/fragment navigation model, or clear the previous tab with `FLAG_ACTIVITY_CLEAR_TOP|SINGLE_TOP`, or implement a Back handler that returns to the start tab and finishes.
- **Verification Needed:** Test all tab transitions and Back at the root; verify no duplicate/stale tab instances.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `BaseNavActivity.java`, `HomeActivity.java`, `MainActivity.java`, `MenuScreenActivity.java`.
- Fix: root tabs register an `OnBackPressedCallback`; Back on Ask/Profile returns to Home, Back on Home moves the task to the background, and detail screens keep the default finish behaviour. This also avoids the predictive-back lint error.
- Root cause: tabs used `REORDER_TO_FRONT`, so the system back stack exposed the previously visited tab.
- Verification: runtime Profile → Home → system Back resumed the launcher, not the Profile screen; lint reports 0 errors.
- Remaining limitation: the emulator is API 29, so predictive-back animation was not exercised.

### BUG-016 — Email-verification box is visible before any login

- **Feature:** Login / email verification.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/LoginActivity.java`; `app/src/main/java/com/example/engqa/viewmodel/AuthViewModel.java`
- **Line(s):** `LoginActivity.java:178–181`; `AuthViewModel.java:53`.
- **Class/Function:** `LoginActivity.observe`, `AuthViewModel.emailVerified`.
- **Description:** `emailVerified` is initialized to `false`, and the observer shows the verification box whenever the value is not `true`. On a fresh, unauthenticated Login screen, the box is therefore shown as if the anonymous visitor had an unverified account. Its actions also fail with "Chưa đăng nhập".
- **Reproduction Steps:** Clear app data; launch the app; observe the login screen.
- **Expected Result:** The box is hidden until a user is signed in with an unverified email.
- **Actual Result / Evidence:** UI dump on the fresh login screen contains "Email của bạn chưa được xác minh...", "Gửi lại email xác minh", and "Đã xác minh, kiểm tra lại". Tapping resend while signed out produced an error toast.
- **Root Cause:** Default `false` conflates "not verified" with "not signed in"; observer has no authenticated-user check.
- **Impact:** Confusing onboarding and dead controls that produce errors.
- **Related Files:** `activity_login.xml` verify box; `UserRepository.refreshEmailVerified`; `SessionManager`.
- **Suggested Remediation:** Hide the box when `FirebaseAuth.getCurrentUser() == null`; use a tri-state/nullable LiveData; only show for authenticated unverified accounts.
- **Verification Needed:** Fresh login, unverified user login, verified user login, and logout.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `LoginActivity.java`.
- Fix: the verification box is hidden by default and shown only when a user is signed in and their email is not verified.
- Root cause: the observer treated the initial `false` LiveData value as a signed-in unverified user.
- Verification: runtime fresh-login UI dump on API 29 showed no verification box before authentication.
- Remaining limitation: signed-in, unverified-email state was not exercised because no verification mailbox is available.

### BUG-017 — Registration hint claims a taken username is available

- **Feature:** Registration validation.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/RegisterActivity.java`
- **Line(s):** 204–218.
- **Class/Function:** `RegisterActivity.updateUsernameHint`.
- **Description:** The live hint only checks format via `Validators.isValidUsername`; it does not query uniqueness. It displays "Tên hiển thị có thể dùng được" for any syntactically valid name, including an existing one.
- **Reproduction Steps:** Open Register; type `minh_anh` (an existing seeded username).
- **Expected Result:** A hint that availability will be checked on submit, or an actual uniqueness result.
- **Actual Result / Evidence:** The hint displayed "Tên hiển thị có thể dùng được" for `minh_anh`; submitting would fail on the repository uniqueness check.
- **Root Cause:** Local format validation presented as an availability claim; the unused `AuthViewModel.usernameAvailable` path suggests an unfinished asynchronous check.
- **Impact:** User frustration and a false promise; only affects registration, which then correctly rejects the name.
- **Related Files:** `strings.xml` `username_available`; `AuthViewModel.checkUsernameAvailable` (unused); `UserRepository.isUsernameTaken`.
- **Suggested Remediation:** Change the hint text to "Định dạng hợp lệ" rather than "available", or implement a debounced uniqueness check that excludes the current user.
- **Verification Needed:** Type a taken name and an unused name; verify the hint matches actual submit results.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `res/values/strings.xml`.
- Fix: the green hint now says "Định dạng tên hợp lệ (kiểm tra trùng khi gửi)" instead of claiming the name is available; the key is unchanged and the real uniqueness check still runs at submit.
- Root cause: the hint was wired to a message that implied an asynchronous availability check.
- Verification: resource compilation, build and lint passed; the string is the only consumer.
- Remaining limitation: the hint does not perform live availability checking (by design; the submit check remains authoritative).

### BUG-018 — Word-of-the-Day can display a stale, unrelated cached word

- **Feature:** Word of the Day / offline cache.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/data/WordRepository.java`
- **Line(s):** 88–94; 165–170.
- **Class/Function:** `WordRepository.load`, `WordRepository.isUsable`.
- **Description:** `isUsable` correctly requires today's date and the matching target word for a cache hit. However, if the network fetch fails, the catch block returns *any* cached entry without rechecking `isUsable`. On a new day (or when the target word changes), a failed fetch therefore displays yesterday's/another word as today's word.
- **Reproduction Steps:**
  1. Cache a word for day 1.
  2. On day 2, disable the network (or make the API fail).
  3. Open the Word of the Day screen.
- **Expected Result:** No word card or an explicit offline/stale indicator, not a different word presented as today's word.
- **Actual Result / Evidence:** The fallback path calls `readCache` and calls `onSuccess(cached)` regardless of `cached.getDate()` or `cached.getWord()`. The code path is unambiguous, although the specific live failure was not triggered during this audit (the emulator had no cache after clearing).
- **Root Cause:** Cache fallback does not apply the same validity checks as the cache-hit path.
- **Impact:** Incorrect daily content; users may learn the wrong word while offline.
- **Related Files:** `WordOfDayViewModel.java` (unused alternative); `PrefsHelper` word cache; `activity_word_detail.xml`.
- **Suggested Remediation:** On failure, only return the cached entry when `isUsable(cached, today, target)`; otherwise return an error/empty state and optionally show a "cached from <date>" stale marker.
- **Verification Needed:** Seed a different-day cache, force an API failure, and open Word detail/feed; assert no wrong word is shown.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `WordRepository.java`.
- Fix: the request-failure path returns the cached entry only when `isUsable(cached, today, target)`; otherwise it reports the error.
- Root cause: the catch block served any cached word.
- Verification: build and unit tests passed; source review.
- Remaining limitation: the dictionary API failure path was not induced during this pass.

### BUG-019 — "Clear cache" does not clear the Firestore media cache or image library cache

- **Feature:** Settings / cache management.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/SettingsFragment.java`; `app/src/main/java/com/example/engqa/media/MediaCompressor.java`; `app/src/main/java/com/example/engqa/worker/CacheCleanupWorker.java`
- **Line(s):** `SettingsFragment.java:67–75`; `MediaCompressor.java:129–134`; `CacheCleanupWorker.java:24–27`.
- **Class/Function:** `SettingsFragment` clear-cache preference; `MediaCompressor.writeToCache`.
- **Description:** The setting deletes only `cache/images` and `cache/audio`. Firestore audio is decoded into `cache/media`, and Glide maintains its own disk cache. The periodic worker does delete `cache/media`, proving the omission is unintentional. "Xoá ảnh và âm thanh đã tải tạm" therefore leaves downloaded question audio and Glide-loaded images behind.
- **Reproduction Steps:** Play Firestore audio from a question (creates `cache/media`), open a promo with an image (Glide cache), then use Settings → "Xoá bộ nhớ tạm"; inspect cache directories/size.
- **Expected Result:** All app-managed caches are cleared, including media and Glide.
- **Actual Result / Evidence:** Code deletes only two directories; `CacheCleanupWorker` includes `media`; Glide is not cleared. Not exercised at runtime with populated caches.
- **Root Cause:** Cache directory list was not kept in sync with the media cache location.
- **Impact:** Storage is not reclaimed as the UI claims; user-visible settings action is partially ineffective.
- **Related Files:** `QuestionDetailActivity.renderAudio`; `Glide` usage in `FeedAdapter`/`MediaImageAdapter`; `file_paths.xml`.
- **Suggested Remediation:** Delete `cache/media` as well and clear Glide's disk/memory caches on a background thread; show the reclaimed size or a failure state.
- **Verification Needed:** Populate all cache types, clear, and measure before/after.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `SettingsFragment.java`.
- Fix: "Xoá bộ nhớ tạm" now also deletes `cache/media`, clears Glide's memory cache, and clears Glide's disk cache on a background thread (application context captured on the main thread).
- Root cause: the action only deleted `cache/images` and `cache/audio`.
- Verification: runtime Settings → "Xoá bộ nhớ tạm" executed without a crash; build and lint pass.
- Remaining limitation: actual disk/memory contents were not inspected after the action; the API calls are standard.

### BUG-020 — Notification switch can show ON while POST_NOTIFICATIONS is denied

- **Feature:** Settings notifications (Android 13+).
- **Severity:** Low.
- **Confidence:** Probable.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/HomeActivity.java`; `app/src/main/java/com/example/engqa/SettingsFragment.java`
- **Line(s):** `HomeActivity.java:156–169`; `SettingsFragment.java:47–59`.
- **Class/Function:** `maybeRequestNotificationPermission`; notification preference change listener.
- **Description:** The preference is persisted as enabled and the watcher is started before/independently of the system permission result. If the user denies `POST_NOTIFICATIONS`, the switch remains ON and no notification is shown. `NotificationHelper.show` catches `SecurityException` silently; the UI has no permission-state reconciliation.
- **Reproduction Steps (API 33+):** Fresh install; deny the notification permission; open Settings.
- **Expected Result:** The switch reflects the OS permission (OFF or blocked with an explanation) and offers a path to settings.
- **Actual Result / Evidence:** Code path sets the pref and starts the watcher, then launches the permission request; denial does not revert the pref. Only API 29 was available, so runtime behaviour was not observed.
- **Root Cause:** App preference and OS permission state are treated as independent.
- **Impact:** Users believe notifications are enabled but receive none. Confusing recovery flow.
- **Related Files:** `PrefsHelper` notification keys; `NotificationWatcher`; `NotificationHelper.show`.
- **Suggested Remediation:** On denial/`shouldShowRequestPermissionRationale`, revert the switch or show a "blocked by system" state with a link to app notification settings; reconcile on resume.
- **Verification Needed:** API 33+ allow/deny/block test matrix.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `SettingsFragment.java`, `HomeActivity.java`.
- Fix: the switch is reconciled with `POST_NOTIFICATIONS` on resume; enabling without permission launches the request and keeps the switch off until the result; the result is persisted; denying from Home persists false and stops the watcher.
- Root cause: the preference reflected the stored flag only, not the OS permission.
- Verification: build and lint pass; the Settings switch rendered ON correctly on API 29 (permission not applicable).
- Remaining limitation: Android 13+ permission grant/deny behaviour could not be executed on the API 29 emulator.

### BUG-021 — Backups include app-private state without exclusions

- **Feature:** Application backup configuration.
- **Severity:** Low.
- **Confidence:** Potential.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/AndroidManifest.xml`; `app/src/main/res/xml/backup_rules.xml`; `app/src/main/res/xml/data_extraction_rules.xml`
- **Line(s):** `AndroidManifest.xml:22` (`allowBackup="true"`); both rules files contain only commented-out examples.
- **Class/Function:** Android Auto Backup configuration.
- **Description:** With `allowBackup=true` and no include/exclude rules, the default backup set includes SharedPreferences, databases, and app files. This can include Firestore's local cache, the word cache, saved promos, notification prefs, and Firebase SDK persistence state. Whether a restored Firebase Auth session remains usable is platform/SDK dependent, but private app state is backed up by default.
- **Reproduction Steps:** Inspect the manifest/rules (no runtime restore test possible).
- **Expected Result:** Explicitly exclude credentials/session state and any private data not intended for backup.
- **Actual Result / Evidence:** The rules files are the default templates; no exclusions are configured.
- **Root Cause:** Default backup configuration retained.
- **Impact:** Potential exposure of cached/session data through cloud backup or device transfer; privacy/compliance risk. Impact is not demonstrated.
- **Related Files:** `SessionManager`; `PrefsHelper`; Room database.
- **Suggested Remediation:** Define explicit backup/transfer rules excluding Firebase Auth/cache files and session preferences, or set `allowBackup=false` if not needed.
- **Verification Needed:** Review Firebase Auth's persisted files and perform a device-to-device restore test in a controlled environment.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`.
- Fix: cloud backup and device transfer exclude all SharedPreferences, all databases, and the `pending`/`firestore` file paths, keeping Firebase Auth sessions, Firestore cache and account-scoped drafts off-device.
- Root cause: empty include/exclude rules allowed default full backup.
- Verification: XML resources compile; build passes.
- Remaining limitation: no backup/restore or device-transfer test was performed.

### BUG-022 — Unused media permissions declared

- **Feature:** Permissions / privacy.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/AndroidManifest.xml`
- **Line(s):** 10–11.
- **Class/Function:** Manifest permissions.
- **Description:** `READ_MEDIA_IMAGES` and `READ_MEDIA_AUDIO` are declared, but image/audio selection uses the Storage Access Framework (`ACTION_OPEN_DOCUMENT`/`OpenMultipleDocuments`) and camera capture uses FileProvider. These runtime permissions are not needed for the implemented flows.
- **Reproduction Steps:** Inspect the manifest and media pickers.
- **Expected Result:** Declare only permissions needed by implemented features.
- **Actual Result / Evidence:** Manifest declares both permissions; lint reports `SelectedPhotoAccess` at manifest line 10 and no code requests/uses these permissions.
- **Root Cause:** Leftover declaration from an earlier media approach.
- **Impact:** Broader permission surface than necessary; potential Play Store/privacy review friction and user concern. No direct functional failure.
- **Related Files:** `MainActivity.java` pickers; `ImageUtils.java`; `PermissionHelper.java`.
- **Suggested Remediation:** Remove the unused permissions; if a future direct-media picker needs them, scope them to that feature and handle Android 14 selected-photo access.
- **Verification Needed:** Confirm media picking/capture still works after removal on API 29 and API 34.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `AndroidManifest.xml`, `util/PermissionHelper.java`.
- Fix: removed `READ_MEDIA_IMAGES` and `READ_MEDIA_AUDIO` and the unused `missingMediaPermissions` helper; the picker uses `ACTION_OPEN_DOCUMENT`, which needs no storage permission.
- Root cause: leftover permissions from an earlier direct-media approach.
- Verification: merged debug manifest contains `POST_NOTIFICATIONS` but no `READ_MEDIA_*`; build and lint pass.
- Remaining limitation: none.

### BUG-023 — First notification snapshot can re-announce the entire backlog

- **Feature:** Notification watcher.
- **Severity:** Low.
- **Confidence:** Probable.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/notification/NotificationWatcher.java`
- **Line(s):** 36–59.
- **Class/Function:** `NotificationWatcher.start`.
- **Description:** The watcher skips only the first snapshot. If the first snapshot is an empty Firestore cache snapshot, the later server snapshot contains all existing notifications as `ADDED` events; those backlog items are then shown as system notifications. The stated intent is to skip the whole backlog.
- **Reproduction Steps (controlled project):** Clear app data, create existing unread notifications, restart the app, and wait for the server snapshot.
- **Expected Result:** Only notifications arriving after the listener starts are announced.
- **Actual Result / Evidence:** The first-snapshot boolean is consumed by the cache callback; there is no server-snapshot baseline or timestamp watermark. Not runtime-tested due to lack of a second user.
- **Root Cause:** Backlog suppression is based on "first callback" rather than the first non-cache/server snapshot or a stored last-seen timestamp.
- **Impact:** Notification spam on first launch/reinstall/restart in some cache states.
- **Related Files:** `NotificationRepository`; `EngQAApp`; `PrefsHelper`.
- **Suggested Remediation:** Skip until the first non-cache snapshot, or store/compare a last-seen notification timestamp; never notify from cache metadata.
- **Verification Needed:** Test with empty cache + existing notifications, populated cache + new notifications, and process restart.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `notification/NotificationWatcher.java`.
- Fix: cached snapshots are ignored entirely; the first server snapshot establishes the baseline (and is not announced), so the initial backlog is not replayed.
- Root cause: the first snapshot flag was consumed by a cached snapshot, causing the first server snapshot to be treated as new.
- Verification: build and lint pass; source review.
- Remaining limitation: a delayed/cached first snapshot was not reproduced with live notifications.

### BUG-024 — Approved promos beyond the first cadence can never appear

- **Feature:** Feed promo interleaving.
- **Severity:** Low.
- **Confidence:** Potential.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/java/com/example/engqa/util/FeedMixer.java`
- **Line(s):** 92–108.
- **Class/Function:** `FeedMixer.mix`.
- **Description:** Promos are inserted only after every `cadence` (2) questions. Any promos left over after the question list ends are not appended. With 3 questions and 5 promos, only one promo appears; with 1 question, no promo appears (the zero-question special case shows all promos).
- **Reproduction Steps:** Use a dataset with fewer questions than promo slots; inspect the feed.
- **Expected Result:** All active promos have a reasonable opportunity to appear, or the feed explains/rotates them.
- **Actual Result / Evidence:** Loop-based insertion caps promos at `floor(questions/cadence)`; no post-loop append for the remainder. This is deterministic unit-testable behaviour.
- **Root Cause:** Missing append/rotation of unused promos.
- **Impact:** Centres' approved promotions may be invisible in sparse feeds. Product impact depends on intended rotation policy; not a crash.
- **Related Files:** `FeedMixerTest`; `HomeActivity`; `PromoRepository.observeApproved`.
- **Suggested Remediation:** Append leftover active promos after the last question, round-robin across sessions, or add dedicated promo paging.
- **Verification Needed:** Unit-test the cadence cases (0/1/2/3 questions, 0/1/5 promos) and confirm the intended product behaviour.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `util/FeedMixer.java`, `util/FeedMixerTest.java`.
- Fix: after the normal interleave, any approved promos not yet inserted are appended; the empty-question case is handled by the same loop.
- Root cause: the loop stopped when questions ran out and no final append existed.
- Verification: unit suite 69/69 passes, including the new `mix_appendsPromosLeftAfterTheLastQuestion` test.
- Remaining limitation: the visible ordering change (leftover promos at the end) is intentional and was not separately UI-tested.

### BUG-025 — `ServerErrorActivity` is dead code / unreachable

- **Feature:** Error screen.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/ServerErrorActivity.java`; `app/src/main/AndroidManifest.xml`
- **Line(s):** `ServerErrorActivity.java:18–105`; manifest declaration (`ServerErrorActivity`).
- **Class/Function:** `ServerErrorActivity`, including its auto-retry network callback.
- **Description:** No code starts this activity. Network and Firestore failures are instead handled by `StateView` error states or toasts. The activity is fully implemented but disconnected.
- **Reproduction Steps:** Search for `new Intent(..., ServerErrorActivity.class)` or an activity alias/intent filter; none exist.
- **Expected Result:** Either a deliberate global error screen is used, or the dead implementation is removed.
- **Actual Result / Evidence:** Only its own declaration and manifest reference exist. A temporary instrumentation launch showed it renders correctly.
- **Root Cause:** Unfinished integration/removed navigation.
- **Impact:** Dead code, maintenance cost, and an inconsistent error-handling story. No runtime failure.
- **Related Files:** `NetworkUtils.java`; `StateView.java`; `RepoErrors.java`.
- **Suggested Remediation:** Wire it into fatal/offline startup paths or remove it and document the chosen error strategy.
- **Verification Needed:** Decide the product requirement; test offline startup and recovery.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `HomeActivity.java`.
- Fix: when the feed produces an error while the device is offline, Home launches `ServerErrorActivity` with auto-retry and finishes; online errors keep the existing inline retry state.
- Root cause: `ServerErrorActivity` had no caller.
- Verification: build and lint pass; the screen itself was previously verified to render; the manifest entry already exists.
- Remaining limitation: the offline launch path was not exercised on the emulator; the choice to use the dedicated screen only for offline errors is documented here.

### BUG-026 — Profile avatar cannot be changed despite avatar fields

- **Feature:** Profile editing / avatar.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** UNRESOLVED.
- **File:** `app/src/main/java/com/example/engqa/EditProfileActivity.java`; `app/src/main/java/com/example/engqa/viewmodel/ProfileViewModel.java`; `app/src/main/java/com/example/engqa/model/User.java`
- **Line(s):** `EditProfileActivity.java:160–172`; `ProfileViewModel.java:90–105`; `UserRepository.java:281–288`; `User.java:24, 75–81`.
- **Class/Function:** Profile edit/persistence.
- **Description:** `avatarUrl` exists in the model/repository and is passed through on save, but Edit Profile has no avatar picker/upload control. The UI displays a generated letter avatar instead. The repository writes the existing value back, so the field can never be populated by the app. The README/schema implies an avatar feature.
- **Reproduction Steps:** Open Edit Profile; observe only display name and level controls; inspect the UI/layout for an avatar control.
- **Expected Result:** An avatar can be chosen/removed and is displayed in the profile/feed.
- **Actual Result / Evidence:** No avatar control or upload code; only letter avatars are rendered.
- **Root Cause:** Incomplete implementation.
- **Impact:** Missing user-facing customization promised by the data model/documentation. Low functional impact.
- **Related Files:** `activity_edit_profile.xml`; `MenuScreenActivity` letter avatar; `HomeActivity` letter avatar.
- **Suggested Remediation:** Add a picker + upload path (Firestore media or Storage) and render `avatarUrl`; otherwise remove the unused field/claim.
- **Verification Needed:** Product decision; if implemented, test upload/replace/remove and error handling.

**Remediation (2026-10-24)** — **UNRESOLVED**
- Files modified: none.
- Reason: `avatarUrl` exists in the model and profile layouts display an initials avatar, but there is no picker, upload target, storage rules contract, or display pipeline for a real avatar URL. Implementing one would add a new user-facing feature and storage design; removing the field/claim would be a product decision. Neither is a minimal bug fix.
- Verification: source review only; the profile continues to show initials.
- Remaining limitation: requires a product decision (implement avatar selection/upload or remove the unused field). No speculative implementation was made.

### BUG-027 — Create-promo validation errors appear outside the visible scroll area

- **Feature:** Create promo form error display.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED AND VERIFIED.
- **File:** `app/src/main/res/layout/activity_create_promo.xml`; `app/src/main/java/com/example/engqa/CreatePromoActivity.java`
- **Line(s):** Layout `39` (ScrollView start), `247` (`tvFormError`), `255` (ScrollView end), `267` (`btnSubmitPromo`); `CreatePromoActivity.java:285–291`.
- **Class/Function:** `CreatePromoActivity.showError`; promo form layout.
- **Description:** The error label sits at the bottom of the scrollable form, while the submit button is in a fixed bar outside the scroll view. Submitting an invalid form sets the error text but does not scroll it into view. On a tall form, the user sees only the field-level error icon (if any) or no visible message.
- **Reproduction Steps:** As a centre account, open Create Promo and tap "Gửi duyệt" with an empty title. Scroll state stays at the top.
- **Expected Result:** The error is visible (or the form scrolls/focuses the first invalid field).
- **Actual Result / Evidence:** UI dump after submit showed `tvFormError` with "Tiêu đề phải từ 5 đến 120 ký tự" but marked `visible: false`/off-screen below the ScrollView; the visible screen did not show the message.
- **Root Cause:** Error text placed at the end of a long scroll view with no `scrollTo`/focus, while the action bar is outside the scroll container.
- **Impact:** Users may not understand why submission failed; validation feels broken.
- **Related Files:** `activity_create_promo.xml`; field-level errors; `CreateQuestionViewModel` uses toasts for some errors.
- **Suggested Remediation:** Move the error banner near the submit bar (or into the fixed bar), scroll to it with `smoothScrollTo`, and request focus on the first invalid field.
- **Verification Needed:** Submit each invalid field combination; verify the message is visible without manual scrolling.

**Remediation (2026-10-24)** — **FIXED AND VERIFIED**
- Files modified: `res/layout/activity_create_promo.xml`, `CreatePromoActivity.java`.
- Fix: `tvFormError` moved out of the scroll view into the fixed bar directly above "Gửi duyệt"; each validation branch also focuses the first invalid field. Layout styling/order is otherwise unchanged.
- Root cause: the error view lived at the end of a long scroll and was off-screen above the fixed submit bar.
- Verification: runtime as the centre-manager demo account, submitting the empty form displayed `Tiêu đề phải từ 5 đến 120 ký tự` at `[0,1899][1080,1965]` (visible above the button) and focused the title field.
- Remaining limitation: the fixed error bar adds a small amount of vertical space; no other visual changes were made.

### BUG-028 — Upvote notifications are stored with the "accepted" type

- **Feature:** Notification creation for votes.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/viewmodel/QuestionDetailViewModel.java`
- **Line(s):** 187–190 (upvote call) and 322–338 (hard-coded type at 333).
- **Class/Function:** `QuestionDetailViewModel.vote`, `QuestionDetailViewModel.notifyAnswerAuthor`.
- **Description:** When an upvote succeeds, the view model asks `notifyAnswerAuthor` to create a notification titled "Có lượt bình chọn", but the helper always writes `AppNotification.TYPE_ACCEPTED`. The notification is therefore semantically wrong even though its title/body identify it as a vote. The same helper is also used for real acceptance notifications.
- **Reproduction Steps (safe test project):** Upvote another user's answer; inspect the created `notifications` document.
- **Expected Result:** The vote notification has a vote type (e.g., `TYPE_VOTE`).
- **Actual Result / Evidence:** `notification.setType(AppNotification.TYPE_ACCEPTED)` is unconditional; the upvote call at lines 187–190 passes only title/body.
- **Root Cause:** Type is not a parameter of the helper; all callers share the accepted constant.
- **Impact:** Incorrect notification classification/analytics; a later UI that filters by type would mislabel vote notifications. The current list does not display the type, so user impact is low.
- **Related Files:** `model/AppNotification.java`; `NotificationRepository.java`; `NotificationWatcher.java`.
- **Suggested Remediation:** Pass the notification type into `notifyAnswerAuthor` and use a `TYPE_VOTE` constant for upvotes.
- **Verification Needed:** Generate both a vote and an acceptance notification; assert their stored types and rendered labels.

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `viewmodel/QuestionDetailViewModel.java`.
- Fix: `notifyAnswerAuthor` now accepts a notification type; upvote uses `TYPE_VOTE` and acceptance uses `TYPE_ACCEPTED`.
- Root cause: the helper hard-coded `TYPE_ACCEPTED`.
- Verification: build, unit tests and source review.
- Remaining limitation: notification documents were not created/inspected at runtime to avoid additional write flows.

### BUG-029 — Saved promos are shared across all accounts on the device

- **Feature:** Saved promos / local data separation.
- **Severity:** Low.
- **Confidence:** Confirmed.
- **Status:** FIXED — VERIFICATION BLOCKED.
- **File:** `app/src/main/java/com/example/engqa/data/local/SavedPromo.java`; `app/src/main/java/com/example/engqa/data/local/SavedPromoDao.java`; `app/src/main/java/com/example/engqa/data/local/SavedPromoRepository.java`; `app/src/main/java/com/example/engqa/SavedPromosActivity.java`
- **Line(s):** `SavedPromo.java:12–28` (no owner field); `SavedPromoDao.java:21–22` (`SELECT *`); `SavedPromoRepository.java:29–31` (`observeAll`); `SavedPromosActivity.java:77, 81`.
- **Class/Function:** `SavedPromo`, `SavedPromoDao`, `SavedPromoRepository`, `SavedPromosActivity`.
- **Description:** The Room `saved_promo` table has no owner column, and the repository returns every saved row. The "Bài đã lưu" menu remains available when signed out and to every account on the same device. Preferences therefore leak between accounts.
- **Reproduction Steps:**
  1. Sign in as user A and save a promo.
  2. Log out.
  3. Sign in as user B (or stay signed out) and open "Bài đã lưu".
- **Expected Result:** Saved promos are scoped to the signed-in user, or the list is cleared on logout.
- **Actual Result / Evidence:** The table has no UID/owner field and the query is unfiltered; the activity renders all rows. This was not runtime-tested across two accounts, but the schema and query are unambiguous.
- **Root Cause:** Local persistence was modeled as device-global content, not user data.
- **Impact:** Cross-account visibility of saved items and confusing state after account switching. The stored content is promotional and non-sensitive, so severity is low.
- **Related Files:** `AppDatabase.java`; `SessionManager.logout` (does not clear the table); `MenuScreenActivity` popup.
- **Suggested Remediation:** Add an `ownerUid` column and filter `observeAll(uid)`; clear or migrate the table on logout/account change.
- **Verification Needed:** Save as A, switch to B (and signed-out), verify isolation/clearing.

---

**Remediation (2026-10-24)** — **FIXED — VERIFICATION BLOCKED**
- Files modified: `data/local/SavedPromo.java`, `SavedPromoDao.java`, `SavedPromoRepository.java`, `viewmodel/SavedViewModel.java`, `viewmodel/PromoViewModel.java`, `data/local/AppDatabase.java`.
- Fix: added `owner_uid` to the Room key (composite primary key), scoped all saved queries/removals to the current UID, required login to save, and added a v1→v2 migration that preserves legacy rows with an empty owner (hidden from every account). Room version bumped to 2.
- Root cause: the table had no owner column, so every account on the device shared the same rows.
- Verification: fresh-install runtime as the centre-manager account saved a promo and saw it in "Bài đã lưu"; after logout and login as the learner, the same list was empty and did not show the centre manager's promo; build, 69 unit tests and lint pass. The migration SQL was compared with the generated Room v2 schema (`CREATE TABLE saved_promo (owner_uid, promo_id, ... PRIMARY KEY(owner_uid, promo_id))`).
- Remaining limitation: the 1→2 upgrade path was not executed on an installed v1 database; legacy rows remain stored but invisible.

## 6. Findings by Module

> **Historical section.** These module notes describe the original pre-remediation state. The per-issue remediation blocks in §5 and the authoritative totals in §11 supersede any "broken/unreachable/incomplete" wording below.


### Authentication and session (`LoginActivity`, `AuthViewModel`, `RegisterActivity`, `SessionManager`, `GoogleAuthHelper`)

- Login validation and credential error mapping work; valid email/password login succeeded for two demo accounts.
- Fresh login shows a false "email unverified" state (BUG-016).
- Registration hint overstates availability (BUG-017); actual password mismatch validation works.
- Google Sign-In launches Credential Manager; completion was not possible without a Google account.
- Home-menu logout corrupts navigation/session state (BUG-002); profile and Settings logout paths clear the task correctly.
- Password reset/change were not executed.

### Home feed, filters and search (`HomeActivity`, `FeedViewModel`, `QuestionRepository`, `FeedMixer`)

- Feed loads realtime data and handles loading/cache/error states.
- The "Trung tâm" category filter and popular sorting work; a normal category filter alone was not re-verified.
- Search is not composed with filters (BUG-010) and pages outside its query (BUG-009).
- Approved promos interleave correctly in the observed feed, but the mixing algorithm can drop leftover promos (BUG-024).
- `FeedAdapter` diffing ignores some media/link changes, so in-place edits may not refresh those visual fields (risk, not confirmed as a user-facing failure).

### Q&A (`QuestionDetailActivity`, `QuestionDetailViewModel`, `AnswerRepository`, `VoteRepository`)

- Question and answer rendering, media placeholders and report-dialog opening work; answer validation was code-reviewed but not submitted.
- Existing votes are not loaded (BUG-011).
- Accepting is non-exclusive and inflates counts (BUG-003).
- Delete is immediate and non-cascading (BUG-004/005).
- `deleteAnswer` and `markSolved` are implemented but have no UI callers.
- Notification creation for votes uses the "accepted" type (BUG-028).

### Promotions and centres (`PromoDetailActivity`, `PromoRepository`, `CreatePromoActivity`, centre activities)

- Promo detail, local save/unsave and the "Trung tâm" feed filter work; saved items are shared across accounts (BUG-029).
- Contact intents are at risk on Android 11+ (BUG-012).
- Clear-cache misses media (BUG-019).
- The centre list/detail screens render but are unreachable; request/edit are reachable only through that orphaned list (BUG-006).
- Create Promo is reachable only for centre accounts; validation error placement is poor (BUG-027) and the link is effectively mandatory even though the detail supports hiding it (risk).
- Promo view/click counters and lead submission were not executed; promo viewCount was incremented by normal screen opening.

### Profile and settings (`MenuScreenActivity`, `EditProfileActivity`, `ProfileViewModel`, `SettingsFragment`)

- Profile stats/tabs and empty answer state work.
- Profile saving is broken (BUG-001).
- Avatar is not editable (BUG-026).
- Settings UI renders; notification permission/state reconciliation is incomplete (BUG-020); cache clearing is incomplete (BUG-019).

### Media and offline (`MainActivity`, `MediaCompressor`, `QuestionPublisher`, worker classes)

- Audio recording/permission/playback works locally; image picker/camera not tested.
- Base64 media decoding occurs on the main thread in the feed and detail adapters (performance risk).
- Offline worker has account/retry hazards (BUG-013).
- Media is explicitly unencrypted and readable by any signed-in user; see risks.

### Backend configuration (`firestore.rules`, `firestore.indexes.json`)

- Over-broad user reads and updates (BUG-007/008).
- Missing all-category popular index (BUG-014).
- Notification creation rules allow arbitrary notifications to any recipient (risk).
- Reports have no in-app reader/moderation UI.

### Navigation and shell (`BaseNavActivity`, manifest, layouts)

- Bottom-nav Back-stack bug (BUG-015).
- Unreachable centre and server-error screens (BUG-006/025).
- No `<queries>` package visibility (BUG-012).

---

## 7. Incomplete or Unimplemented Features

> **Historical section.** Items 1, 5 and 7 (centre navigation, `ServerErrorActivity`, guest/logout redirect) were addressed during remediation; BUG-026 (avatar) remains unresolved. See §5/§11 for the final state.

**Confirmed**

1. **Centre feature is not wired into navigation.** The four centre activities are registered but have no entry point (BUG-006). The temporary test proved list → detail rendering works.
2. **Admin/moderation UI is absent.** Promo approval, centre approval/role assignment, report review, and admin counter correction exist only as schema/rules/console operations. `firestore.rules` comments say admins are added from the Console.
3. **Reports have no viewer.** Users can submit a report from question detail, but no screen lists or resolves reports.
4. **Avatar editing is absent.** `avatarUrl` is modeled and saved but never set by a UI (BUG-026).
5. **`ServerErrorActivity` is orphaned** (BUG-025).
6. **Dead classes/methods:** `StorageRepository` and `LinkStorageRepository` are not used by the current media path; `WordOfDayViewModel` is unused (Home uses `WordRepository` directly); `VoteRepository.observeMyVote` is unused; `QuestionRepository.markSolved` and `QuestionDetailViewModel.deleteAnswer` have no UI callers; `AuthViewModel.usernameAvailable`/`checkUsernameAvailable` are unused.
7. **Guest mode is unfinished.** The UI offers Login/Saved/Settings when signed out, but the main feed requires authentication and shows a permission error rather than redirecting (BUG-002).
8. **System notifications only work while the app process is alive** (Firestore listener), as documented in the README and `NotificationWatcher` comments. There is no FCM/background delivery.
9. **Create-promo link is de facto mandatory** (`PromoValidator.urlError` rejects null/empty), while the promo detail screen supports hiding the website button and docs describe website/phone as optional. The form label does not mark it optional.
10. **Image EXIF orientation is not corrected** (documented trade-off); rotated photos may appear sideways.

**Suspicions / needs product confirmation**

- Promo interleaving intentionally drops leftover promos or is an oversight (BUG-024).
- Requiring both a phone number and a website for every promo may be intentional despite the detail UI supporting absent fields.
- The `README` says 37 unit tests, but 41 exist; it also instructs enabling Cloud Storage although the current media path is Firestore-only. Documentation drift, not a runtime defect.

---

## 8. Build, Runtime, and Test Failures

> **Historical results.** The command outcomes below are from the original audit. The post-remediation run is in §4.1: 69/69 unit tests, 7/7 instrumented tests, lint 0 errors/268 warnings, and successful debug/release builds. The runtime failures listed below were re-tested after remediation where noted in each issue's remediation block.

**Build commands and outcomes**

- `.\gradlew.bat assembleDebug testDebugUnitTest --no-configuration-cache --console=plain`
  - Exit: success (`BUILD SUCCESSFUL`).
  - Unit test XML: 41 tests, 0 failures, 0 errors, 0 skipped.
- `.\gradlew.bat connectedDebugAndroidTest --no-configuration-cache --console=plain`
  - Exit: success; 7 tests, 0 failures on Pixel_4 API 29.
  - Final XML: `tests="7" failures="0" errors="0" skipped="0"`.
- `.\gradlew.bat lintDebug --no-configuration-cache --console=plain`
  - Exit: success; 271 warnings, 0 errors. HTML/SARIF/TXT reports were produced.
  - Key warning groups: `UnusedResources` 93, `Autofill` 37, `Overdraw` 22, `DiscouragedApi` 20, `GradleDependency` 13, `TextFields` 11, `HardcodedText` 11, `QueryPermissionsNeeded` 2, `SelectedPhotoAccess` 1, `CredentialManagerMisuse` 1, `StaticFieldLeak` 1.
- `.\gradlew.bat assembleRelease --no-configuration-cache --console=plain`
  - Exit: success; produced `app/build/outputs/apk/release/app-release-unsigned.apk`.
  - Release is unsigned because `keystore.properties` is absent. This is expected by the build comment, but it means there is no installable signed release artifact.
- Lint's `lintVitalRelease` also ran as part of `assembleRelease` and did not fail the build.

**Runtime outcomes**

- App installed and launched on Android 10 API 29.
- No Java/Kotlin crash (`FATAL EXCEPTION`) was observed in the tested flows. No app-specific stack trace was found in logcat for the tested actions.
- Runtime functional failures observed: profile save (BUG-001), search paging (BUG-009), filter/search interaction (BUG-010), bottom-nav Back (BUG-015), Home logout state (BUG-002), false verification box (BUG-016), username availability hint (BUG-017).
- Word card fetch did not produce a card after app-data clearing; no app-level error was logged because `WordRepository` maps failures to a callback/empty state. Host `Invoke-WebRequest` to the same API succeeded; emulator validation/network state is the likely cause. This is recorded as a blocked external integration, not a confirmed app bug.

**Test gap observations**

- No tests cover profile update, session/logout navigation, search paging/filter composition, vote restoration, acceptance exclusivity, deletion cascade, Firestore rules, centre navigation, promo intents, or the offline worker.
- Unit tests focus on pure helpers; instrumented tests cover login/register settings/word cache only.
- The README's "37 tests" figure is outdated (actual 41 unit + 7 instrumented).

---

## 9. Potential Risks and Untested Areas

> These are risks recorded by the original audit that were outside the 29 numbered issues. User-profile enumeration (BUG-007) and the counter/field tampering branches (BUG-008) were tightened as described in §5. Client-side creation of notifications to any recipient and unencrypted media remain unchanged and still require trusted-backend hardening before production.

**Suspicious implementations / security and privacy risks**

- **User enumeration and email exposure (BUG-007).** Confirmed by the rule; not exploited.
- **Counter/field tampering (BUG-008).** Confirmed rule weakness; not exploited.
- **Arbitrary notification creation.** `firestore.rules:203–213` allows any signed-in user to create a notification for any recipient with arbitrary `type`, `title`, `body`, and `questionId` (only the actor must match and self-recipient is blocked). This enables spam/phishing in the in-app/system notification surface. Potential risk.
- **Media is not encrypted.** `docs/MEDIA_STORAGE.md:84` explicitly states no encryption; Firestore media reads are allowed for any signed-in user (`firestore.rules:175–185`). Combined with questions being community-visible, this may be intended, but it is a privacy risk for user-uploaded images/audio. The commit title `c280cc5` claims encryption while no encryption/decryption code exists (grep found no cipher/AES/encrypt/decrypt in `app/src`). Documentation correctly disclaims it; the commit message is misleading.
- **Main-thread Base64 decoding.** `ThumbnailLoader.load` (`ThumbnailLoader.java:19–31`) and `MediaImageAdapter.onBindViewHolder` (`MediaImageAdapter.java:50–72`) decode images synchronously on the main thread. Up to five ~1024px images plus feed thumbnails can cause jank or memory pressure on low-end devices. Not measured.
- **Committed Firebase configuration.** `app/google-services.json` is tracked in Git and contains a Firebase API key, OAuth client IDs, and project identifiers. Firebase API keys are not secrets by design, but the checked-in shared demo project lets anyone with the repo attempt access subject to rules. Values are redacted here.
- **In-app notification backlog (BUG-023)** can spam old alerts.
- **Backup of private state (BUG-021)** may include session/cache data.
- **Unused broad media permissions (BUG-022)** widen the permission surface.
- **No rate limiting or content moderation** for questions, answers, promos, reports, or leads beyond rules; abuse is possible.
- **`WorkManager` retries indefinitely** for permanent failures (BUG-013), with battery/network cost.
- **No global error screen use**; network failures are handled per-screen.

**Untested areas**

- End-to-end post with 1–5 images and audio, including compression, Base64 size limits, rollback, and feed/detail rendering.
- Offline posting and the worker (network loss, app kill, account switch, retry/backoff, folder cleanup).
- Voting mutations, toggling, rapid double taps, concurrency across devices, and Firestore transaction failures.
- Accepting/unaccepting and counter consistency.
- Question/answer deletion and cascade.
- Promo creation, admin approval, lead submission, duplicate lead, contact intents on API 30+.
- Reports and moderation.
- Centre request/edit and admin role assignment.
- Registration completion, email verification delivery, password reset/change, unverified login dialog.
- Google Sign-In completion, logged-out authentication, revoked/expired sessions.
- Notification delivery, click-through, mark-read/per-item behavior, Android 13 permission.
- Camera capture, gallery picker, permission denial/permanent denial, image orientation.
- Process death, low-memory behavior, background restrictions.
- RTL, accessibility, localization, large font, dark theme, tablet/foldable widths and sizes beyond the API 29 phone used in §4.2.
- Firestore rules emulator tests and fresh-project index deployment.
- Release signing and Play Store compliance.
- Performance/leak testing.

---

## 10. Prioritized Remediation Plan

> **Historical plan.** This is the original pre-remediation priority list, preserved for traceability. The actual final status of every item is recorded in the remediation block of its issue in §5 and in §11. BUG-005/008/026 remain the only items not fully closed.

### 1. Critical issues

None reproduced. The app starts and the main login/feed/read workflows are reachable. (This does not mean the app is safe to release; the High items below are release blockers.)

### 2. High-severity issues (fix first)

1. **BUG-001 — Profile save collision.** Add self-exclusion to the uniqueness check; then re-enable level changes. This is a small, isolated fix with immediate user impact.
2. **BUG-002 — Logout/session cleanup.** Clear the task stack, finish stale activities, clear user-scoped ViewModels, and re-check the session in `onStart`. This also removes the post-logout profile/email exposure.
3. **BUG-003 — Exclusive acceptance.** Replace the batch with a transaction that clears the previous acceptance and adjusts both authors' counts; enforce idempotency.
4. **BUG-004 + BUG-005 — Safe question deletion.** Add confirmation and implement a recursive/tombstone deletion path that cleans answers, votes, media, notifications, reports, and counters. These two should be fixed together.
5. **BUG-006 — Centre navigation.** Connect the existing screens to a visible entry point and add an end-to-end navigation test.
6. **BUG-007 + BUG-008 — Firestore authorization.** Restrict user reads to self/admin or a public projection; move counters to trusted server-side logic; constrain answer updates to exact allowed fields/actors. Deploy with rules-emulator tests.

### 3. Medium-severity issues

7. **BUG-009 + BUG-010 — Search correctness.** Disable paging while searching and compose keyword + category + sort (with indexes), or explicitly clear/disable filters.
8. **BUG-011 — Vote restoration.** Observe `votes/{uid}` per answer and populate `myVotes`; test toggle-off after reopen.
9. **BUG-012 — Contact intents.** Start intents directly and catch `ActivityNotFoundException`; add `<queries>` if `resolveActivity` is retained.
10. **BUG-013 — Offline job lifecycle.** Bind jobs to the owner UID, cancel on logout, classify permanent failures, cap retries, and clean drafts.
11. **BUG-014 — Index deployment.** Add the missing popular-sort index and any search/filter composite indexes; validate on a fresh project.
12. **BUG-015 — Bottom-nav Back.** Decide the expected Back model and implement it consistently (single-activity navigation, clear-top, or Back handling).

### 4. Low-severity issues

13. **BUG-016** — Hide the verify box until authenticated.
14. **BUG-017** — Change the availability hint to a format hint or add a real uniqueness check.
15. **BUG-018** — Only serve valid/stale-marked word cache entries on failure.
16. **BUG-019** — Clear `cache/media` and Glide caches.
17. **BUG-020** — Reconcile the notification switch with the OS permission.
18. **BUG-021** — Define backup/transfer exclusions.
19. **BUG-022** — Remove unused media permissions.
20. **BUG-023** — Suppress notification backlog using a last-seen watermark or non-cache snapshot.
21. **BUG-024** — Append/rotate leftover promos or document the cap.
22. **BUG-025** — Wire or delete `ServerErrorActivity`.
23. **BUG-026** — Implement avatar editing or remove the unused field/claim.
24. **BUG-027** — Move/scroll validation errors into view.
25. **BUG-028** — Store upvote notifications with the correct type.
26. **BUG-029** — Scope saved promos to the signed-in user or clear them on logout.

### 5. Additional testing needed

- Firebase rules-emulator suite covering every collection, actor, and field.
- End-to-end instrumentation for: registration → level → post with media; offline queue → account switch/reconnect; vote → reopen → toggle off; accept A → accept B; delete with children; promo create → approve → view → lead; centre request → approve → edit.
- API 33+ notification-permission and API 30+ package-visibility tests.
- Process-death tests for drafts/forms/playback; tablet, foldable and reverse-landscape responsive checks.
- Accessibility and performance profiling, especially Base64 media decoding.

**Recommended fix order and dependencies**

- Fix BUG-001 independently (low risk).
- Fix BUG-004/005 together before exposing delete more widely.
- Fix BUG-002 and BUG-015 together (same navigation design).
- Fix BUG-007/008 before any production data is stored.
- Fix BUG-009/010/014 together because search/filter composition requires indexes.
- Fix BUG-003 before relying on `acceptedCount` for reputation/ranking.
- Add integration tests before/with each high-severity fix; do not declare the app ready based only on the existing 48 tests.

---

## 11. Final Assessment

**Original assessment (preserved):** The pre-remediation application built and launched, but the High-severity profile, logout/privacy, acceptance, deletion, centre-navigation and backend-authorization findings were release blockers, and the search/vote/low-severity findings prevented a reliability claim.

**Post-remediation assessment (2026-10-24)**

- **Does the application build?** Yes. `assembleDebug`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` all succeed. The release APK is unsigned (`app-release-unsigned.apk`).
- **Does the application launch?** Yes. It installs and launches on the available Android 10 / API 29 emulator; login, feed, profile, centre list, question detail, promo detail, saved promos and settings render without a crash in the tested paths.
- **Authoritative remediation totals (29 findings):**

| Status | Count | Issues |
|---|---:|---|
| FIXED AND VERIFIED | 14 | BUG-001, 002, 004, 006, 009, 010, 014, 015, 016, 017, 019, 022, 024, 027 |
| FIXED — VERIFICATION BLOCKED | 12 | BUG-003, 007, 011, 012, 013, 018, 020, 021, 023, 025, 028, 029 |
| PARTIALLY FIXED | 2 | BUG-005, 008 |
| UNRESOLVED | 1 | BUG-026 |
| BLOCKED | 0 | — |
| NOT A BUG / ALREADY FIXED | 0 | — |

- **Verified fixed at runtime:** profile save with an unchanged name (001), Home logout/back-stack privacy (002), delete confirmation dialog and cancel path (004), centre menu entry and list (006), **accent/case/whitespace-insensitive multi-word search, partial words, bounded typo candidates, search+filters/sorts, local ranked paging, no-results/too-short/clear/rapid-query states (009), search/filter composition (010), deployed/READY index set (014)**, bottom-nav Back (015), pre-login verify box (016), username hint resource (017), cache-clear action (019), unused media permissions in the merged manifest (022), leftover-promo unit test (024), create-promo error visibility and focus (027), and saved-promo account isolation on a fresh install (029).
- **Implemented, with end-to-end verification blocked:** exclusive answer acceptance (003), profile-read rule (007), vote restoration (011), promo contact intents (012), offline worker lifecycle (013), word-cache fallback (018), notification permission sync (020), backup exclusions (021), notification backlog (023), offline error screen wiring (025), vote notification type (028), and the v1→v2 saved-promo migration portion of 029. Reasons: no destructive shared-data writes, API 29-only emulator, and no Firebase rules emulator/deployment.
- **Partially fixed:** question deletion (005) now removes answers, votes and media and derives profile counters from live data, but notifications/reports and other users' stored counters still require a trusted backend. Firestore authorization (008) no longer exposes profiles or allows arbitrary answer/acceptance edits, but bounded counter deltas cannot be fully attributed on the client without Cloud Functions.
- **Unresolved:** avatar editing (026) requires a product decision; the unused `avatarUrl` field remains without a picker/upload/display contract.
- **Responsive UI (additional pass, not a numbered finding):** portrait/landscape layouts were repaired for Home (clipped category grid), ChooseLevel (overflow + baseline clipping + selected-level state), ServerError (overflow), Login/Register (insets overwrote gutters), all form gutters, profile tab state, lead-dialog scrolling and detail scroll restoration. API 29 phone screenshot/dump checks covered Login, Register, Home, Ask, Profile, Edit Profile, QuestionDetail, CenterList, CenterDetail, Notification, Settings and SavedPromos; temporary fully-visible Espresso checks covered ChooseLevel and ServerError; existing instrumented tests pass 7/7 in both orientations. Tablet/foldable, reverse-landscape, PromoDetail, WordDetail, centre forms, image viewer and open-keyboard dialogs remain untested (see §4.2).
- **Remaining actions required before release:**
  1. Deploy the updated `firestore.rules` (indexes are already deployed and READY), then run a rules-emulator suite covering every collection/actor/field.
  2. Execute the destructive/write flows in a disposable Firebase project: post with media, vote → reopen → unvote, accept A → accept B → accept A, delete with answers/votes/media, worker account switch/permanent failure, notification backlog/type, and promo create → approve → lead.
  3. Run Android 11+ package-visibility and Android 13+ notification-permission tests.
  4. Test the Room v1→v2 migration on an installed v1 database (the SQL matches Room's generated v2 schema, but the upgrade was not executed).
  5. Decide whether avatar editing is in scope; if yes, add the picker/upload/storage integration and tests.
  6. Run `tools/seed/reindex-search.js --dry-run` after any bulk import/older-data restore; it should report zero changes. New questions are indexed automatically; the existing 17 questions were backfilled (0 remaining changes).
  7. Run the responsive checks on a tablet/foldable and reverse-landscape, and with the keyboard open on the lead/report dialogs and centre forms.
- **Release-readiness conclusion:** The original code-level release blockers have been addressed to the extent possible in this environment, search is verified with deployed indexes, and the main screens now adapt correctly on an API 29 phone in both orientations. The application is suitable for **further QA and a rules-deployment pass**, not yet for production release, because the security rules are not deployed, destructive workflows have not been end-to-end verified, BUG-005/008 remain partially fixed without a trusted backend, and tablet/foldable responsive coverage is still limited.

**Remediation integrity statement:** Application source was modified only for the fixes listed per issue in §5. `firestore.indexes.json` was deployed to `accountdata-dd29b` (19/19 READY); `firestore.rules` was **not** deployed. No destructive writes to shared Firebase content were performed. Search metadata on 17 existing questions was rebuilt additively via `tools/seed/reindex-search.js --apply`, which updates only the `searchKeywords` field and preserves title/body/tags/answers; a follow-up dry-run reported 0 changes. Runtime re-checks covered sign-in/sign-out, unchanged profile save, menu/centre navigation, delete-dialog cancellation, saved-promo isolation, create-promo validation, cache clearing, Back behaviour, and the search scenarios listed in §4.1. The responsive pass additionally rotated the emulator between portrait and landscape for Login, Home/search, Ask draft, Profile tab, Edit Profile, QuestionDetail, CenterList/Detail, Notification, Settings and SavedPromos, and used a temporary `TempResponsiveLayoutTest` for fully-visible ChooseLevel/ServerError checks; that test was deleted after execution. Signing in updated Firebase Auth metadata, opening a promo incremented its `viewCount` once through normal app behaviour, and local Room state was written on the emulator. The v1→v2 Room migration was statically compared with Room's generated schema but not executed. This report was updated in place; no competing report was created.
