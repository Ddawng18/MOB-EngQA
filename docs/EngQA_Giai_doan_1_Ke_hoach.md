# EngQA – Giai đoạn 1–5: Kiểm kê, hiện trạng, phạm vi, kiến trúc, kế hoạch

Ngày lập: 13/10/2026. Chưa viết code ứng dụng. Mọi nhận định về code đều lấy từ việc đọc repo đã clone về; **chưa biên dịch hay chạy app** (chưa kiểm chứng bằng thực thi).

---

## 1. Bảng kiểm kê nguồn (Giai đoạn 1)

| Nguồn | Nội dung đã xác minh | Phần sử dụng | Phần còn thiếu |
|---|---|---|---|
| Mẫu đồ án (`Mau Do An.pdf`, 11 trang) | Đã đọc đủ 11 trang. Bìa UEH, mục lục, 6 chương, Phụ lục, Tài liệu tham khảo. Mẫu viết bằng **Java + Firebase**. Chữ đỏ là hướng dẫn điền. | Khung báo cáo (mục 9) | Mẫu chỉ có tiêu đề, không có nội dung mẫu. Ghi "Ngày xx tháng xx năm 2023", Khóa K45, 5 thành viên. |
| `ENGQA.pdf` (19 trang) | Đã đọc đủ 19 trang. Bảng phân công 4 người (Thịnh, Bảo, Phú, Đăng), mô tả màn hình và chức năng; 4 ghi chú gạch đầu dòng ở trang 4; use case + đặc tả + sơ đồ hoạt động cho **UC04 Trang chủ, UC05 Tìm kiếm/Lọc, UC06 Đăng câu hỏi, UC07 Chi tiết câu hỏi**. | Chức năng nền, 4 use case có sẵn | **Không có UC01–UC03** (đoán là đăng ký/đăng nhập/hồ sơ). Không có use case cho bình chọn, chấp nhận câu trả lời, trung tâm, âm thanh. |
| File ý tưởng bổ sung | **Không có file riêng nào được đính kèm.** 4 dòng ở cuối trang 4 của ENGQA.pdf có vẻ là ý đó: page cho trung tâm, câu hỏi hình ảnh/âm thanh/ngữ pháp/nghe đọc, up/down vote, chức năng mở rộng cho trung tâm. | Dùng 4 dòng này làm yêu cầu bổ sung | Cần bạn xác nhận có file khác không. |
| File kiến thức nền (`knowledge_base.zip`) | Đã giải nén và đọc mục lục, bảng kiểm kê, phần Firebase. **Đây là giáo trình Android (Android Developer Fundamentals, 34 pptx + 2 bài tập B6/B9), không phải kiến thức tiếng Anh.** Tìm "IELTS/TOEIC" chỉ trùng ngẫu nhiên. | Làm tài liệu tham khảo kỹ thuật (Firestore `addSnapshotListener`, RecyclerView, Room…) | **Không có nội dung ngữ pháp, từ vựng, nghe, đọc nào để đưa vào Firebase.** Xem câu hỏi 1. |
| `android-skills.zip` | 3 SKILL.md (UI, dữ liệu/kiến trúc, kiểm thử) cho **Java + XML**. | Chuẩn kỹ thuật tham chiếu | — |
| Figma | **Không truy cập được** (trang chặn đọc tự động). Chưa xem màn hình nào. | — | Cần ảnh chụp/xuất PNG/PDF từ Figma. |
| GitHub `Ddawng18/MOB-EngQA` | Clone được. 6 nhánh: `main`, `dev`, `BAO`, `ddawng`, `Thinh-dev`, `origin/HEAD→main`. Đã đọc cấu hình Gradle, manifest, toàn bộ Java của `main`/`dev`, và các file Firebase của `Thinh-dev`. | Nền code | Chưa chạy được app. Không có `layout-land`. |

## 2. Hiện trạng code (Giai đoạn 2)

**Công nghệ thực tế:** Java 11, XML layout, Material 3, AGP 9.3.0, compileSdk/targetSdk 37, minSdk 29, giao diện dark-only. Không dùng Kotlin hay Compose → giữ nguyên Java + XML.

**Các nhánh không đồng bộ:**

| Nhánh | Có gì | Thiếu gì |
|---|---|---|
| `main` | Prototype 4 màn hình (Home, Đặt câu hỏi, Chi tiết, Lỗi server), dữ liệu tĩnh | Mọi thứ khác |
| `dev` (= `ddawng`) | `main` + Login/Register/ChooseLevel (UI), bottom nav dùng chung, kiểm tra mạng | **Không có Firebase.** Login chỉ cần nhập đủ ô là "thành công". |
| `Thinh-dev` (commit mới nhất 13/10) | Firebase Auth + Firestore thật cho đăng ký/đăng nhập/chọn trình độ, `SessionManager`, `UserRepository`, `MenuScreenActivity` (hồ sơ), `firestore.rules` (chỉ `users`), `google-services.json` | Không có bottom nav của `dev`. Còn file SQLite cũ (`UserDbHelper`, `PasswordHasher`) không dùng nữa. |

→ Chưa nhánh nào là bản hoàn chỉnh; cần **hợp nhất `Thinh-dev` vào `dev`** trước tiên (sẽ có xung đột ở `activity_home.xml`, `strings.xml`, `AndroidManifest.xml`).

**Bảng kiểm kê chức năng** (đánh giá trên nhánh tốt nhất cho từng mục):

| Chức năng | Hiện trạng | Bằng chứng từ code | Việc cần làm |
|---|---|---|---|
| Đăng nhập/đăng ký email | **Có thật, ở `Thinh-dev`**; ở `dev` chỉ là UI | `UserRepository.login/register` dùng `FirebaseAuth`; ở `dev` `LoginActivity` chỉ kiểm tra ô trống rồi Toast "thành công" | Hợp nhất nhánh |
| Đăng nhập Google, quên mật khẩu | Chỉ là nút Toast "chưa triển khai"; `google-services.json` có `oauth_client: []` nên Google Sign-In **chưa cấu hình** | `LoginActivity` | Thêm SHA-1 + bật provider; làm reset mật khẩu bằng `sendPasswordResetEmail` |
| Duy trì đăng nhập | Có ở `Thinh-dev` | `SessionManager.isLoggedIn()` | OK |
| Chọn trình độ | Có ở `Thinh-dev` (ghi `level` lên Firestore). Lệch tên: code `"Advance"`, tài liệu `"Advanced"` | `ChooseLevelActivity` | Thống nhất tên |
| Notification | Chưa có (Toast "Thông báo (chưa triển khai)" ở nút chuông) | `HomeActivity.btnNotifications` | Làm T12b |
| Hồ sơ | Một phần: hiển thị tên/email/trình độ; "Câu hỏi/trả lời của tôi", thống kê là Toast | `MenuScreenActivity` | Làm thật |
| Trang chủ | **Chỉ UI**, 3 câu hỏi cứng trong mảng, lọc bằng `String.contains` | `HomeActivity.QUESTIONS` | Chuyển sang RecyclerView + Firestore |
| Tìm kiếm | Chỉ lọc 3 phần tử cứng | `renderQuestions()` | Thiết kế lại (mục 5.4) |
| Đăng câu hỏi | **Chỉ UI**; nút "Đăng" chỉ mở màn chi tiết; 5 tag cứng | `MainActivity` (TODO) | Làm thật |
| Hình ảnh | Chưa có (Toast) | `MainActivity`, `QuestionDetailActivity` | Camera/gallery + Storage |
| Âm thanh | **Không có** UI lẫn code | — | Thêm mới |
| Trả lời | Chỉ UI, 3 câu hỏi cứng; nút Gửi là Toast | `QuestionDetailActivity.ANSWERS` | Làm thật |
| Upvote/downvote | Chỉ có "▲ số" cứng; **không có downvote** | `item_answer.xml`, Toast | Làm thật |
| Chấp nhận câu trả lời | Chưa có | — | Làm thật |
| Firebase realtime | **Chưa có listener nào** trong cả 6 nhánh (grep không thấy `addSnapshotListener`) | — | Phần lõi của đồ án |
| Security rules | Chỉ có rule cho `users/{uid}` | `firestore.rules` | Viết đầy đủ + Storage rules |
| Trang trung tâm | Chưa có | — | Thiết kế mới |
| Xoay ngang/dọc | Manifest **không khóa hướng** (tốt) nhưng không có `layout-land`, không lưu state; `HomeActivity` tự `finish()` khi mất mạng nên mất trạng thái | `AndroidManifest.xml` | Làm ở giai đoạn cuối, nhưng dùng RecyclerView + ViewModel ngay từ đầu |

**Vấn đề kỹ thuật cần xử lý:**
1. `google-services.json` đang được commit (project `accountdata-dd29b`). API key Firebase phía client không phải bí mật tuyệt đối, nhưng phải dựa vào Security Rules và giới hạn key; cần thống nhất cả nhóm dùng chung một project Firebase.
2. Rule hiện tại chỉ cho `users`; mọi collection khác sẽ bị từ chối mặc định.
3. README có ERD kiểu SQL (bigint id, `password_hash`, `reputation`, polymorphic votes) → **không khớp với Firestore** và với việc Firebase Auth giữ mật khẩu. Cần viết lại ERD cho Firestore.
4. Code dùng `LinearLayout` + `inflate` thủ công để dựng danh sách → không phù hợp cho phân trang/realtime; cần `RecyclerView`.
5. Mỗi Activity lặp lại đoạn xử lý insets; có thể gom vào `BaseNavActivity`.

## 3. Mâu thuẫn giữa các nguồn

| # | Mâu thuẫn | Đề xuất |
|---|---|---|
| 1 | Giảng viên yêu cầu **upvote + downvote**; ENGQA.pdf ghi "thả tim/Upvote" | Dùng upvote + downvote |
| 2 | ENGQA.pdf: bottom nav = Trang chủ / Tạo câu hỏi / **Hồ sơ**; code `dev`: Home / Ask / **Saved** (Saved trỏ tạm vào màn lỗi) | Theo ENGQA.pdf: đổi Saved → Hồ sơ (trung tâm có thể là mục trong Home hoặc tab thứ 4, chờ Figma) |
| 3 | Tag yêu cầu: Grammar, Vocabulary, Listening, Reading, Writing, Speaking, Pronunciation, IELTS, TOEIC; code chỉ có Ngữ pháp, Từ vựng, Phát âm, Nghe, Viết | Lưu `categories` trong Firestore, đủ 9 nhóm |
| 4 | ENGQA.pdf: ảnh ≤5MB JPG/PNG; yêu cầu mới có âm thanh | Thêm giới hạn âm thanh (đề xuất ≤10MB, mp3/m4a/aac/wav) – cần bạn xác nhận |
| 5 | ENGQA.pdf: hệ thống thông báo cho chủ câu hỏi (UC07); nay là yêu cầu bổ sung | Đã chuyển lên nhóm A, chọn Notification |
| 6 | UC04–UC07 ghi "tối ưu trên cả máy tính" – ứng dụng là Android | Sửa thành điện thoại/máy tính bảng, ngang/dọc |
| 7 | Tên trình độ "Advance" vs "Advanced" | Dùng "Advanced" |
| 8 | Mẫu đồ án: Chương 6 đánh số 5.1–5.4; Chương 5 có hai mục "5.1" | Không sao chép lỗi; đánh số lại 6.x và 5.1/5.2/5.3 |
| 9 | Mẫu: 5 thành viên; ENGQA.pdf: 4 thành viên | Hỏi nhóm bạn mấy người |
| 10 | File "kiến thức nền" là giáo trình Android, không phải kiến thức tiếng Anh | Câu hỏi 1 |

## 4. Phạm vi (Giai đoạn 3)

**Nhóm A – Bắt buộc (làm trước):** Tài khoản/hồ sơ (email), chọn trình độ, Trang chủ realtime, tìm/lọc, đăng câu hỏi (văn bản + ảnh + âm thanh), chi tiết + trả lời realtime, upvote/downvote, chấp nhận câu trả lời, **Notification (yêu cầu bổ sung ngày 13/10, chọn 1 trong 3: Notification / JobScheduler / Alarm → chọn Notification)**, Security Rules, xoay ngang/dọc.

**Nhóm B – Mở rộng quan trọng:** Trang trung tâm (xem + quản lý + phân quyền), báo cáo nội dung, đăng nhập Google, sửa/xóa nội dung của mình, màn hình danh sách thông báo.

**Nhóm C – Để sau:** Đăng ký khóa học/tư vấn, thanh toán, push FCM khi app đã đóng hẳn (cần Cloud Functions + Blaze), thống kê chuyên sâu, kiểm duyệt nâng cao, nội dung bài học từ "kiến thức nền".

Lý do đặt trung tâm ở nhóm B: giảng viên gọi đây là mở rộng; nhưng không thể làm trước khi có model dữ liệu, vote và rules.

## 5. Kiến trúc đề xuất (Giai đoạn 4 – phần quyết định chính)

### 5.1 Chọn Cloud Firestore (không đổi sang Realtime Database)
- Dự án đã có `firebase-firestore` + `firebase-auth` và code `UserRepository` dùng Firestore.
- Cần truy vấn có điều kiện + sắp xếp + phân trang (lọc theo chủ đề, mới nhất, phổ biến), Firestore làm tốt hơn RTDB.
- `addSnapshotListener` đáp ứng yêu cầu realtime, kể cả khi sửa trực tiếp từ Firebase Console.
- Nhược điểm cần nói rõ khi bảo vệ: không có full-text search; mỗi lần đọc tính phí đọc (không quan trọng ở quy mô đồ án).

### 5.2 Giữ Java + XML, bổ sung tối thiểu
Thêm: `RecyclerView` (đã có trong Material/AndroidX), `ViewModel + LiveData` (`androidx.lifecycle`), Glide (ảnh), Media3/`MediaPlayer` (phát âm thanh), `MediaRecorder` (ghi âm). Không đổi AGP hay ngôn ngữ.

Cấu trúc package đề xuất (giữ các Activity hiện có, chỉ thêm):
```
com.example.engqa
  ui/        (Activity, Adapter)   – giữ nguyên chỗ hiện tại trong giai đoạn đầu, tách dần
  data/      QuestionRepository, AnswerRepository, VoteRepository, CenterRepository, UserRepository
  model/     User, Question, Answer, Center …
  viewmodel/ HomeViewModel, QuestionDetailViewModel …
```
Mô hình listener: **Repository trả về `ListenerRegistration`; ViewModel đăng ký trong `onStart` của màn hình và gỡ trong `onStop`** (hoặc `onCleared`) → xoay màn hình không tạo listener trùng.

### 5.3 Thiết kế Firestore
| Collection | ID | Trường chính | Ghi chú |
|---|---|---|---|
| `users/{uid}` | = Auth uid | username, email, level, avatarUrl, questionCount, acceptedCount, createdAt | Không chứa quyền quản trị |
| `categories/{id}` | slug | name, order | Seed 9 nhóm |
| `questions/{qid}` | auto | authorId, authorName, title, body, categoryId, tags[], level, type (grammar/vocab/…), imageUrls[], audioUrl, passageText, answerCount, score, isSolved, acceptedAnswerId, searchKeywords[], createdAt, updatedAt | Ảnh/âm thanh lưu **URL Storage**, không Base64 |
| `questions/{qid}/answers/{aid}` | auto | authorId, authorName, body, upCount, downCount, isAccepted, createdAt | `answerCount` trên question là số đếm dự phòng |
| `questions/{qid}/answers/{aid}/votes/{uid}` | = uid người vote | value (+1/-1) | Mỗi người 1 doc/câu trả lời ⇒ đảm bảo 1 bình chọn |
| `reports/{id}` | auto | targetPath, reporterId, reason, status, createdAt | Nhóm B |
| `notifications/{id}` | auto | recipientId, actorId, actorName, type (answer/vote/accepted), questionId, title, read, createdAt | Nhóm A. Rules: chỉ người nhận đọc/đánh dấu đã đọc; chỉ người đã đăng nhập tạo, với `actorId == auth.uid` và không tự thông báo cho mình |
| `centers/{cid}` | auto | name, logoUrl, description, address, phone, email, managerUids[], status | `status` do admin duyệt |
| `centers/{cid}/courses/{id}` | auto | title, description, level, schedule | Nhóm B |
| `centers/{cid}/posts/{id}` | auto | title, body, imageUrl, createdAt | Nhóm B |
| `admins/{uid}` | = uid | (rỗng) | **Chỉ tạo bằng Firebase Console**, rules cấm client ghi |

### 5.4 Tìm kiếm
Firestore không có full-text search. Đề xuất cho đồ án: (a) lọc theo `categoryId`/`type` bằng truy vấn thật; (b) tìm từ khóa bằng mảng `searchKeywords` (tiêu đề tách từ, bỏ dấu, chữ thường) và `array-contains` **một từ**, sau đó lọc tiếp phía client trên trang đã tải. Nói rõ giới hạn khi bảo vệ (không tìm cụm từ giữa chừng, không xếp hạng độ liên quan). Dịch vụ ngoài như Algolia/Typesense là nhóm C.

### 5.5 Upvote/downvote an toàn
- Client chạy **Firestore transaction**: đọc `votes/{uid}` hiện tại → tính delta (none→+1, +1→-1, …) → ghi `votes/{uid}` và cập nhật `upCount`/`downCount` trong cùng một transaction. Transaction giải quyết nhiều người vote đồng thời và bấm liên tục (nút bị vô hiệu hóa trong lúc chờ).
- **Security Rules** dùng `getAfter()` để kiểm tra: counter chỉ được thay đổi đúng bằng delta của vote tương ứng và chỉ bởi người đăng nhập, `value ∈ {1,-1}`, doc id = uid. Như vậy client không thể tự đặt điểm tùy ý.
- Giới hạn trung thực: rules vẫn khó chống mọi kiểu gian lận. Phương án chắc chắn hơn là **Cloud Functions** (trigger khi `votes` đổi → cập nhật counter), nhưng cần gói Blaze. Đề xuất: dùng transaction + rules cho đồ án, ghi Cloud Functions vào hướng phát triển.

### 5.6 Realtime – listener cần đăng ký
| Màn hình | Listener | Hủy khi |
|---|---|---|
| Home | `questions` orderBy createdAt desc limit N (+ lọc category) | `onStop` |
| Chi tiết | `questions/{qid}` + `answers` orderBy isAccepted desc, upCount desc | `onStop` |
| Hồ sơ | `users/{uid}`, câu hỏi/câu trả lời của mình | `onStop` |
| Trung tâm | `centers/{cid}` (+ courses/posts) | `onStop` |
| Thông báo | `notifications` where recipientId == uid | gắn ở mức Application để còn nhận khi app ở nền, xem 5.9 |

Phân trang: listener cho trang đầu (realtime) + nút/scroll "tải thêm" dùng `startAfter` (các trang sau là truy vấn một lần). Trạng thái UI: Loading / Empty / Error (+ Thử lại) / Success. Cache ngoại tuyến của Firestore bật mặc định trên Android; dùng `snapshot.getMetadata().isFromCache()` để hiển thị "đang xem dữ liệu đã lưu", và `hasPendingWrites()` để phân biệt chưa đồng bộ.

### 5.7 Phân quyền trung tâm
- Tài khoản thường **không** tự trở thành quản lý trung tâm. Quy trình: người dùng gửi yêu cầu (doc `centers` với `status = "pending"`, `managerUids` rỗng hoặc chính họ nhưng chưa có hiệu lực) → **admin duyệt bằng Firebase Console** (đặt `status = "approved"` và thêm uid vào `managerUids`) → rules chỉ cho uid trong `managerUids` của center `approved` được sửa trang và nội dung con. Admin được xác định bằng `exists(/databases/.../admins/$(uid))`, không bằng trường trong hồ sơ.
- Tối thiểu cho đồ án: xem danh sách/trang trung tâm + manager sửa thông tin và đăng bài; đăng ký khóa học là nhóm C.

### 5.8 Storage
`/questions/{uid}/{qid}/...` cho ảnh (≤5MB, `image/*`) và âm thanh (≤10MB, `audio/*`), rules kiểm tra `request.auth.uid`, kích thước, contentType; ai cũng đọc được. **Cần kiểm tra:** Firebase hiện yêu cầu gói Blaze để dùng Cloud Storage cho project mới; nếu nhóm chỉ dùng gói Spark thì cần phương án thay thế (xem câu hỏi 3). Tôi chưa xác minh được điều này từ môi trường hiện tại.


### 5.9 Notification (yêu cầu bổ sung)
**Chọn Notification** (không dùng JobScheduler/Alarm). Lý do: cần báo ngay khi có tương tác (UC07); JobScheduler/WorkManager chạy định kỳ tối thiểu ~15 phút và Alarm dành cho hẹn giờ nên không đúng bản chất "sự kiện".

Cơ chế (Java, `NotificationCompat`):
1. Tạo `NotificationChannel` "EngQA" khi khởi động app (`Application.onCreate`).
2. Xin quyền `POST_NOTIFICATIONS` lúc chạy (bắt buộc từ Android 13; minSdk 29 nên phải kiểm tra phiên bản).
3. Khi A trả lời/vote/chấp nhận, client của A ghi 1 doc vào `notifications` (cùng transaction/batch với thao tác chính).
4. Máy B có listener `notifications where recipientId == uid`. Khi gặp `DocumentChange.Type.ADDED` **và** không phải snapshot đầu tiên (tránh báo lại cả đống cũ) thì gọi `NotificationManager.notify()`. Bấm vào thông báo → `PendingIntent` mở `QuestionDetailActivity` với `questionId`.
5. Có badge/ danh sách trong app đánh dấu `read`.

**Giới hạn cần nói thật khi bảo vệ:** listener Firestore chỉ chạy khi tiến trình app còn sống (đang mở hoặc chạy nền chưa bị hệ thống tắt). Nếu người dùng vuốt tắt app hẳn thì sẽ không nhận được thông báo. Muốn nhận khi app đóng hoàn toàn phải dùng FCM + Cloud Functions (cần gói Blaze) → nhóm C. Phiên bản đồ án: Foreground Service nhẹ là **không** khuyến nghị (phức tạp, tốn pin); chấp nhận giới hạn trên và demo khi app đang chạy/nền.

## 6. Kế hoạch thực hiện (Giai đoạn 5)

Thời gian ước lượng cho sinh viên làm nhóm; chưa biết hạn nộp nên chia theo **buổi/giai đoạn** để chỉnh sau. "Phụ thuộc" thể hiện thứ tự.

| Mã | Công việc | Phụ thuộc | File chính | Hoàn thành khi | Ưu tiên | Ước lượng |
|---|---|---|---|---|---|---|
| T01 | Hợp nhất `Thinh-dev`→`dev`, giải xung đột, build chạy được; tạo nhánh làm việc; thống nhất 1 project Firebase | — | manifest, `strings.xml`, `activity_home.xml`, `build.gradle.kts` | App build và chạy trên emulator, đăng nhập thật | A | 1 buổi |
| T02 | Firestore model + `firestore.rules` + seed `categories` + dữ liệu mẫu (đánh dấu "mẫu") | T01 | `model/*`, `firestore.rules` | Console có dữ liệu; rules test trên Rules Playground | A | 1 buổi |
| T03 | Hoàn thiện auth: quên mật khẩu, kiểm tra lỗi, hồ sơ cơ bản, đồng bộ tên "Advanced" | T01 | `LoginActivity`, `UserRepository`, `MenuScreenActivity` | Đăng ký→chọn trình độ→đăng xuất→đăng nhập lại ổn | A | 1 buổi |
| T04 | Home dùng RecyclerView + `QuestionRepository` + listener realtime + 4 trạng thái | T02 | `HomeActivity`, `QuestionAdapter`, `HomeViewModel` | A đăng 1 câu hỏi từ Console → Home B tự hiện, không kéo refresh | A | 2 buổi |
| T05 | Lọc theo chủ đề + sắp xếp + tìm từ khóa (`searchKeywords`) + phân trang | T04 | `HomeActivity`, `QuestionRepository` | Lọc/tìm đúng, tải thêm hoạt động | A | 1–2 buổi |
| T06 | Đăng câu hỏi văn bản + validate | T02, T03 | `MainActivity`, `QuestionRepository` | Câu hỏi hiện trên Home của thiết bị khác | A | 1 buổi |
| T07 | Đính kèm ảnh (camera/gallery) + upload Storage + hiển thị | T06 (+ quyết định Storage) | `MainActivity`, Storage rules, `FileProvider` | Ảnh >5MB bị từ chối; ảnh hiện sau khi xoay | A | 2 buổi |
| T08 | Âm thanh: chọn file/ghi âm, upload, phát/tạm dừng | T07 | `MainActivity`, `QuestionDetailActivity`, `AudioPlayerView` | Phát lại được sau khi xoay | A | 2 buổi |
| T09 | Chi tiết + câu trả lời realtime | T04, T06 | `QuestionDetailActivity`, `AnswerAdapter`, `AnswerRepository` | B thấy câu trả lời mới của A tức thì | A | 2 buổi |
| T10 | Upvote/downvote (transaction + rules `getAfter`) | T09, T02 | `VoteRepository`, `firestore.rules` | 2 thiết bị vote đồng thời, điểm đúng; bấm liên tục không tăng nhiều lần | A | 2 buổi |
| T11 | Chấp nhận câu trả lời, `isSolved`, đưa lên đầu, thống kê hồ sơ | T09 | `QuestionDetailActivity`, `MenuScreenActivity` | Chỉ chủ câu hỏi chấp nhận được; trạng thái cập nhật mọi màn | A | 1 buổi |
| T12 | Trung tâm: model, danh sách/trang chi tiết realtime, manager sửa/đăng bài, quy trình duyệt | T02, T03 | `Center*Activity`, `CenterRepository`, rules | Sửa tên trung tâm trên Console → màn đang mở đổi theo | B | 3 buổi |
| T12b | **Notification**: channel, quyền POST_NOTIFICATIONS, ghi doc `notifications`, listener → `notify()`, PendingIntent mở chi tiết | T09, T10, T11 | `NotificationRepository`, `EngQAApp`, `AndroidManifest.xml`, rules | Máy A trả lời → máy B (app đang chạy/nền) hiện thông báo, bấm mở đúng câu hỏi; không báo lại thông báo cũ | A | 2 buổi |
| T13 | Báo cáo nội dung, sửa/xóa theo quyền, màn danh sách thông báo | T09, T11, T12b | `ReportRepository`, `NotificationActivity` | Chạy được các luồng | B | 2 buổi |
| T14 | Layout ngang (`layout-land`), lưu state, kiểm tra tablet | T04–T11 | `res/layout-land/*`, ViewModel | Xoay giữa lúc nhập/phát âm thanh không mất dữ liệu, không nhân đôi listener | A | 2 buổi |
| T15 | Kiểm thử tổng thể theo kế hoạch Phần IX, 2 thiết bị, ghi kết quả thật | T01–T14 | Bảng kiểm thử | Có log kết quả thật | A | 2 buổi |
| T16 | Viết báo cáo, chụp ảnh giao diện dọc/ngang, vẽ lại sơ đồ khớp code, README | T15 | tài liệu | Báo cáo đủ 6 chương + phụ lục | A | 3–4 buổi |

Tài liệu (UC01–UC03 còn thiếu, use case cho vote/accepted/trung tâm, workflow, phân rã chức năng, sequence) nên viết **song song** với T04–T12 để khớp với code thực tế.

## 7. Mục lục báo cáo EngQA (bám mẫu)

- **Bìa:** giữ bìa UEH của mẫu; đổi đề tài, danh sách nhóm, ngày; GVHD TS. Đặng Ngọc Hoàng Thành (theo mẫu).
- **Chương 1. Tổng quan** — 1.1 Giới thiệu; 1.2 Tính cấp thiết; 1.3 Công cụ phát triển (a Java, b Android SDK, c Android Studio, d Firebase); 1.4 Kiểm thử trên emulator và thiết bị.
- **Chương 2. Phân tích và thiết kế hệ thống** — 2.1 Sơ đồ luồng công việc; 2.2 Sơ đồ phân rã chức năng. *(Bổ sung vì cần: 2.3 Danh sách actor/quyền; 2.4 Use case + đặc tả (UC01–UC0x); 2.5 Sơ đồ hoạt động/trình tự cho đăng câu hỏi, trả lời, bình chọn.)*
- **Chương 3. Thiết kế giao diện** — 3.1 Trang chủ; 3.2 Chi tiết câu hỏi; 3.3 Tìm kiếm; 3.4 Đăng câu hỏi (thay "giỏ hàng"); 3.5 Đăng nhập/đăng ký/chọn trình độ; 3.6 Hồ sơ; 3.7 Trang trung tâm; mỗi mục có ảnh dọc và ngang.
- **Chương 4. Xây dựng cơ sở dữ liệu** — 4.1 Thực thể và thuộc tính (Firestore); 4.2 Sơ đồ tương tác hướng đối tượng; 4.3 Một số truy vấn (a danh sách câu hỏi mới nhất realtime; b lọc theo chủ đề; c tìm theo từ khóa; d bình chọn bằng transaction); 4.4 Security Rules.
- **Chương 5. Xây dựng ứng dụng** — 5.1 Cấu trúc ứng dụng; 5.2 Kết nối Firebase; 5.3 Nội dung các trang (a…g, mỗi trang: mô tả chức năng + thao tác dữ liệu Firebase).
- **Chương 6. Quy trình thực thi** — 6.1 Đăng ký; 6.2 Đăng nhập; 6.3 Đăng câu hỏi; 6.4 Trả lời; 6.5 Bình chọn; 6.6 Quản lý trung tâm (nếu làm).
- **Phụ lục** — link GitHub; bảng phân công (nhiệm vụ cụ thể, kèm ảnh GitHub/commit); kết quả kiểm thử thật.
- **Tài liệu tham khảo.**

## 8. Bảng tiến độ

| Công việc | Trạng thái | File liên quan | Kết quả kiểm thử | Tiếp theo |
|---|---|---|---|---|
| Giai đoạn 1: kiểm kê | Đã hoàn thành (trừ Figma, file kiến thức nền sai nội dung) | — | n/a | — |
| Giai đoạn 2–5: hiện trạng, phạm vi, kiến trúc, kế hoạch | Đã hoàn thành (chờ bạn duyệt) | tài liệu này | n/a | Duyệt |
| T01 hợp nhất nhánh | Chưa làm | — | — | Sau khi duyệt |

## 9. Câu hỏi cần bạn xác nhận
1. File kiến thức nền là giáo trình Android. Bạn muốn **(a)** dùng nó chỉ làm tài liệu tham khảo kỹ thuật và tạo dữ liệu mẫu tiếng Anh tự đánh dấu "minh họa", hay **(b)** gửi đúng file kiến thức tiếng Anh?
2. Có file ý tưởng bổ sung nào ngoài 4 dòng cuối trang 4 của ENGQA.pdf không?
3. Nhóm dùng gói Firebase nào (Spark hay Blaze)? Điều này quyết định có dùng được Storage cho ảnh/âm thanh và Cloud Functions hay không.
4. Hạn nộp và số thành viên (mẫu 5 người, ENGQA.pdf 4 người)?
5. Gửi giúp ảnh Figma (xuất PNG/PDF) hoặc ít nhất ảnh các màn hình.
6. Đồng ý chọn Cloud Firestore, giữ Java + XML, và hợp nhất `Thinh-dev` vào `dev` làm nền?
