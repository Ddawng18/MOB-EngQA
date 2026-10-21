# Phân chia công việc cho 4 người

Tài liệu này chia toàn bộ repo `MOB-EngQA` thành **4 gói việc độc lập**, mỗi file chỉ thuộc **một
người**. Mục tiêu: 4 người commit song song mà **không conflict**, mỗi PR có phạm vi rõ ràng.

---

## 0. Nguyên tắc bắt buộc

1. **Một file = một người sở hữu.** Không ai sửa file của người khác.
2. Cần thay đổi trong file của người khác → mở issue/PR nhỏ, **chủ file commit**, người yêu cầu review.
3. **File dùng chung** (mục 5) do **Người 4** sở hữu. Người 1–3 gửi yêu cầu kèm nội dung chính xác.
4. Mỗi người làm trên **nhánh riêng**, PR vào `Test` (hoặc `dev`), **1 reviewer** là chủ file bị ảnh hưởng.
5. Không commit: `keystore.properties`, `*.jks`, `*.keystore`, service-account JSON, `local.properties`,
   `build/`, `.gradle/`, `tools/seed/node_modules/`.
6. `app/google-services.json` là cấu hình **dùng chung** — chỉ Người 4 cập nhật (bằng
   `node tools/fix-google-signin.js`).

---

## 1. Tổng quan

| Người | Vai trò | Module | Nhánh | Phụ thuộc chính |
|---|---|---|---|---|
| **P1** | Auth & Profile | Đăng ký/đăng nhập (email, Google, phone), quên/đổi mật khẩu, xác minh email, hồ sơ | `feat/auth-*` | P4 (shared UI), P4 (NotificationWatcher) |
| **P2** | Q&A Core | Đặt câu hỏi (ảnh/audio), chi tiết câu hỏi, trả lời, vote, accept, media | `feat/qa-*` | P4 (shared UI, StorageRepository), P1 (UserRepository) |
| **P3** | Feed · Promos · Centres | Feed trộn, quảng bá trung tâm, lead, trung tâm, lưu bài (Room) | `feat/promo-*` | P2 (item_question, CategoryChipAdapter, QuestionDetail), P1 (SessionManager/UserRepository) |
| **P4** | Platform · Infra · QA | Firebase rules/indexes/seed, notifications, reports, build/release, shared UI kit, docs | `chore/platform-*` | — (nền tảng cho tất cả) |

**Thứ tự merge đề xuất:** P4 (nền) → P1 (auth) → P2 (Q&A) → P3 (feed/promos).
Với cây code hiện tại đã tích hợp sẵn, 4 người có thể commit theo thứ tự bất kỳ **miễn là chỉ stage
đúng file của mình**.

---

## 2. Người 1 — Auth & Profile

### Nhiệm vụ
- Đăng ký/đăng nhập email, Google, số điện thoại; validate, hiện/ẩn mật khẩu, trạng thái loading.
- Quên mật khẩu, gửi lại xác minh email, đổi mật khẩu, đăng xuất.
- Hồ sơ: thống kê, sửa tên/trình độ, danh sách câu hỏi/câu trả lời của tôi.
- Ánh xạ lỗi Firebase → tiếng Việt; `SingleLiveEvent` cho event một lần.

### File/folder cần commit
```
app/src/main/java/com/example/engqa/LoginActivity.java
app/src/main/java/com/example/engqa/RegisterActivity.java
app/src/main/java/com/example/engqa/ChooseLevelActivity.java
app/src/main/java/com/example/engqa/EditProfileActivity.java
app/src/main/java/com/example/engqa/MenuScreenActivity.java
app/src/main/java/com/example/engqa/GoogleAuthHelper.java
app/src/main/java/com/example/engqa/PhoneAuthHelper.java
app/src/main/java/com/example/engqa/SessionManager.java
app/src/main/java/com/example/engqa/data/UserRepository.java
app/src/main/java/com/example/engqa/viewmodel/AuthViewModel.java
app/src/main/java/com/example/engqa/viewmodel/ProfileViewModel.java
app/src/main/java/com/example/engqa/model/User.java
app/src/main/java/com/example/engqa/util/Validators.java
app/src/main/java/com/example/engqa/util/SingleLiveEvent.java
app/src/main/res/layout/activity_login.xml
app/src/main/res/layout/activity_register.xml
app/src/main/res/layout/activity_choose_level.xml
app/src/main/res/layout/activity_edit_profile.xml
app/src/main/res/layout/activity_menu_screen.xml
app/src/main/res/layout/dialog_phone_auth.xml
app/src/main/res/drawable/bg_auth_input.xml
app/src/main/res/drawable/bg_level_card.xml
app/src/main/res/drawable/bg_button_orange.xml
app/src/main/res/drawable/ic_visibility.xml
app/src/main/res/drawable/ic_visibility_off.xml
app/src/main/res/drawable/ic_arrow_right.xml
app/src/test/java/com/example/engqa/util/ValidatorsTest.java
docs/AUTH.md
```

### Đọc (không sửa)
`BaseNavActivity`, `view_bottom_nav.xml`, `strings.xml`, `colors.xml`, `styles.xml` (P4);
`NotificationWatcher` (P4) — `SessionManager` gọi `start/stop`.

### Definition of Done
- [ ] Đăng ký mới → `users/{uid}` được tạo; trùng tên hiển thị bị chặn.
- [ ] Đăng nhập sai mật khẩu hiện đúng thông báo; Google/Phone login chạy được (SHA-1 đã đăng ký).
- [ ] Quên mật khẩu, xác minh email, đổi mật khẩu hoạt động.
- [ ] Xoay màn hình không mất dữ liệu đang nhập, không lặp toast.
- [ ] `ValidatorsTest` xanh.

### Stage & commit
```powershell
git add app/src/main/java/com/example/engqa/LoginActivity.java `
  app/src/main/java/com/example/engqa/RegisterActivity.java `
  app/src/main/java/com/example/engqa/ChooseLevelActivity.java `
  app/src/main/java/com/example/engqa/EditProfileActivity.java `
  app/src/main/java/com/example/engqa/MenuScreenActivity.java `
  app/src/main/java/com/example/engqa/GoogleAuthHelper.java `
  app/src/main/java/com/example/engqa/PhoneAuthHelper.java `
  app/src/main/java/com/example/engqa/SessionManager.java `
  app/src/main/java/com/example/engqa/data/UserRepository.java `
  app/src/main/java/com/example/engqa/viewmodel/AuthViewModel.java `
  app/src/main/java/com/example/engqa/viewmodel/ProfileViewModel.java `
  app/src/main/java/com/example/engqa/model/User.java `
  app/src/main/java/com/example/engqa/util/Validators.java `
  app/src/main/java/com/example/engqa/util/SingleLiveEvent.java `
  app/src/main/res/layout/activity_login.xml `
  app/src/main/res/layout/activity_register.xml `
  app/src/main/res/layout/activity_choose_level.xml `
  app/src/main/res/layout/activity_edit_profile.xml `
  app/src/main/res/layout/activity_menu_screen.xml `
  app/src/main/res/layout/dialog_phone_auth.xml `
  app/src/main/res/drawable/bg_auth_input.xml `
  app/src/main/res/drawable/bg_level_card.xml `
  app/src/main/res/drawable/bg_button_orange.xml `
  app/src/main/res/drawable/ic_visibility.xml `
  app/src/main/res/drawable/ic_visibility_off.xml `
  app/src/main/res/drawable/ic_arrow_right.xml `
  app/src/test/java/com/example/engqa/util/ValidatorsTest.java `
  docs/AUTH.md
git commit -m "feat(auth): email/google/phone sign-in, profile and password flows"
```

---

## 3. Người 2 — Q&A Core

### Nhiệm vụ
- Đặt câu hỏi: tiêu đề/mô tả/đoạn văn, chọn loại + trình độ, đính kèm ảnh (chụp/chọn), ghi âm/chọn
  audio, upload Storage, tạo `questions/{id}`.
- Chi tiết: câu hỏi + media, danh sách trả lời realtime, upvote/downvote (transaction),
  accept, báo cáo/xoá.
- Thẻ câu hỏi dùng chung cho feed; adapter chip lọc.
- Tiện ích: tokenize tìm kiếm, thời gian tương đối, quyền camera/mic.

### File/folder cần commit
```
app/src/main/java/com/example/engqa/MainActivity.java
app/src/main/java/com/example/engqa/QuestionDetailActivity.java
app/src/main/java/com/example/engqa/ImageViewerActivity.java
app/src/main/java/com/example/engqa/ServerErrorActivity.java
app/src/main/java/com/example/engqa/NetworkUtils.java
app/src/main/java/com/example/engqa/model/Question.java
app/src/main/java/com/example/engqa/model/Answer.java
app/src/main/java/com/example/engqa/model/Vote.java
app/src/main/java/com/example/engqa/model/Category.java
app/src/main/java/com/example/engqa/model/Categories.java
app/src/main/java/com/example/engqa/model/UiState.java
app/src/main/java/com/example/engqa/data/QuestionRepository.java
app/src/main/java/com/example/engqa/data/AnswerRepository.java
app/src/main/java/com/example/engqa/data/VoteRepository.java
app/src/main/java/com/example/engqa/data/CategoryRepository.java
app/src/main/java/com/example/engqa/data/Snapshot.java
app/src/main/java/com/example/engqa/viewmodel/CreateQuestionViewModel.java
app/src/main/java/com/example/engqa/viewmodel/QuestionDetailViewModel.java
app/src/main/java/com/example/engqa/ui/QuestionAdapter.java
app/src/main/java/com/example/engqa/ui/AnswerAdapter.java
app/src/main/java/com/example/engqa/ui/CategoryChipAdapter.java
app/src/main/java/com/example/engqa/ui/AttachmentAdapter.java
app/src/main/java/com/example/engqa/ui/ImageAdapter.java
app/src/main/java/com/example/engqa/ui/AudioPlayerView.java
app/src/main/java/com/example/engqa/media/AudioPlayerManager.java
app/src/main/java/com/example/engqa/media/AudioRecorder.java
app/src/main/java/com/example/engqa/media/ImageUtils.java
app/src/main/java/com/example/engqa/util/TextUtils2.java
app/src/main/java/com/example/engqa/util/TimeUtils.java
app/src/main/java/com/example/engqa/util/PermissionHelper.java
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/activity_question_detail.xml
app/src/main/res/layout/activity_image_viewer.xml
app/src/main/res/layout/activity_server_error.xml
app/src/main/res/layout/item_question.xml
app/src/main/res/layout/item_answer.xml
app/src/main/res/layout/item_category_chip.xml
app/src/main/res/layout/item_attachment.xml
app/src/main/res/layout/view_audio_player.xml
app/src/main/res/layout-land/activity_main.xml
app/src/main/res/drawable/ic_upvote.xml
app/src/main/res/drawable/ic_downvote.xml
app/src/main/res/drawable/ic_accepted.xml
app/src/main/res/drawable/ic_play.xml
app/src/main/res/drawable/ic_pause.xml
app/src/main/res/drawable/ic_image.xml
app/src/main/res/drawable/ic_audio.xml
app/src/main/res/drawable/ic_more.xml
app/src/main/res/drawable/bg_answer_input.xml
app/src/main/res/drawable/bg_answer_card.xml
app/src/main/res/drawable/bg_ask_circle.xml
app/src/main/res/xml/file_paths.xml
app/src/test/java/com/example/engqa/util/TextUtils2Test.java
app/src/test/java/com/example/engqa/util/TimeUtilsTest.java
```

### Đọc (không sửa)
`StateView` + `view_state.xml` (P4); `StorageRepository` (P4); `Callback`, `RepoErrors`, `FirestoreRefs`
(P4); `SessionManager`, `UserRepository` (P1); `ReportDialogFragment` (P4).

### Hợp đồng với P3
`item_question.xml` được `FeedAdapter` (P3) dùng lại. **Không đổi id**:
`tvQuestionAvatar, tvQuestionUser, tvQuestionTime, tvQuestionTag, tvQuestionTitle,
tvQuestionAnswers, tvQuestionSolved, ivHasImage, ivHasAudio`.
`CategoryChipAdapter(String, listener)` + `setSelectedId` + `submitList` là API P3 gọi.
`QuestionDetailActivity.EXTRA_QUESTION_ID` là hằng số P3/P4 dùng.

### Definition of Done
- [ ] Đăng câu hỏi có ảnh + audio; dữ liệu vào `questions` + Storage URL.
- [ ] Trả lời hiện realtime; vote đổi đúng `upCount/downCount/score`; accept cập nhật `isSolved`.
- [ ] Bài quá hạn/không hợp lệ hiển thị lỗi thân thiện.
- [ ] `TextUtils2Test`, `TimeUtilsTest` xanh.

### Stage & commit
```powershell
git add app/src/main/java/com/example/engqa/MainActivity.java `
  app/src/main/java/com/example/engqa/QuestionDetailActivity.java `
  app/src/main/java/com/example/engqa/ImageViewerActivity.java `
  app/src/main/java/com/example/engqa/ServerErrorActivity.java `
  app/src/main/java/com/example/engqa/NetworkUtils.java `
  app/src/main/java/com/example/engqa/model/Question.java `
  app/src/main/java/com/example/engqa/model/Answer.java `
  app/src/main/java/com/example/engqa/model/Vote.java `
  app/src/main/java/com/example/engqa/model/Category.java `
  app/src/main/java/com/example/engqa/model/Categories.java `
  app/src/main/java/com/example/engqa/model/UiState.java `
  app/src/main/java/com/example/engqa/data/QuestionRepository.java `
  app/src/main/java/com/example/engqa/data/AnswerRepository.java `
  app/src/main/java/com/example/engqa/data/VoteRepository.java `
  app/src/main/java/com/example/engqa/data/CategoryRepository.java `
  app/src/main/java/com/example/engqa/data/Snapshot.java `
  app/src/main/java/com/example/engqa/viewmodel/CreateQuestionViewModel.java `
  app/src/main/java/com/example/engqa/viewmodel/QuestionDetailViewModel.java `
  app/src/main/java/com/example/engqa/ui/QuestionAdapter.java `
  app/src/main/java/com/example/engqa/ui/AnswerAdapter.java `
  app/src/main/java/com/example/engqa/ui/CategoryChipAdapter.java `
  app/src/main/java/com/example/engqa/ui/AttachmentAdapter.java `
  app/src/main/java/com/example/engqa/ui/ImageAdapter.java `
  app/src/main/java/com/example/engqa/ui/AudioPlayerView.java `
  app/src/main/java/com/example/engqa/media `
  app/src/main/java/com/example/engqa/util/TextUtils2.java `
  app/src/main/java/com/example/engqa/util/TimeUtils.java `
  app/src/main/java/com/example/engqa/util/PermissionHelper.java `
  app/src/main/res/layout/activity_main.xml `
  app/src/main/res/layout/activity_question_detail.xml `
  app/src/main/res/layout/activity_image_viewer.xml `
  app/src/main/res/layout/activity_server_error.xml `
  app/src/main/res/layout/item_question.xml `
  app/src/main/res/layout/item_answer.xml `
  app/src/main/res/layout/item_category_chip.xml `
  app/src/main/res/layout/item_attachment.xml `
  app/src/main/res/layout/view_audio_player.xml `
  app/src/main/res/layout-land/activity_main.xml `
  app/src/main/res/drawable/ic_upvote.xml `
  app/src/main/res/drawable/ic_downvote.xml `
  app/src/main/res/drawable/ic_accepted.xml `
  app/src/main/res/drawable/ic_play.xml `
  app/src/main/res/drawable/ic_pause.xml `
  app/src/main/res/drawable/ic_image.xml `
  app/src/main/res/drawable/ic_audio.xml `
  app/src/main/res/drawable/ic_more.xml `
  app/src/main/res/drawable/bg_answer_input.xml `
  app/src/main/res/drawable/bg_answer_card.xml `
  app/src/main/res/drawable/bg_ask_circle.xml `
  app/src/main/res/xml/file_paths.xml `
  app/src/test/java/com/example/engqa/util/TextUtils2Test.java `
  app/src/test/java/com/example/engqa/util/TimeUtilsTest.java
git commit -m "feat(qa): ask, detail, answers, votes and media attachments"
```

---

## 4. Người 3 — Feed · Promos · Centres · Saved (Room)

### Nhiệm vụ
- Feed trộn câu hỏi + quảng bá (`FeedMixer`), chip **Trung tâm**, tìm kiếm promo, menu hồ sơ.
- Trung tâm đăng bài (`PENDING`), chi tiết (view/click), đăng ký tư vấn (`leads`, transaction).
- Trang trung tâm (list/detail/edit/request), khoá học/bài viết.
- Lưu bài cục bộ bằng Room, màn **Bài đã lưu**.

### File/folder cần commit
```
app/src/main/java/com/example/engqa/HomeActivity.java
app/src/main/java/com/example/engqa/PromoDetailActivity.java
app/src/main/java/com/example/engqa/CreatePromoActivity.java
app/src/main/java/com/example/engqa/SavedPromosActivity.java
app/src/main/java/com/example/engqa/LeadDialogFragment.java
app/src/main/java/com/example/engqa/DatePickerFragment.java
app/src/main/java/com/example/engqa/CenterListActivity.java
app/src/main/java/com/example/engqa/CenterDetailActivity.java
app/src/main/java/com/example/engqa/CenterEditActivity.java
app/src/main/java/com/example/engqa/CenterRequestActivity.java
app/src/main/java/com/example/engqa/model/Promo.java
app/src/main/java/com/example/engqa/model/Lead.java
app/src/main/java/com/example/engqa/model/FeedItem.java
app/src/main/java/com/example/engqa/model/Center.java
app/src/main/java/com/example/engqa/model/Course.java
app/src/main/java/com/example/engqa/model/CenterPost.java
app/src/main/java/com/example/engqa/data/PromoRepository.java
app/src/main/java/com/example/engqa/data/CenterRepository.java
app/src/main/java/com/example/engqa/data/local/AppDatabase.java
app/src/main/java/com/example/engqa/data/local/SavedPromo.java
app/src/main/java/com/example/engqa/data/local/SavedPromoDao.java
app/src/main/java/com/example/engqa/data/local/SavedPromoRepository.java
app/src/main/java/com/example/engqa/viewmodel/FeedViewModel.java
app/src/main/java/com/example/engqa/viewmodel/PromoViewModel.java
app/src/main/java/com/example/engqa/viewmodel/CreatePromoViewModel.java
app/src/main/java/com/example/engqa/viewmodel/SavedViewModel.java
app/src/main/java/com/example/engqa/ui/FeedAdapter.java
app/src/main/java/com/example/engqa/ui/CenterAdapter.java
app/src/main/java/com/example/engqa/ui/CourseAdapter.java
app/src/main/java/com/example/engqa/ui/CenterPostAdapter.java
app/src/main/java/com/example/engqa/ui/SavedPromoAdapter.java
app/src/main/java/com/example/engqa/util/FeedMixer.java
app/src/main/java/com/example/engqa/util/PromoValidator.java
app/src/main/java/com/example/engqa/util/PrefsHelper.java
app/src/main/java/com/example/engqa/util/UiUtils.java
app/src/main/res/layout/activity_home.xml
app/src/main/res/layout-land/activity_home.xml
app/src/main/res/layout/activity_promo_detail.xml
app/src/main/res/layout/activity_create_promo.xml
app/src/main/res/layout/activity_saved_promos.xml
app/src/main/res/layout/dialog_lead.xml
app/src/main/res/layout/item_promo.xml
app/src/main/res/layout/item_center.xml
app/src/main/res/layout/item_center_post.xml
app/src/main/res/layout/item_course.xml
app/src/main/res/layout/item_saved_promo.xml
app/src/main/res/layout/activity_center_list.xml
app/src/main/res/layout/activity_center_detail.xml
app/src/main/res/layout/activity_center_edit.xml
app/src/main/res/layout/activity_center_request.xml
app/src/main/res/menu/menu_profile.xml
app/src/main/res/values/integers.xml
app/src/main/res/values-land/integers.xml
app/src/main/res/values-sw600dp/integers.xml
app/src/main/res/drawable/bg_promo_card.xml
app/src/main/res/drawable/bg_promo_badge.xml
app/src/main/res/drawable/bg_search.xml
app/src/main/res/drawable/ic_verified.xml
app/src/main/res/drawable/ic_center.xml
app/src/test/java/com/example/engqa/util/FeedMixerTest.java
app/src/test/java/com/example/engqa/util/PromoValidatorTest.java
tools/seed/data/promos.json
docs/PROMO.md
```

### Đọc (không sửa)
`item_question.xml`, `CategoryChipAdapter`, `QuestionDetailActivity` (P2);
`SessionManager`, `UserRepository` (P1); `BaseNavActivity`, `view_bottom_nav.xml`, `StateView`
(P4); `Callback`, `RepoErrors`, `FirestoreRefs` (P4).

### Hợp đồng
- Không đổi id trong `item_question.xml` (P2 sở hữu).
- `PromoRepository.observeApproved` + `FeedMixer.mix/filterByKeyword` là API nội bộ module.
- `PromoDetailActivity.EXTRA_PROMO_ID` dùng bởi `SavedPromosActivity` + `HomeActivity`.
- `menu_profile.xml` được `HomeActivity` dùng; mục `action_create_promo` chỉ hiện với role CENTER.

### Definition of Done
- [ ] Feed xen kẽ Q Q P Q Q P; chip **Trung tâm** chỉ promo; promo hết hạn tự ẩn.
- [ ] `PENDING` không hiện với người học; duyệt `APPROVED` thì tự hiện.
- [ ] Chi tiết +1 view (không tăng khi xoay), click tăng `clickCount`, lead chống trùng.
- [ ] Lưu/bỏ lưu Room hoạt động sau khi khởi động lại.
- [ ] `FeedMixerTest`, `PromoValidatorTest` xanh.

### Stage & commit
```powershell
git add app/src/main/java/com/example/engqa/HomeActivity.java `
  app/src/main/java/com/example/engqa/PromoDetailActivity.java `
  app/src/main/java/com/example/engqa/CreatePromoActivity.java `
  app/src/main/java/com/example/engqa/SavedPromosActivity.java `
  app/src/main/java/com/example/engqa/LeadDialogFragment.java `
  app/src/main/java/com/example/engqa/DatePickerFragment.java `
  app/src/main/java/com/example/engqa/CenterListActivity.java `
  app/src/main/java/com/example/engqa/CenterDetailActivity.java `
  app/src/main/java/com/example/engqa/CenterEditActivity.java `
  app/src/main/java/com/example/engqa/CenterRequestActivity.java `
  app/src/main/java/com/example/engqa/model/Promo.java `
  app/src/main/java/com/example/engqa/model/Lead.java `
  app/src/main/java/com/example/engqa/model/FeedItem.java `
  app/src/main/java/com/example/engqa/model/Center.java `
  app/src/main/java/com/example/engqa/model/Course.java `
  app/src/main/java/com/example/engqa/model/CenterPost.java `
  app/src/main/java/com/example/engqa/data/PromoRepository.java `
  app/src/main/java/com/example/engqa/data/CenterRepository.java `
  app/src/main/java/com/example/engqa/data/local `
  app/src/main/java/com/example/engqa/viewmodel/FeedViewModel.java `
  app/src/main/java/com/example/engqa/viewmodel/PromoViewModel.java `
  app/src/main/java/com/example/engqa/viewmodel/CreatePromoViewModel.java `
  app/src/main/java/com/example/engqa/viewmodel/SavedViewModel.java `
  app/src/main/java/com/example/engqa/ui/FeedAdapter.java `
  app/src/main/java/com/example/engqa/ui/CenterAdapter.java `
  app/src/main/java/com/example/engqa/ui/CourseAdapter.java `
  app/src/main/java/com/example/engqa/ui/CenterPostAdapter.java `
  app/src/main/java/com/example/engqa/ui/SavedPromoAdapter.java `
  app/src/main/java/com/example/engqa/util/FeedMixer.java `
  app/src/main/java/com/example/engqa/util/PromoValidator.java `
  app/src/main/java/com/example/engqa/util/PrefsHelper.java `
  app/src/main/java/com/example/engqa/util/UiUtils.java `
  app/src/main/res/layout/activity_home.xml `
  app/src/main/res/layout-land/activity_home.xml `
  app/src/main/res/layout/activity_promo_detail.xml `
  app/src/main/res/layout/activity_create_promo.xml `
  app/src/main/res/layout/activity_saved_promos.xml `
  app/src/main/res/layout/dialog_lead.xml `
  app/src/main/res/layout/item_promo.xml `
  app/src/main/res/layout/item_center.xml `
  app/src/main/res/layout/item_center_post.xml `
  app/src/main/res/layout/item_course.xml `
  app/src/main/res/layout/item_saved_promo.xml `
  app/src/main/res/layout/activity_center_list.xml `
  app/src/main/res/layout/activity_center_detail.xml `
  app/src/main/res/layout/activity_center_edit.xml `
  app/src/main/res/layout/activity_center_request.xml `
  app/src/main/res/menu/menu_profile.xml `
  app/src/main/res/values/integers.xml `
  app/src/main/res/values-land/integers.xml `
  app/src/main/res/values-sw600dp/integers.xml `
  app/src/main/res/drawable/bg_promo_card.xml `
  app/src/main/res/drawable/bg_promo_badge.xml `
  app/src/main/res/drawable/bg_search.xml `
  app/src/main/res/drawable/ic_verified.xml `
  app/src/main/res/drawable/ic_center.xml `
  app/src/test/java/com/example/engqa/util/FeedMixerTest.java `
  app/src/test/java/com/example/engqa/util/PromoValidatorTest.java `
  tools/seed/data/promos.json `
  docs/PROMO.md
git commit -m "feat(promo): mixed feed, centre advertising, leads, centres and saved posts"
```

---

## 5. Người 4 — Platform · Infra · Notifications · QA

### Nhiệm vụ
- **Shared UI kit**: strings/colors/dimens/styles/themes/arrays, icon nền, bottom nav, `StateView`,
  launcher icon.
- **Firebase**: `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `firebase.json`,
  `google-services.json`, seed tooling, `fix-google-signin.js`.
- **Build/release**: Gradle files, manifest, versioning, `docs/RELEASE.md`, ký release.
- **Notifications & reports**: channel, watcher, danh sách thông báo, báo cáo nội dung.
- **Shared data contracts**: `Callback`, `RepoErrors`, `FirestoreRefs`, `StorageRepository`.
- **QA**: unit tests mẫu, review PR, deploy rules/indexes, chạy seed.

### File/folder cần commit
```
app/src/main/java/com/example/engqa/EngQAApp.java
app/src/main/java/com/example/engqa/BaseNavActivity.java
app/src/main/java/com/example/engqa/ReportDialogFragment.java
app/src/main/java/com/example/engqa/model/AppNotification.java
app/src/main/java/com/example/engqa/model/Report.java
app/src/main/java/com/example/engqa/data/Callback.java
app/src/main/java/com/example/engqa/data/RepoErrors.java
app/src/main/java/com/example/engqa/data/FirestoreRefs.java
app/src/main/java/com/example/engqa/data/NotificationRepository.java
app/src/main/java/com/example/engqa/data/ReportRepository.java
app/src/main/java/com/example/engqa/data/StorageRepository.java
app/src/main/java/com/example/engqa/data/LinkStorageRepository.java
app/src/main/java/com/example/engqa/data/ProgressCallback.java
app/src/main/java/com/example/engqa/viewmodel/NotificationViewModel.java
app/src/main/java/com/example/engqa/notification/NotificationHelper.java
app/src/main/java/com/example/engqa/notification/NotificationWatcher.java
app/src/main/java/com/example/engqa/ui/NotificationAdapter.java
app/src/main/java/com/example/engqa/ui/StateView.java
app/src/main/res/layout/activity_notification.xml
app/src/main/res/layout/item_notification.xml
app/src/main/res/layout/dialog_report.xml
app/src/main/res/layout/view_bottom_nav.xml
app/src/main/res/layout/view_state.xml
app/src/main/res/drawable/ic_bell.xml
app/src/main/res/drawable/ic_report.xml
app/src/main/res/drawable/ic_close.xml
app/src/main/res/drawable/ic_arrow_back.xml
app/src/main/res/drawable/ic_add.xml
app/src/main/res/drawable/ic_dropdown_arrow.xml
app/src/main/res/drawable/ic_nav_home.xml
app/src/main/res/drawable/ic_nav_profile.xml
app/src/main/res/drawable/ic_launcher_background.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/bg_card.xml
app/src/main/res/drawable/bg_chip.xml
app/src/main/res/drawable/bg_input.xml
app/src/main/res/drawable/bg_button_primary.xml
app/src/main/res/drawable/bg_button_outline.xml
app/src/main/res/drawable/bg_bottom_bar.xml
app/src/main/res/drawable/bg_avatar.xml
app/src/main/res/drawable/bg_attachment.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/dimens.xml
app/src/main/res/values/styles.xml
app/src/main/res/values/themes.xml
app/src/main/res/values/arrays.xml
app/src/main/res/values-night/
app/src/main/res/xml/data_extraction_rules.xml
app/src/main/res/xml/backup_rules.xml
app/src/main/res/drawable-xxhdpi/
app/src/main/res/mipmap-anydpi/
app/src/main/res/mipmap-hdpi/
app/src/main/res/mipmap-mdpi/
app/src/main/res/mipmap-xhdpi/
app/src/main/res/mipmap-xxhdpi/
app/src/main/res/mipmap-xxxhdpi/
app/src/main/AndroidManifest.xml
app/src/test/java/com/example/engqa/ExampleUnitTest.java
app/src/androidTest/java/com/example/engqa/ExampleInstrumentedTest.java
app/build.gradle.kts
build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/
gradlew
gradlew.bat
app/google-services.json
firebase.json
firestore.rules
firestore.indexes.json
storage.rules
.gitignore
keystore.properties.example
tools/
docs/FIREBASE_SETUP.md
docs/RELEASE.md
docs/MEDIA_STORAGE.md
docs/TEAM_DIVISION.md
docs/CODEOWNERS.template
docs/EngQA_Giai_doan_1_Ke_hoach.md
docs/EngQA_Cau_truc_du_an.md
README.md
```

### Definition of Done
- [ ] `firebase deploy --only firestore:rules,firestore:indexes` thành công; seed chạy được.
- [ ] Thông báo: channel + watcher + danh sách; báo cáo ghi `reports`.
- [ ] `assembleDebug` + `assembleRelease` (ký release) thành công.
- [ ] Test tổng xanh; docs cập nhật.
- [ ] Review PR của P1–P3 khi họ chạm file dùng chung.

### Stage & commit
```powershell
git add app/src/main/java/com/example/engqa/EngQAApp.java `
  app/src/main/java/com/example/engqa/BaseNavActivity.java `
  app/src/main/java/com/example/engqa/ReportDialogFragment.java `
  app/src/main/java/com/example/engqa/model/AppNotification.java `
  app/src/main/java/com/example/engqa/model/Report.java `
  app/src/main/java/com/example/engqa/data/Callback.java `
  app/src/main/java/com/example/engqa/data/RepoErrors.java `
  app/src/main/java/com/example/engqa/data/FirestoreRefs.java `
  app/src/main/java/com/example/engqa/data/NotificationRepository.java `
  app/src/main/java/com/example/engqa/data/ReportRepository.java `
  app/src/main/java/com/example/engqa/data/StorageRepository.java `
  app/src/main/java/com/example/engqa/data/LinkStorageRepository.java `
  app/src/main/java/com/example/engqa/data/ProgressCallback.java `
  app/src/main/java/com/example/engqa/viewmodel/NotificationViewModel.java `
  app/src/main/java/com/example/engqa/notification `
  app/src/main/java/com/example/engqa/ui/NotificationAdapter.java `
  app/src/main/java/com/example/engqa/ui/StateView.java `
  app/src/main/res/layout/activity_notification.xml `
  app/src/main/res/layout/item_notification.xml `
  app/src/main/res/layout/dialog_report.xml `
  app/src/main/res/layout/view_bottom_nav.xml `
  app/src/main/res/layout/view_state.xml `
  app/src/main/res/drawable/ic_bell.xml `
  app/src/main/res/drawable/ic_report.xml `
  app/src/main/res/drawable/ic_close.xml `
  app/src/main/res/drawable/ic_arrow_back.xml `
  app/src/main/res/drawable/ic_add.xml `
  app/src/main/res/drawable/ic_dropdown_arrow.xml `
  app/src/main/res/drawable/ic_nav_home.xml `
  app/src/main/res/drawable/ic_nav_profile.xml `
  app/src/main/res/drawable/ic_launcher_background.xml `
  app/src/main/res/drawable/ic_launcher_foreground.xml `
  app/src/main/res/drawable/bg_card.xml `
  app/src/main/res/drawable/bg_chip.xml `
  app/src/main/res/drawable/bg_input.xml `
  app/src/main/res/drawable/bg_button_primary.xml `
  app/src/main/res/drawable/bg_button_outline.xml `
  app/src/main/res/drawable/bg_bottom_bar.xml `
  app/src/main/res/drawable/bg_avatar.xml `
  app/src/main/res/drawable/bg_attachment.xml `
  app/src/main/res/values `
  app/src/main/res/values-night `
  app/src/main/res/xml/data_extraction_rules.xml `
  app/src/main/res/xml/backup_rules.xml `
  app/src/main/res/drawable-xxhdpi `
  app/src/main/res/mipmap-anydpi `
  app/src/main/res/mipmap-hdpi `
  app/src/main/res/mipmap-mdpi `
  app/src/main/res/mipmap-xhdpi `
  app/src/main/res/mipmap-xxhdpi `
  app/src/main/res/mipmap-xxxhdpi `
  app/src/main/AndroidManifest.xml `
  app/src/test/java/com/example/engqa/ExampleUnitTest.java `
  app/src/androidTest `
  app/build.gradle.kts `
  build.gradle.kts `
  settings.gradle.kts `
  gradle.properties `
  gradle `
  gradlew `
  gradlew.bat `
  app/google-services.json `
  firebase.json `
  firestore.rules `
  firestore.indexes.json `
  storage.rules `
  .gitignore `
  keystore.properties.example `
  tools `
  docs/FIREBASE_SETUP.md `
  docs/RELEASE.md `
  docs/MEDIA_STORAGE.md `
  docs/TEAM_DIVISION.md `
  docs/CODEOWNERS.template `
  docs/EngQA_Giai_doan_1_Ke_hoach.md `
  docs/EngQA_Cau_truc_du_an.md `
  README.md
git commit -m "chore(platform): shared UI, firebase config, notifications, reports and release tooling"
```

---

## 6. File dùng chung — quy trình thay đổi

Các file sau **chỉ P4 sửa**. Người khác gửi yêu cầu kèm **key/value hoặc đoạn XML chính xác**.

| File | Ai dùng | Quy trình |
|---|---|---|
| `app/src/main/res/values/strings.xml` | P1, P2, P3 | Gửi `tên_key` + nội dung; P4 thêm |
| `values/colors.xml`, `dimens.xml`, `styles.xml`, `themes.xml`, `arrays.xml` | tất cả | Gửi tên + giá trị; P4 thêm |
| `view_bottom_nav.xml`, `BaseNavActivity.java`, `view_state.xml`, `StateView.java` | tất cả | Gửi mô tả thay đổi; P4 sửa |
| `AndroidManifest.xml` | P1, P2, P3 (Activity mới) | Gửi `android:name`, `parentActivityName`, `exported`; P4 thêm |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | tất cả (thư viện mới) | Gửi tên dependency + version; P4 thêm |
| `firestore.rules`, `firestore.indexes.json` | P1, P2, P3 | Gửi collection/field/query; P4 cập nhật + deploy |
| `app/google-services.json` | tất cả | Chỉ P4 chạy `node tools/fix-google-signin.js` |
| `README.md`, `docs/FIREBASE_SETUP.md`, `docs/RELEASE.md` | tất cả | P4 cập nhật |

> Gợi ý mở rộng (không bắt buộc): tách `strings.xml` thành `strings_auth.xml`, `strings_qa.xml`,
> `strings_promo.xml` để mỗi người tự thêm chuỗi. Khi làm, P4 thực hiện một lần rồi bàn giao file.

---

## 7. Phụ thuộc giữa các module (đọc, không sửa)

| Thành phần | Chủ | Được dùng bởi |
|---|---|---|
| `SessionManager`, `UserRepository` | P1 | P2, P3, P4 |
| `User.role/centerId` | P1 | P3 (menu + tạo promo) |
| `UiState`, `Question`, `Category`, `CategoryChipAdapter`, `item_question.xml` | P2 | P1, P3, P4 |
| `QuestionDetailActivity.EXTRA_QUESTION_ID` | P2 | P3 (feed), P4 (notification) |
| `Promo`, `PromoRepository`, `FeedMixer`, `PromoDetailActivity.EXTRA_PROMO_ID` | P3 | P3 (nội bộ), P4 (có thể mở từ notification sau này) |
| `StateView`, `BaseNavActivity`, `view_bottom_nav.xml` | P4 | P1, P2, P3 |
| `Callback`, `RepoErrors`, `FirestoreRefs`, `StorageRepository` | P4 | P1, P2, P3 |
| `NotificationWatcher.start/stop` | P4 | P1 (`SessionManager`), P4 (`EngQAApp`) |
| `ReportDialogFragment` | P4 | P2 (chi tiết câu hỏi) |

---

## 8. Quy ước Git

### Nhánh
```
main            # bản ổn định (tag release)
Test            # nhánh tích hợp của nhóm
feat/auth-<viec>        # P1
feat/qa-<viec>          # P2
feat/promo-<viec>       # P3
chore/platform-<viec>   # P4
fix/<mo-ta-ngan>        # bất kỳ ai
```

### Commit (Conventional Commits)
```
feat(auth): add phone OTP dialog
fix(qa): keep vote state after rotation
feat(promo): filter feed by centre chip
chore(platform): deploy promo indexes
test(promo): cover expired promos in FeedMixer
docs(auth): document SHA-1 troubleshooting
```

### PR
- Tiêu đề: `[P1] feat(auth): ...`
- Mô tả: mục tiêu, file đã chạm, cách test, ảnh chụp nếu là UI.
- **1 reviewer**: chủ file bị ảnh hưởng (nếu chạm contract) hoặc P4 (nếu chạm file dùng chung).
- Không merge khi: build đỏ, test đỏ, hoặc có file ngoài phạm vi.

### CODEOWNERS
Copy `docs/CODEOWNERS.template` → `.github/CODEOWNERS`, thay `@user1..@user4` bằng username GitHub
thật. GitHub sẽ tự thêm reviewer theo file.

---

## 9. Checklist tổng trước khi nộp

- [ ] 4 nhánh đã merge vào `Test`, không conflict.
- [ ] `./gradlew assembleDebug testDebugUnitTest` xanh.
- [ ] `firebase deploy --only firestore:rules,firestore:indexes` thành công.
- [ ] `node tools/seed/verify.js` ra 4 promos (3 APPROVED), role CENTER đúng.
- [ ] Bản release ký keystore cố định; SHA-1 release đã đăng ký; cài trên máy sạch chạy đủ tính năng.
- [ ] Mỗi người có ≥ 1 PR gắn tên mình (dùng cho báo cáo cá nhân).
