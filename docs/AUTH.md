# Cơ chế Đăng ký / Đăng nhập (Auth)

Tài liệu này mô tả luồng xác thực đã triển khai, các file liên quan, cách xử lý lỗi và những
điểm phụ thuộc vào Security Rules.

## 1. Tổng quan

| Chức năng | Trạng thái | Ghi chú |
|---|---|---|
| Đăng ký email/mật khẩu | ✅ | Auth + `users/{uid}` + `usernames/{name}` trong một transaction; lỗi tạm thời giữ Auth để đăng nhập hoàn tất |
| Tên hiển thị duy nhất | ✅ | Reservation document `usernames/{name}`, không dùng collection query trên `users` |
| Đăng nhập email/mật khẩu | ✅ | Tự tạo profile nếu Auth account có mà document thiếu |
| Duy trì đăng nhập | ✅ | Firebase Auth persistence (mặc định LOCAL) |
| Quên mật khẩu | ✅ | `sendPasswordResetEmail` |
| Xác minh email | ✅ (không bắt buộc) | Gửi sau đăng ký; có "Gửi lại" và "Kiểm tra lại" |
| Đổi mật khẩu | ✅ | Re-authenticate rồi `updatePassword` |
| Đăng xuất | ✅ | `SessionManager.logout()` + dừng watcher thông báo |
| Google | ✅ (cần cấu hình SHA) | Credential Manager + GoogleSignInClient fallback; cần web client ID và SHA-1/SHA-256 của bản release |
| Số điện thoại | ⛔ | Chưa triển khai |

## 2. Luồng

### Đăng ký

```
RegisterActivity
  └─ AuthViewModel.validateRegisterForm()        // tên hiển thị, email, mật khẩu, xác nhận
     └─ UserRepository.register()
        ├─ FirebaseAuth.createUserWithEmailAndPassword()
        ├─ transaction:
        │    ├─ get users/{uid}                  // nếu đã có (retry) thì dùng lại profile
        │    ├─ get usernames/{name}              // chỉ đọc document cụ thể
        │    │    └─ thuộc uid khác? → UsernameTakenException (xoá Auth vừa tạo)
        │    ├─ set users/{uid}                   // username, email, level:null, counters=0
        │    └─ set usernames/{name}              // {uid, username, createdAt}
        ├─ Auth displayName = username
        └─ user.sendEmailVerification()           // best effort
  └─ thành công → SessionManager.onLoginSuccess() → ChooseLevelActivity
  └─ lỗi Firestore tạm thời → giữ Auth, báo "đăng nhập để hoàn tất"; lần đăng nhập sau
     ensureProfile() sẽ tạo profile bằng displayName đã đặt trước đó.
```

### Đăng nhập

```
LoginActivity
  └─ AuthViewModel.validateLoginForm()
     └─ UserRepository.login()
        ├─ signInWithEmailAndPassword()
        └─ ensureProfile()   // đọc users/{uid}; thiếu thì tạo tối thiểu (self-heal)
  └─ thành công
       ├─ email đã xác minh → HomeActivity
       └─ chưa xác minh    → dialog: [Gửi lại email] / [Tiếp tục]
```

## 3. File liên quan

| Lớp | File | Vai trò |
|---|---|---|
| data | `data/UserRepository.java` | Toàn bộ Auth + profile (chỉ lớp này chạm Firebase) |
| data | `data/RepoErrors.java` | Ánh xạ mã lỗi Firebase → thông báo tiếng Việt |
| viewmodel | `viewmodel/AuthViewModel.java` | Validate form (`FormErrors`), LiveData trạng thái, một-shot event |
| util | `util/Validators.java` | Email, mật khẩu, tên hiển thị, media |
| util | `util/SingleLiveEvent.java` | Event một lần (tránh toast lặp khi xoay màn hình) |
| ui | `LoginActivity.java`, `RegisterActivity.java`, `ChooseLevelActivity.java` | Màn hình |
| ui | `EditProfileActivity.java` | Sửa tên/trình độ + đổi mật khẩu |
| root | `SessionManager.java` | `isLoggedIn()`, `logout()`, bật/tắt NotificationWatcher |

Nguyên tắc: **Activity không gọi Firebase SDK**; chỉ quan sát `LiveData` từ ViewModel.

## 4. Validation

| Trường | Quy tắc | Thông báo |
|---|---|---|
| Email | regex `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` | "Email không hợp lệ" |
| Mật khẩu | ≥ 6 ký tự | "Mật khẩu tối thiểu 6 ký tự" |
| Xác nhận mật khẩu | khớp | "Mật khẩu xác nhận không khớp" |
| Tên hiển thị | 3–24 ký tự, chỉ `A–Z a–z 0–9 . _ -` | "Tên hiển thị chỉ được dùng chữ, số và . _ -" |

`Validators` là Java thuần (không phụ thuộc Android) nên có unit test JVM:
`ValidatorsTest`, `TextUtils2Test`, `TimeUtilsTest` (19 test).

## 5. Ánh xạ lỗi (RepoErrors)

| Mã Firebase | Thông báo hiển thị |
|---|---|
| `ERROR_INVALID_EMAIL` | Email không hợp lệ |
| `ERROR_WRONG_PASSWORD` | Mật khẩu không đúng |
| `ERROR_USER_NOT_FOUND` | Không tìm thấy tài khoản với email này |
| `ERROR_USER_DISABLED` | Tài khoản đã bị khoá |
| `ERROR_TOO_MANY_REQUESTS` | Bạn thao tác quá nhanh, vui lòng thử lại sau |
| `ERROR_EMAIL_ALREADY_IN_USE` | Email này đã được đăng ký |
| `ERROR_WEAK_PASSWORD` | Mật khẩu quá yếu |
| `ERROR_OPERATION_NOT_ALLOWED` | Phương thức đăng nhập này chưa được bật |
| `ERROR_NETWORK_REQUEST_FAILED` | Không có kết nối mạng |
| `PERMISSION_DENIED` (Firestore) | Bạn không có quyền thực hiện thao tác này |
| `UsernameTakenException` | Tên hiển thị này đã được sử dụng |
| `ProfileSetupException` | Tài khoản đã được tạo nhưng chưa lưu được hồ sơ. Vui lòng thử lại hoặc đăng nhập để hoàn tất. |

## 6. Phụ thuộc Security Rules

Đăng ký cần rules mới nhất đã deploy. Nguyên nhân lỗi `Bạn không có quyền thực hiện thao tác này`
trên máy khác là code cũ chạy `users.whereEqualTo("username", …)` trong khi `users` chỉ cho phép
đọc chính mình; Firestore không cho phép một collection query như vậy. Code hiện tại đọc
`usernames/{name}` (một document cụ thể) và ghi `users/{uid}` + `usernames/{name}` trong một
transaction.

```powershell
firebase deploy --only firestore:rules --project accountdata-dd29b
```

Rules hiện tại đã:

- `users/{uid}`: chỉ chủ sở hữu hoặc admin đọc; chủ sở hữu chỉ sửa các field profile/counter được
  phép, không tự cấp quyền.
- `usernames/{name}`: người đã đăng nhập được đọc; chỉ được tạo/cập nhật reservation với `uid`
  của chính mình; không xoá (tên cũ vẫn được giữ chỗ).
- Các collection câu hỏi/answer/vote/promo giữ nguyên giới hạn quyền hiện có.

Migration cho dữ liệu cũ (additive, không sửa `users`):

```powershell
cd tools\seed
node backfill-usernames.js --dry-run
node backfill-usernames.js --apply
```

Đã deploy rules ngày 2026-10-24 và tạo 13 reservation cho dữ liệu hiện có; dry-run sau đó = 0.
Các profile có username cũ không hợp lệ (`Mr Crow`, `PhúNguyễn`, `N Ddawng`) được bỏ qua và vẫn
đăng nhập được, nhưng không được bảo vệ bởi username index cho tới khi đổi tên.

## 7. Kiểm thử thủ công

1. Mở app → **Đăng ký** → nhập tên hiển thị/email/mật khẩu → chọn trình độ.
2. Đăng xuất → **Đăng nhập** lại bằng tài khoản vừa tạo (kiểm tra duy trì phiên: mở lại app phải
   vào thẳng Home).
3. Nhập sai mật khẩu → phải hiện "Mật khẩu không đúng" (không phải lỗi chung).
4. **Quên mật khẩu** với email đã đăng ký → hộp thư nhận link (nếu mạng tới được Identity Toolkit).
5. Tạo tài khoản thứ hai trùng tên hiển thị → phải bị chặn với "Tên hiển thị này đã được sử dụng".
6. **Hồ sơ → Đổi mật khẩu** → nhập sai mật khẩu hiện tại phải báo lỗi; nhập đúng thì đổi được.
7. Xoay màn hình ở màn đăng nhập/đăng ký → không mất dữ liệu đang nhập, không lặp toast.

## 8. Google Sign-In báo mã 10 (DEVELOPER_ERROR)

**Triệu chứng:** bấm "Tiếp tục với Google" → *"Chưa cấu hình Google Sign-In trên Firebase Console
(mã 10)…"*.

**Nguyên nhân:** mã 10 = chữ ký APK không khớp bất kỳ SHA-1 nào đã đăng ký cho Android app trên
Firebase. Thường gặp khi:

- Debug keystore bị tạo mới (máy mới, xoá `~/.android/debug.keystore`) → SHA-1 khác.
- Build bằng keystore release nhưng chưa đăng ký SHA-1 của keystore đó.
- `google-services.json` cũ, chưa có SHA-1 hiện tại.

Kiểm tra SHA-1 đang dùng:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android -keypass android
```

So sánh `SHA1:` với `certificate_hash` trong `app/google-services.json` (bỏ dấu `:`).
Nếu khác, đăng ký + tải lại config bằng một lệnh:

```powershell
node tools/fix-google-signin.js <sha1-40-hex>
```

Script sẽ thêm SHA-1 vào Firebase Android app (giữ các SHA-1 cũ), tải lại
`app/google-services.json` và in web client id. Nếu web client id khác
`res/values/strings.xml` → `default_web_client_id`, cập nhật lại.

Sau đó **build + cài lại** app:

```powershell
.\gradlew.bat installDebug
```

Lưu ý:

- Mỗi máy/keystore cần SHA-1 riêng; thêm nhiều SHA-1 cho cùng app là bình thường (Firebase giữ
  danh sách).
- Nếu dùng build release, chạy lại tool với SHA-1 của keystore release.
- Mã 10 khác với lỗi `auth/operation-not-allowed` (provider Google chưa bật trong
  Authentication → Sign-in method) và `12501` (người dùng huỷ).

