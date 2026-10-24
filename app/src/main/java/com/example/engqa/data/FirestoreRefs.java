package com.example.engqa.data;

/** Central place for Firestore collection and field names. */
public final class FirestoreRefs {

    private FirestoreRefs() {
    }

    public static final String USERS = "users";
    /** Document id = exact display name; value = { uid, username, createdAt }. */
    public static final String USERNAMES = "usernames";
    public static final String CATEGORIES = "categories";
    public static final String QUESTIONS = "questions";
    public static final String ANSWERS = "answers";
    public static final String VOTES = "votes";
    public static final String MEDIA = "media";
    public static final String REPORTS = "reports";
    public static final String NOTIFICATIONS = "notifications";
    public static final String CENTERS = "centers";
    public static final String COURSES = "courses";
    public static final String POSTS = "posts";
    public static final String PROMOS = "promos";
    public static final String LEADS = "leads";
    public static final String ADMINS = "admins";

    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_SCORE = "score";
    public static final String FIELD_ANSWER_COUNT = "answerCount";
    public static final String FIELD_CATEGORY_ID = "categoryId";
    public static final String FIELD_RECIPIENT_ID = "recipientId";
    public static final String FIELD_SEARCH_KEYWORDS = "searchKeywords";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_AUTHOR_UID = "authorUid";
    public static final String FIELD_CENTER_ID = "centerId";
}
