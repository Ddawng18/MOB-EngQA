package com.example.engqa.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Gson round-trip for the offline draft payload (WorkManager input data). */
public class PendingQuestionDraftTest {

    @Test
    public void gsonRoundTripKeepsEveryField() {
        PendingQuestionDraft draft = new PendingQuestionDraft();
        draft.uid = "uid-1";
        draft.authorName = "minh_anh";
        draft.title = "When do we use has been?";
        draft.body = "Please explain with examples.";
        draft.passageText = "Some context.";
        draft.categoryId = "grammar";
        draft.categoryName = "Ngữ pháp";
        draft.level = "Beginner";
        draft.type = "grammar";
        draft.tags = Collections.singletonList("Ngữ pháp");
        draft.imageFiles = Arrays.asList("img_0.jpg", "img_1.png");
        draft.audioFile = "audio/audio.m4a";
        draft.createdAt = 1_700_000_000_000L;

        Gson gson = new Gson();
        PendingQuestionDraft restored =
                gson.fromJson(gson.toJson(draft), PendingQuestionDraft.class);

        assertEquals(draft.uid, restored.uid);
        assertEquals(draft.title, restored.title);
        assertEquals(draft.body, restored.body);
        assertEquals(draft.categoryId, restored.categoryId);
        assertEquals(draft.level, restored.level);
        assertEquals(draft.tags, restored.tags);
        assertEquals(draft.imageFiles, restored.imageFiles);
        assertEquals(draft.audioFile, restored.audioFile);
        assertEquals(draft.createdAt, restored.createdAt);
        assertTrue(restored.imageFiles.size() == 2);
    }
}
