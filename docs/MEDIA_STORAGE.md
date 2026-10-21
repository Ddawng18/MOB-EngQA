# Lưu ảnh / âm thanh bằng liên kết (không cần Blaze)

## Vì sao

Cloud Storage for Firebase **bắt buộc gói Blaze**. Project đang ở Spark nên không tạo được bucket;
mọi upload trả về `StorageException: Object does not exist at location`.

Firestore đã lưu media dưới dạng **URL** (`questions.imageUrls`, `questions.audioUrl`) và giao diện
tải ảnh bằng Glide / phát audio bằng MediaPlayer. Vì vậy chỉ cần **upload tệp lên một host HTTPS
bất kỳ** và lưu URL trả về — **không đổi schema, không đổi màn hình**.

## Cách đang dùng

```
MainActivity (chọn/chụp ảnh, ghi âm)
  └─ CreateQuestionViewModel
       └─ LinkStorageRepository          (data/LinkStorageRepository.java)
            └─ POST https://x0.at       multipart field "file"
                 └─ trả về https://x0.at/xxxx.png | .m4a
       └─ QuestionRepository.createQuestion(...)
            └─ Firestore: imageUrls=[...], audioUrl="https://x0.at/..."
  └─ QuestionAdapter / FeedAdapter / PromoDetail
       └─ Glide.load(url)  → ảnh hiển thị bình thường
       └─ AudioPlayerView  → MediaPlayer.setDataSource(url)
```

- Chạy trên `ExecutorService` (không block main thread), callback được post về main thread.
- Có progress 0–100% (đọc theo buffer 64 KB).
- Giới hạn phía app: ảnh ≤ 5 MB (JPG/PNG), audio ≤ 10 MB.
- Không cần đăng nhập, không cần API key, không cần `storage.rules`.

## Đổi lại sang Firebase Storage (khi có Blaze)

1. Console → **Storage → Get Started** (tạo bucket).
2. `firebase deploy --only storage --project accountdata-dd29b`
3. Trong `CreateQuestionViewModel`, đổi:
   ```java
   private final LinkStorageRepository storageRepository = new LinkStorageRepository();
   ```
   thành:
   ```java
   private final StorageRepository storageRepository = new StorageRepository();
   ```
4. Build lại. `StorageRepository` (Firebase) vẫn giữ nguyên trong repo.

## Đổi sang Cloudinary (khuyến nghị nếu cần link lâu dài)

1. Tạo tài khoản Cloudinary (free).
2. Settings → Upload → **Add upload preset** → Signing mode: **Unsigned**; đặt folder `engqa`,
   giới hạn định dạng/kích thước.
3. Viết một `CloudinaryStorageRepository` cùng interface, gọi:
   `POST https://api.cloudinary.com/v1_1/<cloud_name>/auto/upload`
   với `upload_preset=<preset>` + `file`, rồi lấy `secure_url`.
4. Đổi một dòng trong `CreateQuestionViewModel`.

## Hạn chế cần biết

- **x0.at giữ tệp có thời hạn** (giống 0x0.st), không phải vĩnh viễn. Bài đăng cũ có thể mất ảnh.
  Với đồ án/demo thì đủ; sản phẩm thật nên dùng Firebase Storage (Blaze) hoặc Cloudinary.
- Host công cộng, không kiểm soát uptime/rate limit.
- Link công khai: ai có URL đều xem được (giống mọi URL ảnh).
- Nếu host đổi chính sách (như 0x0.st đã tắt upload), chỉ cần thay lớp upload — schema và UI không đổi.

## Kiểm thử

```powershell
# 1. Build + cài
.\gradlew.bat installDebug

# 2. Trong app: Đặt câu hỏi → đính kèm ảnh → Đăng câu hỏi
# 3. Kiểm tra Firestore: questions/{id}.imageUrls[0] là https://x0.at/...
# 4. Mở lại câu hỏi: ảnh hiển thị; audio phát được
```

Đã kiểm chứng endpoint bằng script: upload PNG → `https://x0.at/...png` (200, `image/png`);
upload WAV → `https://x0.at/...wav` (200, `audio/wav`).
