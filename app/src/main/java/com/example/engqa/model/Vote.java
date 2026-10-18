package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** Firestore POJO for {@code questions/{qid}/answers/{aid}/votes/{uid}}. Document id is the uid. */
@IgnoreExtraProperties
public class Vote {

    public static final int UP = 1;
    public static final int DOWN = -1;

    @DocumentId
    private String uid;
    private int value;

    public Vote() {
    }

    public Vote(int value) {
        this.value = value;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
