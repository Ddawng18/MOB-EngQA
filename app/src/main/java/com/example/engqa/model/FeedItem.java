package com.example.engqa.model;

/**
 * One row of the home feed: a Q&amp;A question, a centre promo or the daily word card.
 *
 * <p>{@code FeedAdapter.getItemViewType()} reads {@link #getType()} to decide which ViewHolder to
 * create, which is the multi-view-type pattern from the course.
 */
public final class FeedItem {

    public static final int TYPE_QUESTION = 0;
    public static final int TYPE_PROMO = 1;
    public static final int TYPE_WORD = 2;

    private final int type;
    private final Question question;
    private final Promo promo;
    private final WordEntry word;

    private FeedItem(int type, Question question, Promo promo, WordEntry word) {
        this.type = type;
        this.question = question;
        this.promo = promo;
        this.word = word;
    }

    public static FeedItem of(Question question) {
        return new FeedItem(TYPE_QUESTION, question, null, null);
    }

    public static FeedItem of(Promo promo) {
        return new FeedItem(TYPE_PROMO, null, promo, null);
    }

    public static FeedItem of(WordEntry word) {
        return new FeedItem(TYPE_WORD, null, null, word);
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

    public WordEntry getWord() {
        return word;
    }

    public boolean isPromo() {
        return type == TYPE_PROMO;
    }

    public boolean isWord() {
        return type == TYPE_WORD;
    }

    /** Stable key for diffing/recycling. */
    public String key() {
        if (isWord()) {
            return "word:" + (word != null && word.getDate() != null ? word.getDate() : "");
        }
        if (isPromo()) {
            return "promo:" + (promo != null ? promo.getPromoId() : "");
        }
        return "question:" + (question != null ? question.getId() : "");
    }
}
