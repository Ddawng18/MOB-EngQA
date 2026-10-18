package com.example.engqa.viewmodel;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.Callback;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.StorageRepository;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.util.TextUtils2;
import com.example.engqa.util.Validators;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds everything typed on the "Đặt câu hỏi" screen so a rotation does not lose the draft, and
 * orchestrates media upload followed by the question write.
 */
public class CreateQuestionViewModel extends ViewModel {

    private final QuestionRepository questionRepository = new QuestionRepository();
    private final StorageRepository storageRepository = new StorageRepository();

    private final MutableLiveData<UiState<String>> submitState = new MutableLiveData<>();
    private final MutableLiveData<Integer> uploadProgress = new MutableLiveData<>(0);

    private String title = "";
    private String body = "";
    private String passageText = "";
    private String categoryId = com.example.engqa.model.Categories.SLUGS[0];
    private String categoryName = com.example.engqa.model.Categories.NAMES[0];
    private String level = "Beginner";
    private final List<Uri> imageUris = new ArrayList<>();
    private Uri audioUri;

    public LiveData<UiState<String>> getSubmitState() {
        return submitState;
    }

    public LiveData<Integer> getUploadProgress() {
        return uploadProgress;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getPassageText() {
        return passageText;
    }

    public void setPassageText(String passageText) {
        this.passageText = passageText;
    }

    public void setCategory(String categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public List<Uri> getImageUris() {
        return imageUris;
    }

    public void addImage(Uri uri) {
        imageUris.add(uri);
    }

    public void removeImage(Uri uri) {
        imageUris.remove(uri);
    }

    public Uri getAudioUri() {
        return audioUri;
    }

    public void setAudioUri(Uri audioUri) {
        this.audioUri = audioUri;
    }

    public boolean hasContent() {
        return !title.isEmpty() || !body.isEmpty() || !imageUris.isEmpty() || audioUri != null;
    }

    /**
     * Validates, uploads media to {@code questions/{uid}/{qid}/...} then writes the question.
     * Progress is reported as a percentage across all attachments.
     */
    public void submit(String uid, String authorName) {
        if (!Validators.isValidTitle(title)) {
            submitState.setValue(UiState.error("Tiêu đề cần ít nhất 10 ký tự"));
            return;
        }
        if (body.trim().isEmpty()) {
            submitState.setValue(UiState.error("Vui lòng nhập mô tả chi tiết"));
            return;
        }
        submitState.setValue(UiState.loading());
        uploadProgress.setValue(0);

        final String questionId = questionRepository.newQuestionId();
        final List<String> uploadedImageUrls = new ArrayList<>();
        final int totalAttachments = imageUris.size() + (audioUri != null ? 1 : 0);

        uploadImagesSequentially(uid, questionId, uploadedImageUrls, 0, totalAttachments,
                () -> uploadAudioThenCreate(uid, authorName, questionId, uploadedImageUrls,
                        totalAttachments));
    }

    private void uploadImagesSequentially(String uid, String questionId, List<String> urls,
                                          int index, int total, Runnable onDone) {
        if (index >= imageUris.size()) {
            onDone.run();
            return;
        }
        Uri uri = imageUris.get(index);
        String fileName = "img_" + index + "_" + System.currentTimeMillis() + ".jpg";
        storageRepository.uploadImage(uri, uid, questionId, fileName, percent -> {
            if (total > 0) {
                uploadProgress.setValue((int) ((index + percent / 100f) * 100 / total));
            }
        }, new Callback<String>() {
            @Override
            public void onSuccess(String url) {
                urls.add(url);
                uploadImagesSequentially(uid, questionId, urls, index + 1, total, onDone);
            }

            @Override
            public void onError(Exception e) {
                submitState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    private void uploadAudioThenCreate(String uid, String authorName, String questionId,
                                       List<String> imageUrls, int total) {
        if (audioUri == null) {
            createQuestion(uid, authorName, questionId, imageUrls, null);
            return;
        }
        String fileName = "audio_" + System.currentTimeMillis() + ".m4a";
        storageRepository.uploadAudio(audioUri, uid, questionId, fileName, percent -> {
            if (total > 0) {
                uploadProgress.setValue((int) ((imageUrls.size() + percent / 100f) * 100 / total));
            }
        }, new Callback<String>() {
            @Override
            public void onSuccess(String url) {
                createQuestion(uid, authorName, questionId, imageUrls, url);
            }

            @Override
            public void onError(Exception e) {
                submitState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    private void createQuestion(String uid, String authorName, String questionId,
                                List<String> imageUrls, String audioUrl) {
        Question question = new Question();
        question.setAuthorId(uid);
        question.setAuthorName(authorName);
        question.setTitle(title.trim());
        question.setBody(body.trim());
        question.setCategoryId(categoryId);
        question.setCategoryName(categoryName);
        question.setLevel(level);
        question.setType(categoryId);
        question.setPassageText(passageText.trim());
        question.setImageUrls(imageUrls);
        question.setAudioUrl(audioUrl);

        List<String> tags = new ArrayList<>();
        tags.add(categoryName);
        question.setTags(tags);
        question.setSearchKeywords(TextUtils2.buildSearchKeywords(title, body, tags));

        questionRepository.createQuestion(questionId, question, new Callback<String>() {
            @Override
            public void onSuccess(String id) {
                submitState.setValue(UiState.success(id));
            }

            @Override
            public void onError(Exception e) {
                submitState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }
}
