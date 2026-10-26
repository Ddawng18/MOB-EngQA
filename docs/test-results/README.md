# Kết quả kiểm thử EngQA (2026-10-26)

Môi trường: Windows, Android Studio JBR/Java 17 cho Gradle; JDK 26 cho Firebase Emulator.

| Hạng mục | Lệnh | Kết quả |
|---|---|---|
| Build | `.\gradlew.bat assembleDebug` | BUILD SUCCESSFUL |
| Unit test | `.\gradlew.bat testDebugUnitTest` | 86/86 pass |
| Instrumentation | `.\gradlew.bat connectedDebugAndroidTest` | 23/23 pass trên Samsung SM-S908E (API 30) |
| Firestore rules | `npm test` trong `rules-test/` với emulator | 12/12 pass |
| Lint | `.\gradlew.bat lintDebug` | 0 error, 279 warning |
| JobScheduler | `adb shell cmd jobscheduler run -f com.example.engqa 7301` | job chạy, gọi `jobFinished` |
| Xoay màn hình | Chi tiết câu hỏi, chế độ ngang | không crash, ảnh `docs/images/detail_land.png` |
| Offline | Tắt Wi-Fi/data rồi mở lại app | feed cache + banner, ảnh `docs/images/offline.png` |

## Ghi chú

- Các thay đổi upload media hiện tại không bị sửa; media vẫn đi theo luồng cũ.
- FCM, Cloud Functions và Admin SDK xoá Auth thật chưa triển khai (xem Chương 7 báo cáo).
- Để chạy rules test cần JDK 21+:
  `npx firebase emulators:exec --project demo-engqa --only firestore --config ../firebase.json "npm test"`.
