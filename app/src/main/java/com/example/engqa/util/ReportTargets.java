package com.example.engqa.util;

/**
 * Builds and parses the {@code targetPath} stored on {@code reports/{id}} documents.
 *
 * <p>The report schema has always used a Firestore document path as its target. Post reports use
 * {@code questions/{qid}} or {@code promos/{pid}}; answer reports use the answer's real nested path,
 * {@code questions/{qid}/answers/{aid}}, so existing report records stay valid.
 */
public final class ReportTargets {

    private static final String QUESTIONS = "questions";
    private static final String ANSWERS = "answers";
    private static final String PROMOS = "promos";

    public enum Type {
        QUESTION,
        ANSWER,
        PROMO,
        UNKNOWN
    }

    public static final class Target {

        private final Type type;
        private final String questionId;
        private final String answerId;
        private final String promoId;

        private Target(Type type, String questionId, String answerId, String promoId) {
            this.type = type;
            this.questionId = questionId;
            this.answerId = answerId;
            this.promoId = promoId;
        }

        public Type getType() {
            return type;
        }

        public String getQuestionId() {
            return questionId;
        }

        public String getAnswerId() {
            return answerId;
        }

        public String getPromoId() {
            return promoId;
        }
    }

    private ReportTargets() {
    }

    public static String questionPath(String questionId) {
        return QUESTIONS + "/" + questionId;
    }

    public static String answerPath(String questionId, String answerId) {
        return QUESTIONS + "/" + questionId + "/" + ANSWERS + "/" + answerId;
    }

    public static String promoPath(String promoId) {
        return PROMOS + "/" + promoId;
    }

    /** Parses a stored target path; malformed or unsupported paths become {@link Type#UNKNOWN}. */
    public static Target parse(String targetPath) {
        if (targetPath == null) {
            return unknown();
        }
        String[] parts = targetPath.trim().split("/", -1);
        if (parts.length == 2) {
            if (QUESTIONS.equals(parts[0]) && !parts[1].isEmpty()) {
                return new Target(Type.QUESTION, parts[1], null, null);
            }
            if (PROMOS.equals(parts[0]) && !parts[1].isEmpty()) {
                return new Target(Type.PROMO, null, null, parts[1]);
            }
        } else if (parts.length == 4
                && QUESTIONS.equals(parts[0])
                && ANSWERS.equals(parts[2])
                && !parts[1].isEmpty()
                && !parts[3].isEmpty()) {
            return new Target(Type.ANSWER, parts[1], parts[3], null);
        }
        return unknown();
    }

    private static Target unknown() {
        return new Target(Type.UNKNOWN, null, null, null);
    }
}
