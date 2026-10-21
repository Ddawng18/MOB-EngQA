package com.example.engqa.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.engqa.data.Callback;
import com.example.engqa.data.MediaStorageRepository;
import com.example.engqa.data.MediaUploadSession;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.UploadedMedia;
import com.example.engqa.model.Categories;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.util.TextUtils2;
import com.example.engqa.util.Validators;

import java.util.ArrayList;
import java.util.List;

public class CreateQuestionMediaViewModel extends AndroidViewModel {

    private final QuestionRepository questionRepository = new QuestionRepository();
    private final MediaStorageRepository mediaStorageRepository = new MediaStorageRepository();

    private final MutableLiveData<UiState<String>> submitState = new MutableLiveData<>();
    private final MutableLiveData<Integer> uploadProgress = new MutableLiveData<>(0);

    private String title = "";
    private String body = "";
    private String passageText = "";
    private String categoryId = Categories.SLUGS[0];
    private String categoryName = Categories.NAMES[0];
    private String level = "Beginner";
    private final List<Uri> imageUris = new ArrayList<>();
    private Uri audioUri;
    private boolean isSubmitting = false;

    public CreateQuestionMediaViewModel(@NonNull Application application) {
        super(application);
    }

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
        if (uri != null && !imageUris.contains(uri)) {
            imageUris.add(uri);
        }
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

    public void submit(String uid, String authorName) {
        if (isSubmitting) {
            return;
        }
        if (!Validators.isValidTitle(title)) {
            submitState.setValue(UiState.error("Tiêu đề cần ít nhất 10 ký tự"));
            return;
        }
        if (body == null || body.trim().isEmpty()) {
            submitState.setValue(UiState.error("Vui lòng nhập mô tả chi tiết"));
            return;
        }
        isSubmitting = true;
        submitState.setValue(UiState.loading());
        uploadProgress.setValue(0);

        final String questionId = questionRepository.newQuestionId();
        final MediaUploadSession session = new MediaUploadSession();
        final int totalAttachments = imageUris.size() + (audioUri != null ? 1 : 0);

        if (totalAttachments == 0) {
            createQuestionInFirestore(uid, authorName, questionId, session);
            return;
        }

        uploadImagesSequentially(uid, authorName, questionId, session, 0, totalAttachments);
    }

    private void uploadImagesSequentially(String uid, String authorName, String questionId,
                                          MediaUploadSession session, int index, int totalAttachments) {
        if (index >= imageUris.size()) {
            uploadAudioThenCreate(uid, authorName, questionId, session, totalAttachments);
            return;
        }
        Uri uri = imageUris.get(index);
        mediaStorageRepository.uploadImage(getApplication(), uri, uid, questionId, index, percent -> {
            if (totalAttachments > 0) {
                int overall = (int) ((index + percent / 100f) * 100 / totalAttachments);
                uploadProgress.postValue(overall);
            }
        }, new Callback<UploadedMedia>() {
            @Override
            public void onSuccess(UploadedMedia media) {
                session.add(media);
                uploadImagesSequentially(uid, authorName, questionId, session, index + 1, totalAttachments);
            }

            @Override
            public void onError(Exception e) {
                session.rollback();
                isSubmitting = false;
                submitState.postValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    private void uploadAudioThenCreate(String uid, String authorName, String questionId,
                                       MediaUploadSession session, int totalAttachments) {
        if (audioUri == null) {
            createQuestionInFirestore(uid, authorName, questionId, session);
            return;
        }
        mediaStorageRepository.uploadAudio(getApplication(), audioUri, uid, questionId, percent -> {
            if (totalAttachments > 0) {
                int imageCount = imageUris.size();
                int overall = (int) ((imageCount + percent / 100f) * 100 / totalAttachments);
                uploadProgress.postValue(overall);
            }
        }, new Callback<UploadedMedia>() {
            @Override
            public void onSuccess(UploadedMedia media) {
                session.add(media);
                createQuestionInFirestore(uid, authorName, questionId, session);
            }

            @Override
            public void onError(Exception e) {
                session.rollback();
                isSubmitting = false;
                submitState.postValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    private void createQuestionInFirestore(String uid, String authorName, String questionId,
                                           MediaUploadSession session) {
        Question question = new Question();
        question.setAuthorId(uid);
        question.setAuthorName(authorName);
        question.setTitle(title.trim());
        question.setBody(body.trim());
        question.setCategoryId(categoryId);
        question.setCategoryName(categoryName);
        question.setLevel(level);
        question.setType(categoryId);
        question.setPassageText(passageText != null ? passageText.trim() : "");
        question.setImageUrls(session.getImageUrls());
        question.setAudioUrl(session.getAudioUrl());

        List<String> tags = new ArrayList<>();
        tags.add(categoryName);
        question.setTags(tags);
        question.setSearchKeywords(TextUtils2.buildSearchKeywords(title, body, tags));

        questionRepository.createQuestion(questionId, question, new Callback<String>() {
            @Override
            public void onSuccess(String id) {
                isSubmitting = false;
                submitState.postValue(UiState.success(id));
            }

            @Override
            public void onError(Exception e) {
                session.rollback();
                isSubmitting = false;
                submitState.postValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }
}
