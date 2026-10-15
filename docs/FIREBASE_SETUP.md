# Hướng dẫn tạo Firebase (Firestore + Auth + Storage + Rules + Seed)

Checklist này tạo toàn bộ backend cho EngQA trên project Firebase **`accountdata-dd29b`**
(project number `13331358113`) — đúng project trong `app/google-services.json`.

Thời gian dự kiến: ~10 phút (chưa tính thời gian index build).

---

## Trạng thái hiện tại (đã hoàn thành)

Cập nhật 2026-10-14:

| Hạng mục | Trạng thái |
|---|---|
| Firestore database | ✅ **Đã tồn tại sẵn** — `(default)`, **FIRESTORE_NATIVE**, edition STANDARD, region **`asia-southeast3`**, tạo `2026-10-13T06:04:17Z`. Không cần tạo lại. |
| `firestore.rules` | ✅ Đã deploy **bản mới** (sửa lỗi `users` create từng khiến đăng ký bị `PERMISSION_DENIED`). Đã xác minh ruleset live không còn check `uid` cũ. |
| Firebase Auth user demo | ✅ Đã tạo **7 tài khoản** (verified, displayName đúng, uid khớp dữ liệu seed). Đăng nhập thật đã được kiểm chứng. |
| `storage.rules` | ❌ Chưa deploy — **Firebase Storage chưa được bật** trên project. |
| `firestore.indexes.json` | ✅ Đã deploy — **10/10 index ở trạng thái `READY`**. |
| Seed dữ liệu Firestore | ✅ Đã ghi **56 documents**: 9 categories, 8 questions (+ 6 answers, + votes), 2 centers (+ 3 courses, + 2 posts), 7 users mẫu. |

### Việc còn lại

**Firebase Storage chưa bật.**
`firebase deploy --only storage` báo `Firebase Storage has not been set up on project`. Mở
[Console → Storage](https://console.firebase.google.com/project/accountdata-dd29b/storage) → **Get
Started** (chọn region), rồi deploy lại `storage.rules`.

---

## 0. Chuẩn bị

- Là **Owner/Editor** của project `accountdata-dd29b`.
- Node.js ≥ 18 và npm.
- Trình duyệt để đăng nhập Firebase.

---

## 1. Tạo Firestore Database

> ✅ **Bỏ qua bước này** — database `(default)` đã tồn tại sẵn ở region `asia-southeast3`
> (Native mode, tạo 2026-10-13). Phần dưới chỉ dùng khi tạo project mới.

Firebase Console → chọn project `accountdata-dd29b` → **Build → Firestore Database** →
**Create database**:

| Lựa chọn | Giá trị |
|---|---|
| Security rules | **Start in production mode** (ta sẽ deploy rules thật ở bước 5) |
| Location | `asia-southeast3` (region đang dùng) hoặc `asia-southeast1` cho project mới |

> ⚠️ **Region không sửa được sau khi tạo.** Nếu nhóm đã lỡ tạo ở region khác thì giữ nguyên,
> miễn là tất cả thành viên dùng chung đúng một project/region.
>
> Khi tạo xong, màn hình Console sẽ có collection `users` rỗng — bình thường.

---

## 2. Bật đăng nhập Email/Password

Firebase Console → **Build → Authentication → Get started** →
tab **Sign-in method** → bật **Email/Password** → Save.

(Google Sign-In **không** cần cho giai đoạn này; code hiện đang để nút Google/Phone ở dạng
"chưa triển khai".)

---

## 3. Kiểm tra Cloud Storage

`google-services.json` đã có bucket `accountdata-dd29b.firebasestorage.app` ⇒ Storage đã bật.
Mở **Build → Storage** để xác nhận. Nếu chưa có, bấm **Get started** (giữ rules mặc định, ta sẽ
deploy rules thật ở bước 5).

---

## 4. Cài Firebase CLI và chọn project

```powershell
npm i -g firebase-tools
firebase --version
firebase login
cd C:\Users\LENOVO\AndroidStudioProjects\MOB-EngQA
firebase use accountdata-dd29b
```

`firebase use` sẽ hỏi xác nhận alias — chọn project `accountdata-dd29b`.

> Nếu không muốn `firebase use`, thêm `--project accountdata-dd29b` vào mọi lệnh deploy.

---

## 5. Deploy Rules + Indexes + Storage Rules

Repo đã có sẵn: `firestore.rules`, `firestore.indexes.json`, `storage.rules`, `firebase.json`.

```powershell
firebase deploy --only firestore:rules,firestore:indexes,storage
```

- **Rules:** `firestore.rules`, `storage.rules`.
- **Indexes:** 10 composite/collection-group index. Deploy xong Console →
  Firestore → tab **Indexes** sẽ hiện trạng thái **Building**; đợi **Enabled** (vài phút).
  Trong lúc Building, truy vấn có `orderBy`/`where` kết hợp sẽ báo lỗi *"The query requires an
  index"* — cứ đợi.

---

## 6. Tạo Service Account key (cho seed) — BẮT BUỘC cho bước 7

Firebase Console → ⚙️ **Project settings → Service accounts → Generate new private key** →
**Generate key** → tải file `.json` về, ví dụ:

```
C:\Users\LENOVO\keys\accountdata-dd29b-firebase-adminsdk.json
```

> 🔒 File này = **quyền admin toàn project**. **KHÔNG** commit, **KHÔNG** để trong repo, **KHÔNG**
> gửi chat. Sau khi seed xong nên xoá và rotate key trong Console.

---

## 7. Chạy seed

Có **hai** cách, tuỳ bạn có service-account key hay không:

**Cách A — REST, không cần key** (dùng chính phiên `firebase login`):

```powershell
cd tools\seed
node seed-rest.js --dry-run     # xem kế hoạch
node seed-rest.js               # ghi dữ liệu (tạo cả tài khoản demo)
node seed-rest.js --no-auth     # chỉ ghi Firestore, bỏ qua tạo tài khoản Auth
```

Nếu mạng không tới được `identitytoolkit.googleapis.com`, dùng `--no-auth` và tạo user thủ công
trong Console.

**Cách B — Admin SDK, cần service-account key:**

```powershell
cd tools\seed
npm install

$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\Users\LENOVO\keys\accountdata-dd29b-firebase-adminsdk.json"

npm run seed:dry     # in kế hoạch, KHÔNG ghi gì
npm run seed         # ghi thật
```

Kết quả mong đợi (`seed:dry`):

```
categories : 9
users      : 7
questions  : 8
centers    : 2
documents  : 56
auth users : 7
```

Sau `npm run seed`, Console → Firestore sẽ có các collection:
`categories`, `questions` (+ `answers` + `votes`), `centers` (+ `courses` + `posts`), `users`.
Mọi document seed đều có `isSample: true`.

Nếu muốn xoá sạch dữ liệu mẫu: `npm run seed:clear`.

---

## 8. Tạo admin (tuỳ chọn)

Admin **không** được seed. Vào Console → Firestore → tạo collection **`admins`** → document id =
**uid** của user bạn muốn cấp quyền admin (field bất kỳ, ví dụ `role: "admin"`).

Code client **không thể** ghi vào `admins` (đã chặn trong `firestore.rules`), nên không thể tự
phong quyền.

---

## 9. Build & chạy app để kiểm tra

```powershell
cd C:\Users\LENOVO\AndroidStudioProjects\MOB-EngQA
.\gradlew.bat installDebug
```

Đăng nhập bằng tài khoản demo (mật khẩu chung `Engqa@1234`):

| Email | Username |
|---|---|
| `minh.anh@engqa.demo` | `minh_anh` |
| `lan.teacher@engqa.demo` | `lan_teacher` |
| `khoa.nguyen@engqa.demo` | `anh_khoa` |
| `linh.tran@engqa.demo` | `linhnguyen` |
| `tuan.le@engqa.demo` | `tuan_ng` |
| `hoa.pham@engqa.demo` | `hoa_pham` |
| `center.manager@engqa.demo` | `engqa_center` |

---

## Nghiệm thu (checklist)

- [ ] Firestore Database ở **Native mode**, region **asia-southeast1**.
- [ ] Authentication → **Email/Password** đã bật.
- [ ] `firebase deploy --only firestore:rules,firestore:indexes,storage` chạy thành công.
- [ ] Firestore → Indexes: tất cả chuyển từ **Building** sang **Enabled**.
- [ ] `npm run seed` báo **56 documents** + **7 auth account(s)**.
- [ ] Collection `questions` có 8 document, mỗi document có subcollection `answers`.
- [ ] Auth có 7 user `*@engqa.demo`.
- [ ] App: đăng nhập `lan.teacher@engqa.demo` → Home hiện danh sách câu hỏi (realtime).
- [ ] Đăng 1 câu hỏi mới từ app → tài khoản khác (hoặc Console) thấy ngay trên Home, không cần
      kéo refresh.
- [ ] Bấm upvote 1 câu trả lời → `upCount` tăng 1 và có document
      `questions/{qid}/answers/{aid}/votes/{uid}` với `value = 1`.

---

## Xử lý sự cố

| Triệu chứng | Nguyên nhân & cách xử lý |
|---|---|
| `The query requires an index` | Index chưa build xong — đợi trạng thái **Enabled**, hoặc làm theo link Console in ra. |
| `PERMISSION_DENIED` khi đọc/ghi | Rules chưa deploy (bước 5) hoặc user chưa đăng nhập. |
| App báo `Email hoặc mật khẩu không đúng` với tài khoản demo | Chưa chạy seed, hoặc seed dùng **project khác** (kiểm tra `GOOGLE_APPLICATION_CREDENTIALS` có đúng project `accountdata-dd29b`). |
| `auth/operation-not-allowed` | Chưa bật **Email/Password** (bước 2). |
| Seed lỗi `Could not load the default credentials` | Chưa set `$env:GOOGLE_APPLICATION_CREDENTIALS` hoặc đường dẫn sai. |
| Seed lỗi `Unknown categorySlug` | Sửa `tools/seed/data/questions.json` cho khớp `categories.json`. |
| Home trống dù đã seed | Dữ liệu seed có `createdAt`; nếu đồng hồ máy lệch nhiều, `TimeUtils` hiển thị lạ nhưng vẫn có dữ liệu. Kiểm tra đã chọn đúng category chip "Tất cả". |
| Không upload được ảnh/audio | Storage rules chưa deploy, hoặc file vượt 5MB (ảnh) / 10MB (audio). |
| `npm i -g firebase-tools` lỗi `ECONNRESET` hoặc `EPERM cleanup` | Mạng chập chờn khi tải cây phụ thuộc lớn + bản cài dở còn bị khoá. Xem mục *Cài firebase-tools khi mạng yếu* bên dưới. |
| Đăng nhập/đăng ký trong app lỗi; seed báo `fetch failed` | Host `identitytoolkit.googleapis.com` bị mạng chặn (dải `172.217.11x.4`). Dùng `node seed-rest.js --no-auth`, tạo user trong Console, và thử mạng khác/VPN cho app. |
| `firebase deploy --only storage` báo *Storage has not been set up* | Bật Storage trong Console (Get Started) rồi deploy lại. |

### Cài firebase-tools khi mạng yếu

`firebase-tools` kéo theo ~670 gói (re2, pglite, google-cloud/*…). Nếu mạng reset giữa chừng,
npm để lại thư mục cài dở gây `EPERM` ở lần sau. Cách xử lý:

```powershell
# 1. Xoá bản cài dở (đóng các terminal node khác trước nếu bị khoá)
Remove-Item -Recurse -Force "$env:APPDATA\npm\node_modules\firebase-tools" -ErrorAction SilentlyContinue

# 2. Cho npm kiên nhẫn hơn và ít kết nối song song hơn
npm config set fetch-retries 5
npm config set fetch-retry-mintimeout 20000
npm config set fetch-retry-maxtimeout 600000
npm config set fetch-timeout 1800000

# 3. Cài lại, ưu tiên dùng cache đã tải dở
npm i -g firebase-tools --prefer-offline --maxsockets 2 --no-fund --no-audit

# 4. Kiểm tra
firebase --version
```

> Nếu tải mãi vẫn lỗi, có thể bỏ qua CLI: tạo rules/indexes trực tiếp trong Console
> (Firestore → Rules: dán nội dung `firestore.rules`; Indexes: thêm thủ công theo
> `firestore.indexes.json`). Bước seed chỉ cần `firebase-admin` (nhẹ hơn nhiều) nên vẫn chạy được.

---

## Bảo mật (quan trọng)

- **Service account key**: xoá khỏi máy sau khi seed và **rotate** trong Console.
- **API key trong `google-services.json`** (đang được commit): API key phía client không phải bí
  mật tuyệt đối, nhưng nên vào Google Cloud Console → **APIs & Services → Credentials** → giới hạn
  key (Application restrictions = Android apps + SHA-1; API restrictions = chỉ các API Firebase).
- **Rules** là tuyến phòng thủ thật sự; vì vậy bước 5 không được bỏ qua.
- Link tham khảo: `docs/EngQA_Giai_doan_1_Ke_hoach.md` (mục 6 — vấn đề kỹ thuật cần xử lý).
