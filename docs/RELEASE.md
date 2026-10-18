# Phát hành & phân phối (để mọi máy dùng được)

## Vì sao mỗi máy dev chạy được mà máy người dùng lại không?

| Thành phần | Phụ thuộc chữ ký app? |
|---|---|
| Email/mật khẩu | Không (chỉ cần mạng + Firebase) |
| Đọc/ghi Firestore, feed, promos | Không |
| **Google Sign-In** | **Có** — Google kiểm tra SHA-1 của APK đang cài |
| **Phone Sign-In** | **Có** (Play Integrity/reCAPTCHA gắn với SHA-1) |
| Thông báo, Room, media | Không |

Mỗi máy dev có `~/.android/debug.keystore` **khác nhau** → SHA-1 khác nhau. Vì vậy bản **debug** chỉ
chạy Google/Phone login trên máy đã đăng ký SHA-1 của máy đó.

**Cách đúng:** build bản **release** ký bằng **một keystore cố định**, đăng ký SHA-1 của keystore đó
**một lần**. Mọi máy cài cùng file APK/AAB đó đều dùng được đầy đủ tính năng.

---

## 1. Tạo keystore release (làm một lần, giữ cẩn thận)

```powershell
keytool -genkeypair -v `
  -keystore C:\Users\LENOVO\keys\engqa-release.jks `
  -alias engqa -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass <MAT_KHAU_KHO> -keypass <MAT_KHAU_KEY> `
  -dname "CN=EngQA, OU=MOB, O=EngQA, L=HCM, C=VN"
```

- Đặt keystore **ngoài repo** (ví dụ `C:\Users\LENOVO\keys\`).
- **Sao lưu** file `.jks` + mật khẩu. Mất keystore = không thể cập nhật app trên Play.
- `.gitignore` đã chặn `*.jks`, `*.keystore`, `keystore.properties` → không bị commit.

## 2. Cấu hình signing

Tạo `keystore.properties` ở thư mục gốc repo (đã git-ignore), theo mẫu
`keystore.properties.example`:

```properties
storeFile=C:/Users/LENOVO/keys/engqa-release.jks
storePassword=<MAT_KHAU_KHO>
keyAlias=engqa
keyPassword=<MAT_KHAU_KEY>
```

`app/build.gradle.kts` tự đọc file này. Không có file → bản release để **unsigned** (không cài được),
nên không bao giờ vô tình phát hành bản không ký.

## 3. Build bản phát hành

```powershell
# AAB để đăng Play Store (khuyến nghị)
.\gradlew.bat bundleRelease

# APK để gửi trực tiếp / test nội bộ
.\gradlew.bat assembleRelease
```

- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

Nhớ tăng `versionCode`/`versionName` trong `app/build.gradle.kts` cho mỗi lần phát hành.

## 4. Đăng ký SHA-1 của keystore release với Firebase (quan trọng)

```powershell
keytool -list -v -keystore C:\Users\LENOVO\keys\engqa-release.jks -alias engqa
```

Lấy `SHA1:` (bỏ dấu `:`) rồi chạy:

```powershell
node tools/fix-google-signin.js <sha1-40-hex>
```

Script thêm SHA-1 vào Android app trên Firebase và tải lại `app/google-services.json` (giữ các
SHA-1 cũ). Sau đó **build lại** để đóng gói config mới.

> Chỉ cần đăng ký **một lần** cho keystore release. Mọi máy cài APK/AAB ký bằng keystore này đều
> đăng nhập Google/Phone được.

## 5. Nếu phát hành qua Google Play (Play App Signing)

Play có thể **ký lại** app bằng khoá của Google. Khi đó app trên máy người dùng mang chữ ký của
**Play App Signing**, không phải keystore của bạn.

1. Play Console → **Setup → App signing** → lấy **SHA-1 của App signing key**.
2. Chạy lại `node tools/fix-google-signin.js <sha1-play>` (giữ luôn SHA-1 keystore upload).
3. Upload AAB.

## 6. Google provider & OAuth consent

Firebase Console → **Authentication → Sign-in method**:

- Bật **Google** (nếu chưa).
- Bật **Phone** nếu dùng đăng nhập số điện thoại.

Google Cloud Console → **OAuth consent screen**: điền tên app, email hỗ trợ, (khi public) chính sách
riêng tư. Thiếu bước này Google có thể chặn đăng nhập.

## 7. Giới hạn API key (nên làm)

Google Cloud Console → **APIs & Services → Credentials** → API key của app:

- **Application restrictions**: Android apps → thêm package `com.example.engqa` + **SHA-1 release**
  (và SHA-1 Play nếu dùng Play App Signing).
- **API restrictions**: chỉ các API cần dùng (Identity Toolkit, Cloud Firestore, Firebase Storage…).

Nếu giới hạn theo SHA-1 mà quên SHA-1 release, **mọi máy người dùng** sẽ bị lỗi khi gọi Firebase dù
đăng nhập email vẫn "có vẻ" chạy.

## 8. Kiểm tra trên máy sạch

```powershell
adb uninstall com.example.engqa      # gỡ bản debug cũ (khác chữ ký)
adb install app\build\outputs\apk\release\app-release.apk
```

Checklist:

- [ ] Đăng ký tài khoản mới → chọn trình độ → vào Home.
- [ ] Đăng xuất → đăng nhập lại (email/mật khẩu).
- [ ] **Tiếp tục với Google** → chọn tài khoản → vào Home (không còn mã 10).
- [ ] Home hiện câu hỏi + thẻ **Quảng bá**; chip **Trung tâm** lọc đúng.
- [ ] Mở chi tiết promo, gọi/mở web, đăng ký tư vấn.
- [ ] Đăng câu hỏi có ảnh/âm thanh (cần **Storage đã bật** — xem `docs/FIREBASE_SETUP.md`).
- [ ] Bật thông báo → nhận thông báo khi có người trả lời.
- [ ] Xoay ngang: feed 2 cột, không mất trạng thái.

## 9. Cách phân phối

| Cách | Phù hợp | Ghi chú |
|---|---|---|
| **Google Play (internal testing → production)** | Phát hành thật | AAB; cần Play App Signing SHA-1 |
| **Firebase App Distribution** | Test nội bộ nhanh | Upload APK/AAB, mời tester qua email |
| **Gửi APK trực tiếp** | Demo đồ án | Người dùng phải bật "cài từ nguồn không xác định" |

## 10. Lưu ý chung

- `google-services.json` là cấu hình **dùng chung** cho mọi máy — không tạo project Firebase riêng
  cho từng người.
- Không commit `keystore.properties`, `*.jks`, service-account key.
- Bản **debug** chỉ dùng để dev; đừng phát hành cho người dùng.
- Firebase Storage hiện **chưa bật** → ảnh/âm thanh chưa upload được cho bất kỳ máy nào; bật Storage
  rồi deploy `storage.rules` (xem `docs/FIREBASE_SETUP.md`).
