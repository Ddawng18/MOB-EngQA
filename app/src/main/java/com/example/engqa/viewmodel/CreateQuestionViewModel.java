package com.example.engqa.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.engqa.NetworkUtils;
import com.example.engqa.data.Callback;
import com.example.engqa.data.QuestionPublisher;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.UserRepository;
import com.example.engqa.model.Categories;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.util.Validators;
import com.example.engqa.worker.PendingDraftStore;
import com.example.engqa.worker.PendingQuestionDraft;
import com.example.engqa.worker.WorkScheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds the "Đặt câu hỏi" draft and publishes it.
 *
 * <p>Online: compress + write Firestore media immediately (with progress). Offline: copy the
 * attachments into internal storage, queue a WorkManager job and report "saved".
 */
public class CreateQuestionViewModel extends AndroidViewModel {

    private final UserRepository userRepository = new UserRepository();
    private final QuestionPublisher publisher = new QuestionPublisher();
    private final ExecutorService pendingExecutor = Executors.newSingleThreadExecutor();

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

    public CreateQuestionViewModel(@NonNull Application application) {
        super(application);
    }

    // ------------------------------------------------------------------ state

    public LiveData<UiState<String>> getSubmitState() {
        return submitState;
    }

    public LiveData<Integer> getUploadProgress() {
        return uploadProgress;
    }

    // ------------------------------------------------------------------ draft

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

    @Override
    protected void onCleared() {
        publisher.shutdown();
        pendingExecutor.shutdown();
        super.onCleared();
    }

    // ------------------------------------------------------------------ submit

    public void submit() {
        if (!Validators.isValidTitle(title)) {
            submitState.setValue(UiState.error("Tiêu đề cần ít nhất 10 ký tự"));
            return;
        }
        if (body.trim().isEmpty()) {
            submitState.setValue(UiState.error("Vui lòng nhập mô tả chi tiết"));
            return;
        }
        final String uid = userRepository.currentUid();
        if (uid == null) {
            submitState.setValue(UiState.error("Bạn cần đăng nhập"));
            return;
        }
        submitState.setValue(UiState.loading());
        uploadProgress.setValue(0);

        userRepository.getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                String name = user != null && user.getUsername() != null
                        ? user.getUsername() : "user";
                continueSubmit(uid, name);
            }

            @Override
            public void onError(Exception e) {
                continueSubmit(uid, "user");
            }
        });
    }

    private void continueSubmit(String uid, String authorName) {
        if (!NetworkUtils.isOnline(getApplication())) {
            savePending(uid, authorName);
        } else {
            publish(uid, authorName);
        }
    }

    private void publish(String uid, String authorName) {
        QuestionPublisher.Draft draft = buildDraft(uid, authorName);
        submitState.setValue(UiState.loading());
        uploadProgress.setValue(0);
        publisher.publish(getApplication(), draft,
                percent -> uploadProgress.postValue(percent),
                new Callback<String>() {
                    @Override
                    public void onSuccess(String questionId) {
                        submitState.setValue(UiState.success(questionId));
                    }

                    @Override
                    public void onError(Exception e) {
                        submitState.setValue(UiState.error(RepoErrors.message(e)));
                    }
                });
    }

    private void savePending(String uid, String authorName) {
        PendingQuestionDraft draft = new PendingQuestionDraft();
        draft.uid = uid;
        draft.authorName = authorName;
        draft.title = title.trim();
        draft.body = body.trim();
        draft.passageText = passageText.trim();
        draft.categoryId = categoryId;
        draft.categoryName = categoryName;
        draft.level = level;
        draft.type = categoryId;
        draft.tags = new ArrayList<>(Collections.singletonList(categoryName));
        draft.createdAt = System.currentTimeMillis();

        final List<Uri> images = new ArrayList<>(imageUris);
        final Uri pendingAudio = audioUri;
        pendingExecutor.execute(() -> {
            try {
                File folder = PendingDraftStore.save(
                        getApplication(), draft, images, pendingAudio);
                WorkScheduler.enqueuePendingQuestion(
                        getApplication(), folder.getAbsolutePath());
                submitState.postValue(UiState.success("pending_saved"));
            } catch (Exception e) {
                submitState.postValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    private QuestionPublisher.Draft buildDraft(String uid, String authorName) {
        QuestionPublisher.Draft draft = new QuestionPublisher.Draft();
        draft.authorId = uid;
        draft.authorName = authorName;
        draft.title = title.trim();
        draft.body = body.trim();
        draft.passageText = passageText.trim();
        draft.categoryId = categoryId;
        draft.categoryName = categoryName;
        draft.level = level;
        draft.type = categoryId;
        draft.tags = new ArrayList<>(Collections.singletonList(categoryName));
        draft.images = new ArrayList<>(imageUris);
        draft.audio = audioUri;
        return draft;
    }
}
