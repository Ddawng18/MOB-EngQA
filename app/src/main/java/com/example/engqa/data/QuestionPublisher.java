package com.example.engqa.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.engqa.media.ImageUtils;
import com.example.engqa.media.MediaBudget;
import com.example.engqa.media.MediaCompressor;
import com.example.engqa.model.Question;
import com.example.engqa.util.TextUtils2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Creates a question and its media in the Firestore-only layout:
 *
 * <ol>
 *   <li>compress images (budget) and read the audio on a background thread;</li>
 *   <li>write {@code questions/{qid}} with the thumbnail/metadata;</li>
 *   <li>batch-write {@code questions/{qid}/media/*};</li>
 *   <li>roll the question back if the media batch fails.</li>
 * </ol>
 *
 * Shared by the online submit flow and the offline WorkManager worker.
 */
public class QuestionPublisher {

    /** Everything needed to publish one question. */
    public static final class Draft {
        public String authorId;
        public String authorName;
        public String title;
        public String body;
        public String passageText;
        public String categoryId;
        public String categoryName;
        public String level;
        public String type;
        public List<String> tags = new ArrayList<>();
        public List<Uri> images = new ArrayList<>();
        public Uri audio;
    }

    public interface Progress {
        void onProgress(int percent);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final QuestionRepository questionRepository = new QuestionRepository();
    private final FirestoreMediaRepository mediaRepository = new FirestoreMediaRepository();

    public void publish(Context context, Draft draft, Progress progress,
                        Callback<String> callback) {
        final Context app = context.getApplicationContext();
        executor.execute(() -> {
            try {
                report(progress, 5);
                List<byte[]> images = new ArrayList<>();
                String thumb = null;
                for (int i = 0; i < draft.images.size(); i++) {
                    Uri uri = draft.images.get(i);
                    images.add(MediaCompressor.compressImageToBudget(app, uri));
                    if (i == 0) {
                        thumb = MediaCompressor.thumbnailBase64(app, uri);
                    }
                    report(progress, 5 + (int) ((i + 1) * 35f
                            / Math.max(1, draft.images.size())));
                }

                byte[] audio = null;
                String audioMime = null;
                if (draft.audio != null) {
                    audio = MediaCompressor.readBytes(app, draft.audio,
                            MediaBudget.MAX_AUDIO_BYTES);
                    audioMime = ImageUtils.mimeType(app, draft.audio);
                }
                report(progress, 45);

                Question question = new Question();
                question.setAuthorId(draft.authorId);
                question.setAuthorName(draft.authorName);
                question.setTitle(draft.title);
                question.setBody(draft.body);
                question.setPassageText(draft.passageText);
                question.setCategoryId(draft.categoryId);
                question.setCategoryName(draft.categoryName);
                question.setTags(draft.tags);
                question.setLevel(draft.level);
                question.setType(draft.type);
                question.setImageUrls(new ArrayList<>());
                question.setAudioUrl(null);
                question.setThumbBase64(thumb);
                question.setImageCount(images.size());
                question.setHasAudio(audio != null);
                question.setSearchKeywords(TextUtils2.buildSearchKeywords(
                        draft.title, draft.body, draft.tags));

                final String questionId = questionRepository.newQuestionId();
                final List<byte[]> imageBytes = images;
                final byte[] audioBytes = audio;
                final String finalAudioMime = audioMime;

                mainHandler.post(() -> questionRepository.createQuestion(
                        questionId, question, new Callback<String>() {
                            @Override
                            public void onSuccess(String id) {
                                report(progress, 60);
                                mediaRepository.saveAll(id, imageBytes, audioBytes,
                                        finalAudioMime, new Callback<Void>() {
                                            @Override
                                            public void onSuccess(Void result) {
                                                report(progress, 100);
                                                callback.onSuccess(id);
                                            }

                                            @Override
                                            public void onError(Exception e) {
                                                rollback(id, draft.authorId, e, callback);
                                            }
                                        });
                            }

                            @Override
                            public void onError(Exception e) {
                                callback.onError(e);
                            }
                        }));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }

    private void rollback(String questionId, String authorId, Exception cause,
                          Callback<String> callback) {
        questionRepository.deleteQuestion(questionId, authorId, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                callback.onError(cause);
            }

            @Override
            public void onError(Exception ignored) {
                callback.onError(cause);
            }
        });
    }

    private void report(Progress progress, int percent) {
        if (progress != null) {
            mainHandler.post(() -> progress.onProgress(percent));
        }
    }
}
