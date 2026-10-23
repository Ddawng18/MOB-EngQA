package com.example.engqa.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.engqa.NetworkUtils;
import com.example.engqa.model.WordEntry;
import com.example.engqa.util.PrefsHelper;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches the daily word from the free dictionary API with OkHttp and parses the JSON with Gson
 * (course 7.2). The result is cached in {@link PrefsHelper} for the current day, so the card still
 * works offline after the first successful fetch.
 *
 * <p>API: {@code https://api.dictionaryapi.dev/api/v2/entries/en/{word}} — no key required.
 */
public class WordRepository {

    private static final String ENDPOINT = "https://api.dictionaryapi.dev/api/v2/entries/en/";

    /** A deterministic daily rotation of useful IELTS words. */
    private static final String[] WORDS = {
            "serendipity", "resilient", "meticulous", "ambiguous", "coherent",
            "inevitable", "ubiquitous", "pragmatic", "empathy", "enhance",
            "significant", "elaborate", "profound", "diverse", "innovate",
            "compelling", "intricate", "feasible", "allocate", "concise"
    };

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public String todayWord() {
        Calendar calendar = Calendar.getInstance();
        int index = calendar.get(Calendar.DAY_OF_YEAR) % WORDS.length;
        return WORDS[index];
    }

    public void loadToday(Context context, boolean force, Callback<WordEntry> callback) {
        load(context, todayWord(), force, callback);
    }

    public void load(Context context, String word, boolean force, Callback<WordEntry> callback) {
        final Context appContext = context.getApplicationContext();
        final String target = (word == null || word.trim().isEmpty()) ? todayWord() : word.trim();
        final String today = todayKey();

        if (!force) {
            WordEntry cached = readCache(appContext);
            if (isUsable(cached, today, target)) {
                callback.onSuccess(cached);
                return;
            }
        }

        executor.execute(() -> {
            try {
                if (!NetworkUtils.isOnline(appContext)) {
                    throw new IOException("Không có kết nối mạng");
                }
                WordEntry entry = fetch(target);
                entry.setDate(today);
                writeCache(appContext, entry);
                mainHandler.post(() -> callback.onSuccess(entry));
            } catch (Exception e) {
                WordEntry cached = readCache(appContext);
                if (cached != null) {
                    mainHandler.post(() -> callback.onSuccess(cached));
                } else {
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }

    private WordEntry fetch(String word) throws IOException {
        String encoded = URLEncoder.encode(word, StandardCharsets.UTF_8.name());
        Request request = new Request.Builder()
                .url(ENDPOINT + encoded)
                .header("User-Agent", "EngQA/1.0 (Android)")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Từ điển trả về HTTP " + response.code());
            }
            String json = response.body().string();
            WordApiModels.Entry[] entries =
                    gson.fromJson(json, WordApiModels.Entry[].class);
            if (entries == null || entries.length == 0) {
                throw new IOException("Không tìm thấy từ \"" + word + "\"");
            }
            return map(entries[0]);
        }
    }

    private static WordEntry map(WordApiModels.Entry api) {
        WordEntry entry = new WordEntry();
        entry.setWord(api.word);

        String phonetic = api.phonetic;
        String audio = null;
        if (api.phonetics != null) {
            for (WordApiModels.Phonetic item : api.phonetics) {
                if ((phonetic == null || phonetic.isEmpty()) && item.text != null) {
                    phonetic = item.text;
                }
                if ((audio == null || audio.isEmpty())
                        && item.audio != null && !item.audio.isEmpty()) {
                    audio = item.audio;
                }
            }
        }
        entry.setPhonetic(phonetic);
        entry.setAudioUrl(audio);

        List<WordEntry.Meaning> meanings = new ArrayList<>();
        if (api.meanings != null) {
            for (WordApiModels.Meaning apiMeaning : api.meanings) {
                WordEntry.Meaning meaning = new WordEntry.Meaning();
                meaning.setPartOfSpeech(apiMeaning.partOfSpeech);
                List<WordEntry.Definition> definitions = new ArrayList<>();
                if (apiMeaning.definitions != null) {
                    for (WordApiModels.Definition apiDefinition : apiMeaning.definitions) {
                        WordEntry.Definition definition = new WordEntry.Definition();
                        definition.setText(apiDefinition.definition);
                        definition.setExample(apiDefinition.example);
                        definitions.add(definition);
                    }
                }
                meaning.setDefinitions(definitions);
                meanings.add(meaning);
            }
        }
        entry.setMeanings(meanings);
        return entry;
    }

    private static boolean isUsable(WordEntry cached, String today, String target) {
        return cached != null
                && today.equals(cached.getDate())
                && cached.getWord() != null
                && cached.getWord().equalsIgnoreCase(target);
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private WordEntry readCache(Context context) {
        String json = PrefsHelper.getWordCache(context);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(json, WordEntry.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(Context context, WordEntry entry) {
        PrefsHelper.setWordCache(context, gson.toJson(entry));
    }
}
