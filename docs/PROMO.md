# Trung tâm quảng bá (promos)

Tính năng theo `TRUNG_TAM_QUANG_BA_v2.md`: trung tâm đăng bài giới thiệu/quảng bá, người học thấy
bài xen kẽ trong feed, xem chi tiết, gọi/mở website, đăng ký tư vấn và lưu bài cục bộ.

## 1. Nguyên tắc

1. **Không làm loãng diễn đàn**: promos nằm ở collection riêng `promos`, không trộn vào `questions`.
2. **Minh bạch**: mọi thẻ có nhãn **Quảng bá**, viền cam, ✓ nếu trung tâm đã xác minh.
3. **Có kiểm duyệt**: trung tâm chỉ gửi `PENDING`; quản trị duyệt `APPROVED` thì mới hiện.
4. **Vai trò do quản trị cấp**: `users.role = CENTER` + `users.centerId` chỉ admin sửa được.

## 2. Dữ liệu

| Đường dẫn | Ghi chú |
|---|---|
| `users/{uid}` | thêm `role` (`USER`/`CENTER`/`ADMIN`) và `centerId` |
| `centers/{id}` | thêm `ownerUid`, `city`, `website`, `verified`; status **UPPERCASE** |
| `promos/{promoId}` | bài quảng bá; `centerName`/`centerVerified` denormalize |
| `promos/{promoId}/leads/{uid}` | yêu cầu tư vấn; doc id = uid (chống trùng) |

Promo có: `centerId, centerName, centerVerified, authorUid, title, body, type, level, fee,
schedule, contactPhone, linkUrl, imageUrl, status, viewCount, clickCount, leadCount, createdAt,
endAt`.

`type`: `COURSE | EVENT | SCHOLARSHIP | ANNOUNCEMENT`.
`status`: `PENDING | APPROVED | REJECTED | HIDDEN`.

### Index (đã khai báo trong `firestore.indexes.json`)

| Collection | Trường |
|---|---|
| `promos` | `status` ASC, `createdAt` DESC (feed) |
| `promos` | `authorUid` ASC, `createdAt` DESC |
| `promos` | `status` ASC, `type` ASC, `createdAt` DESC |

## 3. Feed trộn

`util/FeedMixer` (hàm thuần, có JUnit):

- `removeExpired(promos, now)`: bỏ bài có `endAt` quá khứ; `endAt = null` được giữ.
- `mix(questions, promos, filter, cadence = 2)`: chèn 1 promo sau mỗi 2 câu hỏi.
- Filter `CENTER` → chỉ promo; filter category → chỉ câu hỏi; `ALL` → trộn.
- Không có câu hỏi → vẫn hiện promo để feed không trống.
- `filterByKeyword(promos, keyword)`: tìm theo **tên trung tâm / tiêu đề / trình độ**, bỏ dấu.

`FeedViewModel` giữ 2 nguồn độc lập (questions + promos) và phát `MediatorLiveData<UiState<List<FeedItem>>>`.
Chip đang chọn được nhớ bằng `PrefsHelper` (SharedPreferences).

## 4. Màn hình và file

| Màn hình | Layout | Lớp |
|---|---|---|
| Feed + chip **Trung tâm** | `activity_home.xml` | `HomeActivity`, `FeedAdapter` (2 view type) |
| Thẻ quảng bá | `item_promo.xml` | `FeedAdapter.PromoViewHolder` |
| Chi tiết | `activity_promo_detail.xml` | `PromoDetailActivity`, `PromoViewModel` |
| Đăng ký tư vấn | `dialog_lead.xml` | `LeadDialogFragment` |
| Đăng bài | `activity_create_promo.xml` | `CreatePromoActivity`, `CreatePromoViewModel`, `DatePickerFragment` |
| Bài đã lưu | `activity_saved_promos.xml` | `SavedPromosActivity`, `SavedViewModel`, `SavedPromoAdapter` |
| Menu hồ sơ | `res/menu/menu_profile.xml` | `HomeActivity` (PopupMenu) |

Dữ liệu: `PromoRepository` (snapshot listener, tạo `PENDING`, bump counter, transaction cho lead),
`SavedPromoRepository` (Room + `ExecutorService`).

## 5. Hành vi quan trọng

- **Lượt xem**: `PromoViewModel.viewCounted` đảm bảo mỗi lần mở chi tiết chỉ +1, **kể cả khi xoay
  màn hình**.
- **Lượt bấm**: mỗi lần bấm **Mở website** / **Gọi ngay** tăng `clickCount`.
- **Đăng ký tư vấn**: transaction kiểm tra `leads/{uid}` đã tồn tại → báo "Bạn đã đăng ký bài này
  rồi" và không tăng `leadCount`; thành công thì +1 và nhớ họ tên/SĐT vào SharedPreferences.
- **Chống văng app**: `ACTION_DIAL` / `ACTION_VIEW` luôn kiểm tra `resolveActivity()`.
- **Up navigation**: `PromoDetailActivity` và `CreatePromoActivity` khai báo
  `android:parentActivityName=".HomeActivity"`.
- **Room**: lưu/bỏ lưu bài, danh sách tự cập nhật qua `LiveData`; ghi DB trên thread nền.
- **Landscape/tablet**: `feed_columns` = 1 (portrait) / 2 (`values-land`, `values-sw600dp`) và
  `GridLayoutManager`.

## 6. Security rules

- Đọc promo: `APPROVED` cho mọi người; `PENDING/REJECTED` chỉ tác giả hoặc admin.
- Tạo promo: phải `authorUid == auth.uid`, `status == 'PENDING'`, 3 counter = 0,
  `users/{uid}.role == 'CENTER'` và `users/{uid}.centerId == promo.centerId`, tiêu đề 5–120 ký tự.
- Counter: chỉ +1, chỉ trên bài `APPROVED`.
- Lead: chỉ tạo cho chính mình (`leadUid == auth.uid`), đọc bởi chính mình / chủ bài / admin.
- `users`: role/centerId **không** sửa được bởi chính người dùng.
- `centers`: manager không tự đổi `status`/`verified`.

## 7. Seed

`tools/seed/data/promos.json` (3 APPROVED + 1 PENDING + 3 leads), `centers.json` (APPROVED +
PENDING, có `ownerUid`/`verified`), `users.json` (`demo-center` có `role = CENTER`).

```powershell
cd tools\seed
node seed-rest.js      # hoặc npm run seed với service account
node verify.js         # kiểm tra nhanh: 4 promos, 3 APPROVED, role CENTER, leads
```

## 8. Kiểm thử

- `FeedMixerTest` (6 ca): thứ tự Q Q P Q Q P; không promo; filter CENTER; hết hạn/null; rỗng;
  tìm kiếm bỏ dấu.
- `PromoValidatorTest` (5 ca): tiêu đề 4/5/120/121; body rỗng/5000/5001; SĐT 8/10/11/12; URL;
  ảnh tùy chọn.
- Tổng: **31 JUnit test**, tất cả xanh.

### Checklist thủ công

- [ ] Chip **Trung tâm** → chỉ thấy thẻ **Quảng bá**.
- [ ] Bài `PENDING` không hiện với người học; đổi `APPROVED` trên Console → tự hiện (snapshot listener).
- [ ] Mở chi tiết tăng `viewCount` 1 lần, xoay màn hình không tăng lại.
- [ ] Bấm **Mở website** / **Gọi ngay** tăng `clickCount`; máy không có app gọi/web không văng.
- [ ] Đăng ký tư vấn lần 2 báo đã đăng ký, `leadCount` không tăng.
- [ ] Bài hết `endAt` không hiện trên feed.
- [ ] Tài khoản `USER` không thấy mục **Đăng bài quảng bá**; gọi thẳng API bị rules chặn.
- [ ] **Bài đã lưu**: lưu → mở lại app vẫn còn; bỏ lưu → mất.
- [ ] Xoay ngang: feed 2 cột, giữ chip và vị trí cuộn.

## 9. Ngoài phạm vi

Màn trung tâm xem lead, màn admin duyệt bài trong app, sửa/xoá bài trong app, theo dõi trung tâm,
FCM, nhắc hạn ưu đãi — làm trên Console hoặc ở giai đoạn sau.
