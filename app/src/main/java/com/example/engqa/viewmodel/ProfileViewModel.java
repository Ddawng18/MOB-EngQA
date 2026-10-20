package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.AnswerRepository;
import com.example.engqa.data.Callback;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.UserRepository;
import com.example.engqa.model.Answer;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.util.Validators;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** Backs the profile screen: the user document plus their questions and answers. */
public class ProfileViewModel extends ViewModel {

    private final UserRepository userRepository = new UserRepository();
    private final QuestionRepository questionRepository = new QuestionRepository();
    private final AnswerRepository answerRepository = new AnswerRepository();

    private final MutableLiveData<UiState<User>> user = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Question>>> myQuestions = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Answer>>> myAnswers = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> actionState = new MutableLiveData<>();

    private ListenerRegistration userRegistration;
    private ListenerRegistration questionsRegistration;
    private ListenerRegistration answersRegistration;

    private String uid;

    public LiveData<UiState<User>> getUser() {
        return user;
    }

    public LiveData<UiState<List<Question>>> getMyQuestions() {
        return myQuestions;
    }

    public LiveData<UiState<List<Answer>>> getMyAnswers() {
        return myAnswers;
    }

    public LiveData<UiState<String>> getActionState() {
        return actionState;
    }

    public void start(String uid) {
        if (this.uid != null && this.uid.equals(uid) && userRegistration != null) {
            return;
        }
        this.uid = uid;
        attach();
    }

    public void stop() {
        if (userRegistration != null) {
            userRegistration.remove();
            userRegistration = null;
        }
        if (questionsRegistration != null) {
            questionsRegistration.remove();
            questionsRegistration = null;
        }
        if (answersRegistration != null) {
            answersRegistration.remove();
            answersRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        stop();
        super.onCleared();
    }

    public void updateProfile(String username, String avatarUrl, String level) {
        if (uid == null) {
            return;
        }
        userRepository.updateProfile(uid, username, avatarUrl, level, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("profile_updated"));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    /** Re-authenticates with the current password and sets a new one. */
    public void changePassword(String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            actionState.setValue(UiState.error("Vui lòng nhập mật khẩu hiện tại"));
            return;
        }
        String passwordError = Validators.passwordError(newPassword);
        if (passwordError != null) {
            actionState.setValue(UiState.error(passwordError));
            return;
        }
        String confirmError = Validators.confirmPasswordError(newPassword, confirmPassword);
        if (confirmError != null) {
            actionState.setValue(UiState.error(confirmError));
            return;
        }
        userRepository.changePassword(currentPassword, newPassword, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("password_changed"));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    private void attach() {
        user.setValue(UiState.<User>loading());
        userRegistration = userRepository.observeUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result == null) {
                    user.setValue(UiState.<User>error("Không tìm thấy hồ sơ"));
                } else {
                    user.setValue(UiState.success(result));
                }
            }

            @Override
            public void onError(Exception e) {
                user.setValue(UiState.<User>error(RepoErrors.message(e)));
            }
        });
        questionsRegistration = questionRepository.observeByAuthor(uid,
                new Callback<List<Question>>() {
                    @Override
                    public void onSuccess(List<Question> result) {
                        myQuestions.setValue(result.isEmpty()
                                ? UiState.<List<Question>>empty()
                                : UiState.success(result));
                    }

                    @Override
                    public void onError(Exception e) {
                        myQuestions.setValue(UiState.<List<Question>>error(RepoErrors.message(e)));
                    }
                });
        answersRegistration = answerRepository.observeByAuthor(uid, new Callback<List<Answer>>() {
            @Override
            public void onSuccess(List<Answer> result) {
                myAnswers.setValue(result.isEmpty()
                        ? UiState.<List<Answer>>empty()
                        : UiState.success(result));
            }

            @Override
            public void onError(Exception e) {
                myAnswers.setValue(UiState.<List<Answer>>error(RepoErrors.message(e)));
            }
        });
    }
}
