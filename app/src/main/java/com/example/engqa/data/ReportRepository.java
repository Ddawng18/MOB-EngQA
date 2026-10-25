package com.example.engqa.data;

import com.example.engqa.model.Report;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** Writes {@code reports/{id}} (Group B feature). */
public class ReportRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void report(Report report, Callback<String> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetPath", report.getTargetPath());
        data.put("reporterId", report.getReporterId());
        data.put("reporterName", report.getReporterName());
        data.put("reason", report.getReason());
        data.put("status", Report.STATUS_PENDING);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection(FirestoreRefs.REPORTS).add(data)
                .addOnSuccessListener(ref -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(callback::onError);
    }
}
