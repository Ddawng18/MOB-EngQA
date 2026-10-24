# EngQA seed

Seeds a Firebase project with realistic demo data so the app can be shown without hand-typing
content. Everything it writes is marked `isSample: true`, so it can be removed again cleanly.

## Datasets (`data/`)

| File              | Writes                                                        |
|-------------------|---------------------------------------------------------------|
| `categories.json` | 9 topics → `categories/{slug}`                                |
| `users.json`      | 7 demo accounts → Firebase Auth + `users/{uid}` (`role`, `centerId`) |
| `questions.json`  | 8 questions with answers and votes                            |
| `centers.json`    | 2 centres (one APPROVED/verified, one PENDING) + courses/posts |
| `promos.json`     | 4 promo posts (3 APPROVED, 1 PENDING) + 3 leads               |

Every seeded document and the counters on `users/{uid}` (`questionCount`, `answerCount`,
`acceptedCount`) are **derived from the JSON**, so the numbers always match the actual data.

`searchKeywords` are generated with the same normalisation the Android app uses
(`util/TextUtils2`: lower-case, Vietnamese diacritics stripped, full tokens and bounded prefixes
of ≥ 2 characters), so seeded questions are immediately searchable from the feed, including
partial words. Existing questions can be reindexed without touching their content:

```bash
node reindex-search.js --dry-run   # report how many documents would change
node reindex-search.js --apply     # update only the searchKeywords field
```

## Commands

Two writers produce the same data. Pick whichever credential you have.

**Cách A — REST, chỉ cần `firebase login` (không cần key):**

```bash
node seed-rest.js --dry-run   # in kế hoạch, không gọi mạng
node seed-rest.js             # ghi dữ liệu + tạo tài khoản demo
node seed-rest.js --no-auth   # chỉ ghi Firestore (khi không tới được identitytoolkit)
node seed-rest.js --clear     # xoá documents isSample + tài khoản demo
node verify.js                # kiểm tra nhanh dữ liệu đã seed (chỉ đọc)
```

Script này đọc access token mà Firebase CLI đã lưu ở
`%USERPROFILE%\.config\configstore\firebase-tools.json`, nên chạy `firebase login` trước.
`--project <id>` hoặc biến `FIREBASE_PROJECT` để ghi đè project; `--token` / `FIREBASE_ACCESS_TOKEN`
để dùng token có sẵn.

Nó dùng chung hàm `buildPlan()` với `seed.js`, nên hai writer luôn cho ra cùng dữ liệu.

**Cách B — Admin SDK, cần service-account key:**

```bash
cd tools/seed
npm install

# Trỏ tới project Firebase đúng
set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\serviceAccount.json

npm run seed        # ghi categories, auth accounts, users, questions, answers, votes, centres
npm run seed:dry    # in kế hoạch (chạy được trước cả khi npm install)
npm run seed:clear  # xoá mọi document isSample: true + tài khoản demo
```

Lưu ý: `identitytoolkit.googleapis.com` (host tạo user Auth) có thể bị một số mạng chặn; khi đó hãy
dùng `--no-auth` rồi tạo user trong Console.

`seed:dry` / `seed-rest.js --dry-run` không gọi mạng, nên xem trước được kế hoạch mà không cần
credential.

The service account must belong to the **same** Firebase project as
`app/google-services.json`, otherwise the app will not see the data.

## Demo accounts

All demo accounts use the password `Engqa@1234`.

| Email                       | Username       | Role in the sample data        |
|-----------------------------|----------------|--------------------------------|
| `minh.anh@engqa.demo`       | `minh_anh`     | Beginner asking a grammar question |
| `lan.teacher@engqa.demo`    | `lan_teacher`  | Advanced, most accepted answers |
| `khoa.nguyen@engqa.demo`    | `anh_khoa`     | Intermediate                  |
| `linh.tran@engqa.demo`      | `linhnguyen`   | Intermediate, owns a pending centre |
| `tuan.le@engqa.demo`        | `tuan_ng`      | Advanced                      |
| `hoa.pham@engqa.demo`       | `hoa_pham`     | Beginner                      |
| `center.manager@engqa.demo` | `engqa_center` | Manager of the approved centre |

## Admin accounts

Admin status is **not** seeded. Create `admins/{uid}` in the Firebase Console (Firestore → Data)
for the user you want to make an admin. Client code can never write to `admins`, so admin rights
cannot be self-granted.

## Notes

- Media (`imageUrls`, `audioUrl`) is intentionally left empty — the sample data has no Cloud Storage
  objects, and the app stores media as Storage URLs rather than Base64.
- `isSample` is not part of the app's data model. It is used only by `seed:clear` and to tell sample
  data apart in the Console.
- Votes are written as one document per voter (`.../votes/{uid}`) with `value` in `{+1, -1}`, exactly
  as the app does, so `upCount`/`downCount`/`score` stay consistent.
