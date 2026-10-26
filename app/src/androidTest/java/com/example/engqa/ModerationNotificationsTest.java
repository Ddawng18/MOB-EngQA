package com.example.engqa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.engqa.data.NotificationRepository;
import com.example.engqa.model.AppNotification;
import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.notification.ModerationNotifications;
import com.example.engqa.notification.ModerationNotifications.ContentKind;
import com.example.engqa.util.ModerationPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

/** Verifies progressive moderation notification recipients, counts, wording and fields. */
@RunWith(AndroidJUnit4.class)
public class ModerationNotificationsTest {

    @Test
    public void firstUserWarning_showsCurrentCountAndRemainingBeforeTimeout() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report1", "author1", false, ContentKind.QUESTION,
                "Khi nào dùng have been?", "q1", null, null, null, "Nội dung spam",
                ModerationPolicy.applyWarning(0, 0, 0, false, false));

        assertEquals("author1", notification.getRecipientId());
        assertEquals("admin1", notification.getActorId());
        assertEquals(AppNotification.TYPE_MODERATION_WARNING, notification.getType());
        assertEquals("q1", notification.getQuestionId());
        assertEquals("moderation_warning_report1_author1", notification.getId());
        assertEquals("Cảnh cáo lần 1/3", notification.getTitle());
        assertTrue(notification.getBody().contains("Còn 2 cảnh cáo nữa trước timeout 24 giờ"));
        assertTrue(notification.getBody().contains("Khi nào dùng have been?"));
        assertTrue(notification.getBody().contains("Nội dung spam"));
        assertFalse(notification.getBody().contains("null"));
    }

    @Test
    public void thirdUserWarning_explainsTimeoutAndRemainingTimeouts() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report2", "author1", false, ContentKind.QUESTION,
                "Câu hỏi spam", "q1", null, null, null, "Spam",
                ModerationPolicy.applyWarning(2, 0, 0, false, false));

        assertEquals(AppNotification.TYPE_MODERATION_WARNING, notification.getType());
        assertEquals("Cảnh cáo lần 3/3 – tạm khoá", notification.getTitle());
        assertTrue(notification.getBody().contains("đã áp dụng timeout 24 giờ"));
        assertTrue(notification.getBody().contains("lần 1/3"));
        assertTrue(notification.getBody().contains("còn 2 lần trước khi xoá tài khoản"));
        assertFalse(notification.getBody().contains("Còn 0 cảnh cáo"));
    }

    @Test
    public void thirdTimeout_announcesAccountDeletionWithoutNegativeCount() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report3", "author1", false, ContentKind.ANSWER,
                "Câu trả lời xúc phạm", "q1", "a1", null, null, "Xúc phạm",
                ModerationPolicy.applyWarning(2, 0, 2, false, false));

        assertEquals("Tài khoản đã bị xoá", notification.getTitle());
        assertTrue(notification.getBody().contains("Tài khoản đã bị xoá"));
        assertFalse(notification.getBody().contains("-1"));
        assertFalse(notification.getBody().contains("null"));
    }

    @Test
    public void promoWarning_usesPromoCounterAndCentreRemaining() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report4", "centerAccount", false, ContentKind.PROMO,
                "Khóa IELTS", null, null, "p1", "center1", "Nội dung sai lệch",
                ModerationPolicy.applyWarning(0, 1, 0, false, true));

        assertEquals("centerAccount", notification.getRecipientId());
        assertEquals("Cảnh cáo quảng bá lần 2/3", notification.getTitle());
        assertEquals("p1", notification.getPromoId());
        assertEquals("center1", notification.getCenterId());
        assertTrue(notification.getBody().contains("Còn 1 cảnh cáo nữa"));
        assertTrue(notification.getBody().contains("thu hồi quyền"));
        assertTrue(notification.getBody().contains("Khóa IELTS"));
    }

    @Test
    public void thirdPromoWarning_revokesCentreAndExplainsConsequence() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report5", "centerAccount", false, ContentKind.PROMO,
                "Khóa IELTS", null, null, null, null, "Tái phạm",
                ModerationPolicy.applyWarning(0, 2, 0, false, true));

        assertEquals("Trung tâm bị thu hồi quyền", notification.getTitle());
        assertTrue(notification.getBody().contains("thu hồi quyền"));
        assertTrue(notification.getBody().contains("gỡ khỏi EngQA"));
        assertTrue(notification.getBody().contains("Cảnh cáo quảng bá lần 3/3"));
    }

    @Test
    public void promoWarningWithAccountTimeout_reportsBothPenalties() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report6", "centerAccount", false, ContentKind.PROMO,
                "Khóa IELTS", null, null, "p1", "center1", "Tái phạm",
                ModerationPolicy.applyWarning(2, 1, 0, false, true));

        assertTrue(notification.getBody().contains("Còn 1 cảnh cáo nữa"));
        assertTrue(notification.getBody().contains("Tài khoản trung tâm bị tạm khoá 24 giờ"));
        assertTrue(notification.getBody().contains("timeout 1/3"));
    }

    @Test
    public void deletedAnswer_goesToAnswerAuthorAndStillShowsWarningProgress() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report7", "answerAuthor", true, ContentKind.ANSWER,
                "Câu trả lời xúc phạm", "parentQuestion", null, null, null, "Xúc phạm",
                ModerationPolicy.applyWarning(0, 0, 0, false, false));

        assertEquals("answerAuthor", notification.getRecipientId());
        assertEquals(AppNotification.TYPE_MODERATION_DELETED, notification.getType());
        assertEquals("parentQuestion", notification.getQuestionId());
        assertNull(notification.getAnswerId());
        assertEquals("Nội dung đã bị xoá", notification.getTitle());
        assertTrue(notification.getBody().contains("Nội dung đã bị xoá."));
        assertTrue(notification.getBody().contains("Cảnh cáo lần 1/3"));
        assertTrue(notification.getBody().contains("Còn 2 cảnh cáo nữa"));
        assertFalse(notification.getBody().contains("null"));
    }

    @Test
    public void longContentAndReason_neverExceedFirestoreBodyLimit() {
        String longText = repeat("Nội dung rất dài ", 60);
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report8", "author1", false, ContentKind.QUESTION,
                longText, "q1", null, null, null, longText,
                ModerationPolicy.applyWarning(2, 0, 0, false, false));

        assertTrue(notification.getTitle().length() <= 120);
        assertTrue(notification.getBody().length() <= 300);
        assertTrue(notification.getBody().contains("Lý do:"));
        assertFalse(notification.getBody().contains("null"));
    }

    @Test
    public void centreRevocationAndAccountDeletion_areBothExplicit() {
        AppNotification notification = ModerationNotifications.reportAction(
                "admin1", "report9", "centerAccount", false, ContentKind.PROMO,
                "Khóa IELTS", null, null, null, null, "Tái phạm",
                ModerationPolicy.applyWarning(2, 2, 2, false, true));

        assertEquals("Trung tâm bị thu hồi quyền", notification.getTitle());
        assertTrue(notification.getBody().contains("gỡ khỏi EngQA"));
        assertTrue(notification.getBody().contains("Tài khoản trung tâm đã bị xoá"));
    }

    @Test
    public void centerDecision_goesToOwnerNotManager() {
        Center center = new Center();
        center.setId("center1");
        center.setName("Trung tâm ABC");
        center.setOwnerUid("owner1");
        center.setManagerUids(Collections.singletonList("manager1"));

        AppNotification notification = ModerationNotifications.centerDecision(
                "admin1", center, false, "Thiếu giấy phép");

        assertEquals("owner1", notification.getRecipientId());
        assertEquals(AppNotification.TYPE_CENTER_REJECTED, notification.getType());
        assertEquals("center1", notification.getCenterId());
        assertTrue(notification.getBody().contains("Trung tâm ABC"));
        assertTrue(notification.getBody().contains("Thiếu giấy phép"));
        assertNull(notification.getPromoId());
    }

    @Test
    public void promoDecision_goesToCenterAccountAndSerializesCenter() {
        Promo promo = new Promo();
        promo.setPromoId("promo1");
        promo.setAuthorUid("centerAccount");
        promo.setCenterId("center1");
        promo.setTitle("Khóa IELTS");

        AppNotification notification = ModerationNotifications.promoDecision(
                "admin1", promo, true, null);

        assertEquals("centerAccount", notification.getRecipientId());
        assertEquals(AppNotification.TYPE_PROMO_APPROVED, notification.getType());
        assertEquals("promo1", notification.getPromoId());
        assertEquals("center1", notification.getCenterId());

        Map<String, Object> data = NotificationRepository.data(notification);
        assertFalse(data.containsKey("questionId"));
        assertEquals("center1", data.get("centerId"));
        assertEquals(false, data.get("read"));
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
