# EngQA – Cấu trúc dự án đầy đủ (đề xuất)

Căn cứ:
- **Hiện trạng repo** `Ddawng18/MOB-EngQA` (nhánh `dev` + `Thinh-dev`): Java 11, XML, Material 3, package `com.example.engqa`, minSdk 29, targetSdk 37.
- **Skill** `android-ui-and-app-structure` (RecyclerView + Adapter/ViewHolder, adaptive layouts `layout-land`/`sw600dp`, `onSaveInstanceState`), `android-data-storage-architecture` (Repository + `AndroidViewModel` + LiveData, không đưa Context/View vào ViewModel, Firestore `addSnapshotListener`), `android-testing-background-work` (`ExecutorService`, NotificationChannel, kiểm thử JUnit/Espresso).
- **Yêu cầu** của bạn và giảng viên: Firestore realtime, ảnh + âm thanh, upvote/downvote, trung tâm, Notification, xoay ngang/dọc, `ENGQA.pdf`, mẫu đồ án.
- **Kiến thức nền** (giáo trình Android) chỉ dùng làm chuẩn kỹ thuật; chưa có nội dung học tiếng Anh.

Chú thích trạng thái: **[có]** đã có trong repo · **[sửa]** có rồi, cần chỉnh · **[mới]** tạo mới. Mã `Txx` trỏ tới kế hoạch trong `EngQA_Giai_doan_1_Ke_hoach.md`.

**Nguyên tắc luồng dữ liệu (bắt buộc giữ nhất quán):**
```
Activity/Adapter  ──quan sát──►  ViewModel (AndroidViewModel, LiveData)  ──gọi──►  Repository (Firestore/Storage/Auth)
      ▲                                                                                     │
      └────────────── LiveData cập nhật ◄──── SnapshotListener (đăng ký onStart / gỡ onStop) ┘
```
- Activity không gọi Firestore trực tiếp; chỉ Repository mới `import com.google.firebase.*`.
- Listener do Repository trả về `ListenerRegistration`; ViewModel giữ và gỡ ở `onCleared()`, Activity chỉ quan sát LiveData ⇒ xoay màn hình không nhân đôi listener.
- Việc chậm (xử lý file, nén ảnh) chạy trên `ExecutorService`, cập nhật UI qua `postValue`.

---

## 1. Cây thư mục

```
MOB-EngQA/
├── build.gradle.kts                      [sửa] thêm plugin google-services (đã có ở Thinh-dev)
├── settings.gradle.kts                   [có]
├── gradle.properties                     [có]
├── gradle/libs.versions.toml             [sửa] thêm firebase, recyclerview, lifecycle, glide, media3
├── firebase.json                         [mới] (tuỳ chọn) cấu hình Firebase CLI để deploy rules
├── firestore.rules                       [sửa] T02, T10, T12 – rules đầy đủ
├── firestore.indexes.json                [mới] T05 – chỉ mục tổng hợp (category + createdAt…)
├── storage.rules                         [mới] T07 – giới hạn loại/kích thước/uid
├── README.md                             [sửa] thay ERD SQL bằng mô hình Firestore
├── docs/                                 [mới] T16
│   ├── usecases/                         UC01–UC0x + đặc tả
│   ├── diagrams/                         workflow, phân rã chức năng, sequence, activity
│   ├── figma/                            ảnh xuất từ Figma
│   └── test-results/                     kết quả kiểm thử thật + ảnh
├── tools/seed/                           [mới] T02 – JSON dữ liệu mẫu (đánh dấu "mẫu") + hướng dẫn import
└── app/
    ├── build.gradle.kts                  [sửa]
    ├── google-services.json              [có ở Thinh-dev] thống nhất 1 project (xem mục 6)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml       [sửa]
        │   ├── java/com/example/engqa/   (mục 2)
        │   └── res/                      (mục 3)
        ├── test/java/com/example/engqa/        unit test (mục 5)
        └── androidTest/java/com/example/engqa/ Espresso test (mục 5)
```

## 2. Mã nguồn Java – `app/src/main/java/com/example/engqa/`

Giữ các Activity hiện có ở package gốc trong giai đoạn đầu (tránh xung đột merge giữa 4 người); tách `ui/` ở cuối dự án nếu còn thời gian.

### 2.1 Gốc package (ứng dụng + màn hình)
| File | Trạng thái | Vai trò | Task |
|---|---|---|---|
| `EngQAApp.java` | [mới] | `Application`: tạo NotificationChannel, bật Firestore persistence, khởi tạo `NotificationWatcher` | T12b |
| `LoginActivity.java` | [sửa] | Đăng nhập (Thinh-dev đã dùng `UserRepository`), quên mật khẩu, Google | T03 |
| `RegisterActivity.java` | [sửa] | Đăng ký → `ChooseLevelActivity` | T03 |
| `ChooseLevelActivity.java` | [sửa] | Chọn Beginner/Intermediate/Advanced (đổi "Advance"→"Advanced") | T03 |
| `HomeActivity.java` | [sửa] | Feed câu hỏi: RecyclerView, search, lọc, sort, 4 trạng thái UI, nút chuông | T04, T05 |
| `MainActivity.java` | [sửa] | Màn "Đặt câu hỏi" (nên đổi tên `CreateQuestionActivity`, làm riêng 1 commit) | T06–T08 |
| `QuestionDetailActivity.java` | [sửa] | Chi tiết, danh sách trả lời, vote, accept, ảnh, âm thanh | T09–T11 |
| `ImageViewerActivity.java` | [mới] | Xem ảnh phóng to, pinch-to-zoom (theo UC07) | T07 |
| `MenuScreenActivity.java` | [có ở Thinh-dev] [sửa] | Hồ sơ (nên đổi `ProfileActivity`): header, thống kê, 2 tab "Câu hỏi/Câu trả lời của tôi", đăng xuất | T03, T11 |
| `EditProfileActivity.java` | [mới] | Sửa tên, ảnh đại diện, trình độ | T03 |
| `NotificationActivity.java` | [mới] | Danh sách thông báo, đánh dấu đã đọc | T13 |
| `CenterListActivity.java` | [mới] | Danh sách trung tâm | T12 |
| `CenterDetailActivity.java` | [mới] | Trang trung tâm: thông tin, khóa học, bài viết (realtime) | T12 |
| `CenterEditActivity.java` | [mới] | Manager sửa thông tin / đăng bài / thêm khóa học | T12 |
| `CenterRequestActivity.java` | [mới] | Gửi yêu cầu tạo trang trung tâm (chờ admin duyệt) | T12 |
| `ReportDialogFragment.java` | [mới] | Dialog báo cáo nội dung (lý do) | T13 |
| `ServerErrorActivity.java` | [có] | Màn lỗi kết nối (theo ENGQA.pdf: Error State). Nên thay bằng trạng thái lỗi trong từng màn + nút "Thử lại" | T04 |
| `BaseNavActivity.java` | [sửa] | Bottom nav dùng chung; đổi tab Saved → Hồ sơ theo ENGQA.pdf; gom xử lý insets | T01 |
| `NetworkUtils.java` | [có] | Kiểm tra mạng | — |
| `SessionManager.java` | [có ở Thinh-dev] | Trạng thái đăng nhập (`FirebaseAuth`) | T01 |
| `PasswordHasher.java`, `UserDbHelper.java` | [có ở Thinh-dev] **xoá** | Tàn dư SQLite không còn dùng (Firebase Auth giữ mật khẩu) | T01 |

### 2.2 `model/` – POJO cho Firestore (constructor rỗng + getter/setter, `@DocumentId`, `@ServerTimestamp`)
`User`, `Category`, `Question` (có `type`, `level`, `imageUrls`, `audioUrl`, `passageText`, `searchKeywords`, `isSolved`, `acceptedAnswerId`), `Answer`, `Vote` (`value` ±1), `Center`, `Course`, `CenterPost`, `Report`, `AppNotification`, `UiState<T>` (LOADING/EMPTY/ERROR/SUCCESS dùng chung cho mọi màn). **[mới] – T02**

### 2.3 `data/` – lớp truy cập Firebase (nơi duy nhất dùng SDK Firebase)
| File | Trách nhiệm | Task |
|---|---|---|
| `UserRepository.java` | [có ở Thinh-dev][sửa] Auth + `users/{uid}`; thêm `observeUser()`, `updateProfile()`, `sendPasswordReset()` | T03 |
| `QuestionRepository.java` | `observeLatest(category, sort, limit)` → `ListenerRegistration`, `loadMore(startAfter)`, `observeQuestion(id)`, `create()`, `search(keyword)`, `delete()`, `markSolved()` | T04–T06, T11 |
| `AnswerRepository.java` | `observeAnswers(qid)`, `addAnswer()`, `accept()`, `delete()` | T09, T11 |
| `VoteRepository.java` | `vote(qid, aid, +1/-1/0)` bằng **Firestore transaction**; `observeMyVote()` | T10 |
| `StorageRepository.java` | Upload ảnh/âm thanh lên Firebase Storage, tiến độ, kiểm tra loại/dung lượng, trả URL | T07, T08 |
| `CategoryRepository.java` | Đọc 9 chủ đề (Grammar, Vocabulary, Listening, Reading, Writing, Speaking, Pronunciation, IELTS, TOEIC) | T02 |
| `CenterRepository.java` | `observeCenters()`, `observeCenter(id)`, courses/posts, gửi yêu cầu, sửa (manager) | T12 |
| `NotificationRepository.java` | `create()` (khi trả lời/vote/accept), `observeMine()`, `markRead()` | T12b |
| `ReportRepository.java` | `report(targetPath, reason)` | T13 |
| `Result.java` / `Callback<T>` | Kiểu kết quả/callback thống nhất (đã có `UserRepository.Callback`, tách ra dùng chung) | T02 |

### 2.4 `viewmodel/` – `AndroidViewModel` + `LiveData`, không giữ Context/View
`AuthViewModel`, `HomeViewModel` (giữ bộ lọc, từ khóa, trang đã tải, listener), `CreateQuestionViewModel` (giữ nháp khi xoay: tiêu đề, mô tả, URI ảnh/âm thanh, tiến độ upload), `QuestionDetailViewModel`, `ProfileViewModel`, `CenterViewModel`, `NotificationViewModel`. **[mới] – T04…T13**

### 2.5 `ui/` – adapter và thành phần giao diện (RecyclerView + ViewHolder, `ListAdapter` + `DiffUtil` để realtime không vẽ lại cả danh sách)
`QuestionAdapter`, `AnswerAdapter` (nút ▲/▼, điểm, tích xanh Accepted), `CategoryChipAdapter` (dải tag), `CenterAdapter`, `CourseAdapter`, `CenterPostAdapter`, `NotificationAdapter`, `AttachmentAdapter` (ảnh đính kèm khi soạn câu hỏi), `AudioPlayerView` (custom view: play/pause/seek, giữ vị trí khi xoay), `StateView` (hiển thị loading/empty/error + "Thử lại"). **[mới]**

### 2.6 `media/` và `notification/` và `util/`
| File | Vai trò | Task |
|---|---|---|
| `media/AudioPlayerManager.java` | `MediaPlayer`/Media3 gắn với lifecycle, giữ vị trí khi xoay | T08 |
| `media/AudioRecorder.java` | `MediaRecorder` ghi âm (cần quyền RECORD_AUDIO) | T08 |
| `media/ImageUtils.java` | Chọn ảnh/camera, nén ảnh, kiểm tra ≤5MB, JPG/PNG (chạy trên `ExecutorService`) | T07 |
| `notification/NotificationHelper.java` | `NotificationChannel`, `NotificationCompat`, `PendingIntent` mở `QuestionDetailActivity` | T12b |
| `notification/NotificationWatcher.java` | Listener `notifications` của người dùng; bỏ qua snapshot đầu và dữ liệu `fromCache` khi quyết định `notify()` | T12b |
| `util/TimeUtils.java` | "2 giờ trước" từ `Timestamp` | T04 |
| `util/TextUtils2.java` (đặt tên khác `TextUtils` hệ thống) | Bỏ dấu, tách từ → `searchKeywords` | T05 |
| `util/Validators.java` | Kiểm tra email, mật khẩu, tiêu đề, dung lượng file | T03, T06 |
| `util/PermissionHelper.java` | Xin CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS, đọc ảnh/âm thanh (Android 13+) | T07, T08, T12b |

## 3. Tài nguyên – `app/src/main/res/`

### 3.1 `layout/` (dọc, mặc định)
| File | Trạng thái | Dùng cho |
|---|---|---|
| `activity_login.xml`, `activity_register.xml`, `activity_choose_level.xml` | [có] | Auth |
| `activity_home.xml` | [sửa] thay `LinearLayout` danh sách bằng `RecyclerView` + `StateView` | Home |
| `activity_main.xml` (Đặt câu hỏi) | [sửa] thêm chọn loại bài, trình độ, đoạn văn, ảnh, âm thanh | T06–T08 |
| `activity_question_detail.xml` | [sửa] RecyclerView trả lời, phát âm thanh, khung nhập đáp án | T09 |
| `activity_menu_screen.xml` (hồ sơ) | [có ở Thinh-dev][sửa] | T03 |
| `activity_image_viewer.xml`, `activity_edit_profile.xml`, `activity_notification.xml` | [mới] | |
| `activity_center_list.xml`, `activity_center_detail.xml`, `activity_center_edit.xml`, `activity_center_request.xml` | [mới] | T12 |
| `activity_server_error.xml` | [có] | |
| `item_question.xml` | [sửa] thêm chip chủ đề, nhãn "Đã giải đáp", icon ảnh/âm thanh | |
| `item_answer.xml` | [sửa] thêm ▼, điểm, tích Accepted | T10 |
| `item_category_chip.xml`, `item_center.xml`, `item_course.xml`, `item_center_post.xml`, `item_notification.xml`, `item_attachment.xml` | [mới] | |
| `view_bottom_nav.xml` | [có ở dev] | Home / Đặt câu hỏi / Hồ sơ |
| `view_state.xml`, `view_audio_player.xml` | [mới] | Trạng thái, trình phát |
| `dialog_report.xml` | [mới] | T13 |

### 3.2 Hướng ngang và máy tính bảng (yêu cầu bắt buộc, T14)
- `layout-land/`: bản ngang của `activity_home.xml` (feed + dải tag bên trái/phải), `activity_question_detail.xml` (câu hỏi bên trái, trả lời bên phải), `activity_main.xml` (form 2 cột), `activity_login.xml`, `activity_register.xml`, `activity_choose_level.xml`, `activity_menu_screen.xml`, `activity_center_detail.xml`.
- `layout-sw600dp/` (và `-land` tương ứng): feed dạng lưới 2 cột, chi tiết master–detail nếu còn thời gian.
- Không dùng `android:screenOrientation` trong manifest (không khóa hướng). Mọi form dùng `ScrollView`/`NestedScrollView`; nháp giữ trong ViewModel + `onSaveInstanceState`.

### 3.3 Các thư mục còn lại
- `values/`: `strings.xml` (toàn bộ chuỗi UI), `colors.xml`, `styles.xml`, `themes.xml`, `dimens.xml` [mới], `arrays.xml` [mới: trình độ, loại bài]; `values-land/dimens.xml`, `values-sw600dp/dimens.xml` [mới].
- `values-night/themes.xml` [có]; app hiện dark-only (theo README) nên giữ.
- `drawable/` [có nhiều `bg_*`, `ic_*`]; thêm `ic_upvote.xml`, `ic_downvote.xml`, `ic_accepted.xml`, `ic_play.xml`, `ic_pause.xml`, `ic_mic.xml`, `ic_bell.xml`, `ic_report.xml`, `ic_center.xml`, `ic_image.xml`, `ic_audio.xml`, `ic_notification_small.xml` (icon thông báo nền trong suốt) [mới].
- `menu/`: `menu_question_options.xml` (sửa/xóa/báo cáo) [mới].
- `xml/`: `backup_rules.xml`, `data_extraction_rules.xml` [có]; `file_paths.xml` [mới – `FileProvider` cho ảnh chụp camera].

## 4. `AndroidManifest.xml` cần khai báo
- Quyền: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA` (không bắt buộc phần cứng: `<uses-feature android:required="false">`), `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `READ_MEDIA_IMAGES`/`READ_MEDIA_AUDIO` (hoặc dùng Photo Picker/SAF để khỏi xin quyền đọc).
- `<application android:name=".EngQAApp">`, mọi Activity ở mục 2.1, `FileProvider`.
- `LoginActivity` là LAUNCHER (kiểm tra `SessionManager` rồi vào Home), Home không phải launcher như `dev` hiện tại.
- Chỉ `windowSoftInputMode="adjustResize"`; **không** có `screenOrientation`; có thể thêm `android:configChanges` không cần vì dùng ViewModel.

## 5. Kiểm thử – `src/test` và `src/androidTest`
| File | Nội dung | Task |
|---|---|---|
| `test/.../ValidatorsTest.java` | Email, mật khẩu, tiêu đề, dung lượng | T03, T06 |
| `test/.../TextUtilsTest.java` | Bỏ dấu, tạo `searchKeywords` | T05 |
| `test/.../VoteDeltaTest.java` | Logic delta none→+1→−1→0 (tách hàm thuần) | T10 |
| `androidTest/.../LoginFlowTest.java` | Espresso: đăng nhập, xoay màn hình, giữ dữ liệu | T14 |
| `androidTest/.../CreateQuestionRotationTest.java` | Nhập dở → xoay → không mất dữ liệu | T14 |
| `docs/test-results/` | Bảng kiểm thử thủ công 2 thiết bị (realtime, vote, notification, mất mạng) – ghi kết quả thật | T15 |

## 6. Cấu hình Gradle và Firebase
**`gradle/libs.versions.toml` / `app/build.gradle.kts`** (thêm so với `dev`; các phiên bản dưới đây là gợi ý, sẽ kiểm tra khi làm T01 vì chưa build thử):
- Đã có ở `Thinh-dev`: `firebase-bom 33.9.0`, `firebase-auth`, `firebase-firestore`, `firebase-storage`, plugin `google-services`.
- Cần thêm: `androidx.recyclerview`, `androidx.lifecycle:lifecycle-viewmodel` + `lifecycle-livedata`, Glide (ảnh), `androidx.media3` hoặc dùng `MediaPlayer` gốc, (nếu làm Google Sign-In) `play-services-auth`, `androidx.test.espresso`.
- `app/google-services.json`: cả nhóm phải cùng một project Firebase; thêm SHA-1 của từng máy dev nếu dùng Google Sign-In; hiện trong repo project là `accountdata-dd29b`.

**Firebase Console cần bật:** Authentication (Email/Password, Google nếu làm), Firestore (production mode + rules), Storage (xem câu hỏi gói Spark/Blaze), tạo `admins/{uid}` thủ công cho tài khoản admin.

## 7. Ánh xạ yêu cầu → file
| Yêu cầu | File chính |
|---|---|
| Tài khoản, hồ sơ, trình độ | `LoginActivity`, `RegisterActivity`, `ChooseLevelActivity`, `MenuScreenActivity`, `UserRepository`, `AuthViewModel` |
| Trang chủ + realtime | `HomeActivity`, `HomeViewModel`, `QuestionRepository.observeLatest`, `QuestionAdapter` |
| Tìm kiếm/lọc/sort/phân trang | `QuestionRepository.search/loadMore`, `TextUtils2`, `CategoryChipAdapter` |
| Câu hỏi đa phương tiện | `MainActivity`, `CreateQuestionViewModel`, `StorageRepository`, `ImageUtils`, `AudioRecorder`, `storage.rules` |
| Chi tiết + trả lời realtime + phát âm thanh | `QuestionDetailActivity`, `QuestionDetailViewModel`, `AnswerRepository`, `AudioPlayerView` |
| Upvote/downvote | `VoteRepository`, `AnswerAdapter`, `firestore.rules` (`getAfter`) |
| Chấp nhận câu trả lời | `AnswerRepository.accept`, `QuestionRepository.markSolved` |
| Trung tâm + phân quyền | `Center*Activity`, `CenterRepository`, `firestore.rules`, `admins/` |
| Notification (yêu cầu bổ sung) | `EngQAApp`, `NotificationHelper`, `NotificationWatcher`, `NotificationRepository`, `NotificationActivity` |
| Báo cáo nội dung | `ReportDialogFragment`, `ReportRepository` |
| Xoay ngang/dọc | `layout-land/*`, `layout-sw600dp/*`, ViewModel, `AudioPlayerManager` giữ vị trí |
| Bảo mật Firebase | `firestore.rules`, `storage.rules` |
| Báo cáo đồ án | `docs/*`, `README.md` |

## 8. Thứ tự tạo file (theo kế hoạch)
T01 dọn nhánh, xoá file SQLite, cập nhật Gradle/manifest → T02 `model/`, rules, seed → T03 auth/hồ sơ → T04–T05 Home → T06–T08 đăng câu hỏi + media → T09–T11 chi tiết, vote, accept → T12 trung tâm → **T12b notification** → T13 báo cáo/danh sách thông báo → T14 layout-land → T15 kiểm thử → T16 tài liệu.

## 9. Điểm lưu ý khi chia việc cho 4 người
Dựa trên phân công trong `ENGQA.pdf` (Thịnh: đăng nhập/hồ sơ; Bảo: trang chủ/tìm kiếm; Phú: đăng bài/chi tiết; Đăng: hệ thống/điều hướng): mỗi người sở hữu bộ file riêng (Activity + ViewModel + Repository + layout) để hạn chế xung đột merge; các file dùng chung (`strings.xml`, `AndroidManifest.xml`, `firestore.rules`, `libs.versions.toml`) chỉ sửa qua pull request nhỏ. Phần trung tâm và notification chưa có người phụ trách trong `ENGQA.pdf` → cần nhóm quyết định.

## 10. Còn chưa chắc / chưa xác minh
- Chưa build thử nên phiên bản thư viện mục 6 là gợi ý.
- Chưa thấy Figma nên danh sách màn hình và layout ngang có thể thay đổi khi có ảnh thiết kế.
- Tên tab của bottom nav (Hồ sơ hay Trung tâm) chờ Figma.
- Nếu không dùng được Firebase Storage (gói Spark), `StorageRepository`, `AudioRecorder` và phần ảnh/âm thanh cần phương án khác.
