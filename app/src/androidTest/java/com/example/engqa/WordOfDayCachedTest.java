package com.example.engqa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.engqa.model.WordEntry;
import com.example.engqa.util.PrefsHelper;
import com.google.gson.Gson;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/**
 * Seeds the word cache for today and verifies the detail screen renders it without any network
 * call, so the test is deterministic.
 */
@RunWith(AndroidJUnit4.class)
public class WordOfDayCachedTest {

    @Test
    public void cachedWordIsRendered() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PrefsHelper.setWordCache(context, new Gson().toJson(sample("serendipity")));

        Intent intent = new Intent(context, WordDetailActivity.class);
        intent.putExtra(WordDetailActivity.EXTRA_WORD, "serendipity");
        try (ActivityScenario<WordDetailActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withText("serendipity")).check(matches(isDisplayed()));
            onView(withText(containsString("occurrence of events")))
                    .check(matches(isDisplayed()));
        }
    }

    private static WordEntry sample(String word) {
        WordEntry entry = new WordEntry();
        entry.setWord(word);
        entry.setPhonetic("/ˌserənˈdɪpəti/");
        entry.setDate(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));

        WordEntry.Definition definition = new WordEntry.Definition();
        definition.setText("The occurrence of events by chance in a happy way.");
        definition.setExample("Finding this cafe was pure serendipity.");

        WordEntry.Meaning meaning = new WordEntry.Meaning();
        meaning.setPartOfSpeech("noun");
        meaning.setDefinitions(Collections.singletonList(definition));

        entry.setMeanings(Collections.singletonList(meaning));
        return entry;
    }
}
