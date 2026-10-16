# Cơ chế Đăng ký / Đăng nhập (Auth)

Tài liệu này mô tả luồng xác thực đã triển khai, các file liên quan, cách xử lý lỗi và những
điểm phụ thuộc vào Security Rules.

## 1. Tổng quan

| Chức năng | Trạng thái | Ghi chú |
|---|---|---|
| Đăng ký email/mật khẩu | ✅ | Tạo Auth account + `users/{uid}`; rollback nếu ghi profile lỗi |
| Tên hiển thị duy nhất | ✅ | Kiểm tra trùng trước khi ghi profile |
| Đăng nhập email/mật khẩu | ✅ | Tự tạo profile nếu Auth account có mà document thiếu |
| Duy trì đăng nhập | ✅ | Firebase Auth persistence (mặc định LOCAL) |
| Quên mật khẩu | ✅ | `sendPasswordResetEmail` |
| Xác minh email | ✅ (không bắt buộc) | Gửi sau đăng ký; có "Gửi lại" và "Kiểm tra lại" |
| Đổi mật khẩu | ✅ | Re-authenticate rồi `updatePassword` |
| Đăng xuất | ✅ | `SessionManager.logout()` + dừng watcher thông báo |
| Google / Số điện thoại | ⛔ | Chưa cấu hình (`oauth_client: []`); nút báo "sẽ sớm được hỗ trợ" |

## 2. Luồng

### Đăng ký

```
RegisterActivity
  └─ AuthViewModel.validateRegisterForm()        // tên hiển thị, email, mật khẩu, xác nhận
     └─ UserRepository.register()
        ├─ FirebaseAuth.createUserWithEmailAndPassword()
        ├─ isUsernameTaken(username)             // truy vấn users (đã đăng nhập nên được phép)
        │    └─ trùng? → xoá Auth account vừa tạo → UsernameTakenException
        ├─ users/{uid}.set({username, email, level:null, counters=0, createdAt})
        │    └─ lỗi? → xoá Auth account (rollback)
        ├─ Auth displayName = username
        └─ user.sendEmailVerification()          // best effort
  └─ thành công → SessionManager.onLoginSuccess() → ChooseLevelActivity
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

## 6. Phụ thuộc Security Rules ⚠️

Đăng ký **chỉ chạy được khi `firestore.rules` đã được deploy**. Rules cũ yêu cầu document
`users/{uid}` chứa field `uid` (code không ghi field này vì id document đã là uid), nên create sẽ
bị `PERMISSION_DENIED` và tài khoản vừa tạo bị rollback.

```powershell
cd C:\Users\LENOVO\AndroidStudioProjects\MOB-EngQA
firebase deploy --only firestore:rules --project accountdata-dd29b
```

Rules hiện tại (bản mới) đã:

- Bỏ yêu cầu field `uid` khi tạo profile; chặn ghi field đặc quyền (`isAdmin`/`role`).
- Cho phép người trả lời cập nhật **chỉ** `answerCount` (±1) trên câu hỏi của người khác.
- Cho phép người bình chọn cập nhật **chỉ** `upCount`/`downCount` (±1) và `score` (±2).
- Cho phép cộng **+1** vào `acceptedCount` của tác giả câu trả lời khi chủ câu hỏi chấp nhận.

## 7. Kiểm thử thủ công

1. Mở app → **Đăng ký** → nhập tên hiển thị/email/mật khẩu → chọn trình độ.
2. Đăng xuất → **Đăng nhập** lại bằng tài khoản vừa tạo (kiểm tra duy trì phiên: mở lại app phải
   vào thẳng Home).
3. Nhập sai mật khẩu → phải hiện "Mật khẩu không đúng" (không phải lỗi chung).
4. **Quên mật khẩu** với email đã đăng ký → hộp thư nhận link (nếu mạng tới được Identity Toolkit).
5. Tạo tài khoản thứ hai trùng tên hiển thị → phải bị chặn với "Tên hiển thị này đã được sử dụng".
6. **Hồ sơ → Đổi mật khẩu** → nhập sai mật khẩu hiện tại phải báo lỗi; nhập đúng thì đổi được.
7. Xoay màn hình ở màn đăng nhập/đăng ký → không mất dữ liệu đang nhập, không lặp toast.
