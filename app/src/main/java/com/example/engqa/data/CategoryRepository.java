package com.example.engqa.data;

import com.example.engqa.model.Category;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Reads the seeded {@code categories/{slug}} documents. */
public class CategoryRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration observeCategories(Callback<List<Category>> callback) {
        return db.collection(FirestoreRefs.CATEGORIES)
                .orderBy("order")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Category> categories = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Category category = document.toObject(Category.class);
                            if (category != null) {
                                if (category.getId() == null) {
                                    category.setId(document.getId());
                                }
                                categories.add(category);
                            }
                        }
                    }
                    callback.onSuccess(categories);
                });
    }
}
