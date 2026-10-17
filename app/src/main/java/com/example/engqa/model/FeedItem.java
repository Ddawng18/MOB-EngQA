package com.example.engqa.model;

/**
 * One row of the home feed: either a Q&amp;A question or a centre promo.
 *
 * <p>{@code FeedAdapter.getItemViewType()} reads {@link #getType()} to decide which ViewHolder to
 * create, which is the two-view-type pattern from the course.
 */
public final class FeedItem {

    public static final int TYPE_QUESTION = 0;
    public static final int TYPE_PROMO = 1;

    private final int type;
    private final Question question;
    private final Promo promo;

    private FeedItem(int type, Question question, Promo promo) {
        this.type = type;
        this.question = question;
        this.promo = promo;
    }

    public static FeedItem of(Question question) {
        return new FeedItem(TYPE_QUESTION, question, null);
    }

    public static FeedItem of(Promo promo) {
        return new FeedItem(TYPE_PROMO, null, promo);
    }

    public int getType() {
        return type;
    }

    public Question getQuestion() {
        return question;
    }

    public Promo getPromo() {
        return promo;
    }

    public boolean isPromo() {
        return type == TYPE_PROMO;
    }

    /** Stable key for diffing/recycling. */
    public String key() {
        if (isPromo()) {
            return "promo:" + (promo != null ? promo.getPromoId() : "");
        }
        return "question:" + (question != null ? question.getId() : "");
    }
}
