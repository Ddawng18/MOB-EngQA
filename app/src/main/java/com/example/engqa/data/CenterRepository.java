package com.example.engqa.data;

import com.example.engqa.model.Center;
import com.example.engqa.model.CenterPost;
import com.example.engqa.model.Course;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes {@code centers/{cid}} plus its {@code courses} and {@code posts} sub-collections. */
public class CenterRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration observeApprovedCenters(Callback<List<Center>> callback) {
        return db.collection(FirestoreRefs.CENTERS)
                .whereEqualTo(FirestoreRefs.FIELD_STATUS, Center.STATUS_APPROVED)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Center> centers = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Center center = document.toObject(Center.class);
                            if (center != null) {
                                if (center.getId() == null) {
                                    center.setId(document.getId());
                                }
                                centers.add(center);
                            }
                        }
                    }
                    callback.onSuccess(centers);
                });
    }

    public ListenerRegistration observeCenter(String centerId, Callback<Center> callback) {
        return db.collection(FirestoreRefs.CENTERS).document(centerId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(snapshot != null ? snapshot.toObject(Center.class) : null);
                });
    }

    /** A normal account creates a centre request that stays hidden until an admin approves it. */
    public void requestCenter(String ownerUid, Center center, Callback<String> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", center.getName());
        data.put("logoUrl", center.getLogoUrl());
        data.put("description", center.getDescription());
        data.put("address", center.getAddress());
        data.put("city", center.getCity());
        data.put("website", center.getWebsite());
        data.put("phone", center.getPhone());
        data.put("email", center.getEmail());
        data.put("ownerUid", ownerUid);
        data.put("verified", false);
        data.put("managerUids", center.getManagerUids());
        data.put("status", Center.STATUS_PENDING);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection(FirestoreRefs.CENTERS).add(data)
                .addOnSuccessListener(ref -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(callback::onError);
    }

    public void updateCenter(String centerId, Center center, Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", center.getName());
        data.put("description", center.getDescription());
        data.put("address", center.getAddress());
        if (center.getCity() != null) {
            data.put("city", center.getCity());
        }
        if (center.getWebsite() != null) {
            data.put("website", center.getWebsite());
        }
        data.put("phone", center.getPhone());
        data.put("email", center.getEmail());
        if (center.getLogoUrl() != null) {
            data.put("logoUrl", center.getLogoUrl());
        }
        db.collection(FirestoreRefs.CENTERS).document(centerId).update(data)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration observeCourses(String centerId, Callback<List<Course>> callback) {
        return db.collection(FirestoreRefs.CENTERS).document(centerId)
                .collection(FirestoreRefs.COURSES)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Course> courses = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Course course = document.toObject(Course.class);
                            if (course != null) {
                                if (course.getId() == null) {
                                    course.setId(document.getId());
                                }
                                courses.add(course);
                            }
                        }
                    }
                    callback.onSuccess(courses);
                });
    }

    public void addCourse(String centerId, Course course, Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", course.getTitle());
        data.put("description", course.getDescription());
        data.put("level", course.getLevel());
        data.put("schedule", course.getSchedule());
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection(FirestoreRefs.CENTERS).document(centerId)
                .collection(FirestoreRefs.COURSES).add(data)
                .addOnSuccessListener(ref -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration observePosts(String centerId, Callback<List<CenterPost>> callback) {
        return db.collection(FirestoreRefs.CENTERS).document(centerId)
                .collection(FirestoreRefs.POSTS)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<CenterPost> posts = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            CenterPost post = document.toObject(CenterPost.class);
                            if (post != null) {
                                if (post.getId() == null) {
                                    post.setId(document.getId());
                                }
                                posts.add(post);
                            }
                        }
                    }
                    callback.onSuccess(posts);
                });
    }

    public void addPost(String centerId, CenterPost post, Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", post.getTitle());
        data.put("body", post.getBody());
        data.put("imageUrl", post.getImageUrl());
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection(FirestoreRefs.CENTERS).document(centerId)
                .collection(FirestoreRefs.POSTS).add(data)
                .addOnSuccessListener(ref -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
