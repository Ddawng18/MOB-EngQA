package com.example.engqa.worker;

import java.util.ArrayList;
import java.util.List;

/** Serialised draft for a question that will be uploaded when the network returns. */
public class PendingQuestionDraft {

    public String uid;
    public String authorName;
    public String title;
    public String body;
    public String passageText;
    public String categoryId;
    public String categoryName;
    public String level;
    public String type;
    public List<String> tags = new ArrayList<>();
    /** File names relative to {@code images/}. */
    public List<String> imageFiles = new ArrayList<>();
    /** Path relative to the draft folder, or {@code null}. */
    public String audioFile;
    public long createdAt;
}
