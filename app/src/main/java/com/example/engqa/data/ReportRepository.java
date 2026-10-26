package com.example.engqa.data;

import com.example.engqa.model.Report;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** Writes {@code reports/{id}} (Group B feature). */
public class ReportRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * One pending report per (reporter, target). The document id is deterministic, so a second
     * submit is rejected by the transaction and by {@code firestore.rules}; an admin no longer
     * receives a flood of duplicate reports for the same post.
     */
    public void report(Report report, Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetPath", report.getTargetPath());
        data.put("reporterId", report.getReporterId());
        data.put("reporterName", report.getReporterName());
        data.put("reason", report.getReason());
        data.put("status", Report.STATUS_PENDING);
        data.put("createdAt", FieldValue.serverTimestamp());

        DocumentReference ref = db.collection(FirestoreRefs.REPORTS)
                .document(reportKey(report.getReporterId(), report.getTargetPath()));
        db.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(ref);
            if (existing.exists()
                    && Report.STATUS_PENDING.equals(existing.getString("status"))) {
                throw new IllegalStateException("Bạn đã báo cáo nội dung này rồi");
            }
            transaction.set(ref, data);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private static String reportKey(String uid, String targetPath) {
        return uid + "_" + Integer.toHexString((targetPath == null ? "" : targetPath).hashCode());
    }
}
