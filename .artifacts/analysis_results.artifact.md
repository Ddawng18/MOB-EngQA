# Báo cáo tích hợp Firebase Auth & Cloud Firestore cho EngQA

## 1. Khảo sát dự án đích (`TARGET_PROFILE`)

| # | Cần xác định | Kết quả khảo sát |
|---|---|---|
| T1 | `applicationId` / namespace | `com.example.engqa` |
| T2 | Ngôn ngữ | Java 11 |
| T3 | UI | XML View (`ConstraintLayout`, `LinearLayout`) |
| T4 | Điều hướng | Nhiều Activity (`LoginActivity`, `RegisterActivity`, `ChooseLevelActivity`, `MenuScreenActivity`, `HomeActivity`, `MainActivity`,...) |
| T5 | SDK & Version Catalog | `minSdk 29`, `compileSdk 37`, có dùng `libs.versions.toml` |
| T6 | Firebase | Đã thêm plugin `google-services`, thư viện `firebase-bom`, `firebase-auth`, `firebase-firestore` |
| T7 | Auth cũ | Có `UserDbHelper.java` (SQLite cục bộ) — đã chuyển sang `UserRepository` dùng Firebase, giữ lại class cũ không dùng hoặc chuyển song song |
| T8 | Theme | Dark-only (`Theme.Material3.Dark.NoActionBar`), áp dụng màu nền trực tiếp và `EdgeToEdge` |
| T9 | Tên màn hình Home / Profile | `HomeActivity` và `MenuScreenActivity` |
| T10 | Resources | Đã đồng bộ màu, chuỗi, drawable (`bg_card`, `bg_button_orange`, `ic_arrow_right`,...) |

**Nhánh áp dụng**: T3 = XML, T2 = Java, T4 = nhiều Activity, T6 = đã bổ sung Firebase, T7 = chuyển từ SQLite sang Firebase Auth + Firestore.

---

## 2. Danh sách tệp đã tạo / sửa

- **Tạo mới**:
  - `UserRepository.java`: Giao tiếp Firebase Auth + Firestore (`users/{uid}`).
  - `firestore.rules`: Security rules cho Firestore collection `users`.
- **Cập nhật**:
  - `SessionManager.java`: Quản lý phiên đăng nhập qua `FirebaseAuth.getCurrentUser()`.
  - `LoginActivity.java`: Đăng nhập với Firebase Auth, kiểm tra session lúc khởi động.
  - `RegisterActivity.java`: Đăng ký tài khoản Firebase Auth + khởi tạo document trên Firestore.
  - `ChooseLevelActivity.java`: Cập nhật trường `level` của user lên Firestore.
  - `MenuScreenActivity.java`: Hiển thị thông tin profile động từ Firestore.
  - `gradle/libs.versions.toml` & `app/build.gradle.kts`: Khai báo Firebase BoM, Auth, Firestore.

---

## 3. Việc người dùng PHẢI làm thủ công

1. **Tạo Project trên Firebase Console**:
   - Tải tệp **`google-services.json`** đặt vào thư mục `app/` của dự án (`D:/Mobile/MOB-EngQA/app/google-services.json`).
2. **Kích hoạt Authentication**:
   - Bật nhà cung cấp **Email/Password** trong Firebase Console -> Authentication.
3. **Tạo Cloud Firestore Database**:
   - Khởi tạo Firestore Database và publish nội dung file `firestore.rules` đã tạo vào mục **Rules**.

---

## 4. Khác biệt & Lý do
- Sử dụng trực tiếp chuỗi định nghĩa dependency Firebase trong `app/build.gradle.kts` thay vì version catalog để tránh lỗi sync cache chưa cập nhật.
- Xử lý kiểm tra `isFinishing()` trong các callback bất đồng bộ để tránh crash khi người dùng thoát màn hình trước khi response trả về.

---

## 5. Đề xuất dọn dẹp
- Có thể xóa hoặc lưu trữ lại `UserDbHelper.java` và `PasswordHasher.java` nếu không còn dùng đến cơ sở dữ liệu SQLite cục bộ (vì đã chuyển hoàn toàn sang Firebase Auth + Firestore).
